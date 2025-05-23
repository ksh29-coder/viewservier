package com.viewserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viewserver.dto.WebSocketMessage;
import com.viewserver.model.Grid;
import com.viewserver.service.GridManager;
import com.viewserver.service.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket Handler for Grid connections
 * 
 * According to spec.md API Specifications:
 * - Connect: /ws/grid/{userId}/{viewId}
 * - Handle initial load via chunked WebSocket transfer
 * - Process real-time delta updates
 * - Manage subscription lifecycle
 * 
 * Performance targets per spec.md:
 * - Support 1,000+ concurrent WebSocket connections
 * - <50ms latency for updates
 * - Handle small message sizes (<100KB per frame)
 */
@Component
public class GridWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GridWebSocketHandler.class);
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private GridManager gridManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Pattern to extract userId and viewId from WebSocket URI
    private static final Pattern URI_PATTERN = Pattern.compile("/ws/grid/([^/]+)/([^/]+)");
    
    // Configuration from application.yml
    @Value("${viewserver.grid.default-rows:10000}")
    private int DEFAULT_ROWS;
    
    @Value("${viewserver.grid.default-columns:100}")
    private int DEFAULT_COLUMNS;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            log.info("WebSocket connection established: {}", session.getId());
            
            // Extract userId and viewId from session URI
            String[] userAndView = extractUserAndViewId(session);
            if (userAndView == null) {
                log.error("Invalid WebSocket URI format for session {}: {}", 
                         session.getId(), session.getUri());
                session.close(CloseStatus.BAD_DATA.withReason("Invalid URI format"));
                return;
            }
            
            String userId = userAndView[0];
            String viewId = userAndView[1];
            String gridId = userId + "_" + viewId;
            
            log.info("WebSocket connection for user: {}, view: {}, gridId: {}", userId, viewId, gridId);
            
            // Get or create grid for this user/view
            Grid grid = gridManager.getOrCreateGrid(userId, viewId, DEFAULT_ROWS, DEFAULT_COLUMNS);
            if (grid == null) {
                log.error("Failed to create/get grid for {}", gridId);
                session.close(CloseStatus.SERVER_ERROR.withReason("Grid creation failed"));
                return;
            }
            
            // Register session with session manager
            sessionManager.addSession(gridId, session);
            
            // Send initial load data asynchronously to avoid blocking
            CompletableFuture.runAsync(() -> {
                try {
                    gridManager.sendInitialLoad(session, gridId);
                    log.info("Initial load completed for session {} (grid {})", session.getId(), gridId);
                } catch (Exception e) {
                    log.error("Error during initial load for session {}: {}", session.getId(), e.getMessage(), e);
                }
            });
            
            log.info("WebSocket session {} fully initialized for grid {}", session.getId(), gridId);
            
        } catch (Exception e) {
            log.error("Error establishing WebSocket connection for session {}: {}", 
                     session.getId(), e.getMessage(), e);
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("Connection setup failed"));
            } catch (Exception closeError) {
                log.debug("Error closing session after setup failure: {}", closeError.getMessage());
            }
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            log.debug("Received WebSocket message from {}: {}", session.getId(), message.getPayload());
            
            // Parse incoming message
            WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);
            
            // Handle different message types
            switch (wsMessage.getType()) {
                case "PING":
                    handlePingMessage(session, wsMessage);
                    break;
                case "SUBSCRIBE":
                    handleSubscribeMessage(session, wsMessage);
                    break;
                case "UNSUBSCRIBE":
                    handleUnsubscribeMessage(session, wsMessage);
                    break;
                case "CELL_UPDATE_REQUEST":
                    handleCellUpdateRequest(session, wsMessage);
                    break;
                default:
                    log.warn("Unknown message type from session {}: {}", session.getId(), wsMessage.getType());
                    sendErrorMessage(session, "Unknown message type: " + wsMessage.getType());
            }
            
        } catch (Exception e) {
            log.error("Error handling WebSocket message from session {}: {}", 
                     session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "Message processing error: " + e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        
        try {
            // Clean up session and subscriptions
            sessionManager.removeSession(session);
            
            log.debug("Session {} cleanup completed", session.getId());
            
        } catch (Exception e) {
            log.error("Error during session cleanup for {}: {}", session.getId(), e.getMessage(), e);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", 
                 session.getId(), exception.getMessage(), exception);
        
        try {
            // Close session on transport error
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR.withReason("Transport error"));
            }
        } catch (Exception e) {
            log.debug("Error closing session after transport error: {}", e.getMessage());
        }
    }
    
    /**
     * Extract userId and viewId from WebSocket session URI
     */
    private String[] extractUserAndViewId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        
        Matcher matcher = URI_PATTERN.matcher(uri.getPath());
        if (matcher.find()) {
            String userId = matcher.group(1);
            String viewId = matcher.group(2);
            return new String[]{userId, viewId};
        }
        
        return null;
    }
    
    /**
     * Handle ping messages for connection health check
     */
    private void handlePingMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            WebSocketMessage pongMessage = new WebSocketMessage("PONG", message.getGridId(), "pong");
            String json = objectMapper.writeValueAsString(pongMessage);
            session.sendMessage(new TextMessage(json));
            
            log.debug("Sent PONG response to session {}", session.getId());
        } catch (Exception e) {
            log.error("Error sending PONG to session {}: {}", session.getId(), e.getMessage());
        }
    }
    
    /**
     * Handle subscription messages (already handled in connection setup)
     */
    private void handleSubscribeMessage(WebSocketSession session, WebSocketMessage message) {
        // Note: Subscription is already handled in afterConnectionEstablished
        // This could be used for dynamic subscription changes in the future
        log.debug("Subscription message received from session {} for grid {}", 
                 session.getId(), message.getGridId());
        
        try {
            WebSocketMessage ackMessage = new WebSocketMessage("SUBSCRIBE_ACK", message.getGridId(), "subscribed");
            String json = objectMapper.writeValueAsString(ackMessage);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Error sending subscription ACK to session {}: {}", session.getId(), e.getMessage());
        }
    }
    
    /**
     * Handle unsubscription messages
     */
    private void handleUnsubscribeMessage(WebSocketSession session, WebSocketMessage message) {
        log.info("Unsubscription request from session {} for grid {}", 
                session.getId(), message.getGridId());
        
        try {
            // Remove session (will be cleaned up in afterConnectionClosed)
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL.withReason("Unsubscribed"));
            }
        } catch (Exception e) {
            log.error("Error processing unsubscription for session {}: {}", session.getId(), e.getMessage());
        }
    }
    
    /**
     * Handle cell update requests from client
     */
    private void handleCellUpdateRequest(WebSocketSession session, WebSocketMessage message) {
        log.debug("Cell update request from session {} for grid {}", 
                 session.getId(), message.getGridId());
        
        // For now, just acknowledge the request
        // In a real implementation, this might trigger specific cell updates
        try {
            WebSocketMessage ackMessage = new WebSocketMessage("UPDATE_ACK", message.getGridId(), "acknowledged");
            String json = objectMapper.writeValueAsString(ackMessage);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Error sending update ACK to session {}: {}", session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send error message to client
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            WebSocketMessage errorMsg = new WebSocketMessage("ERROR", null, errorMessage);
            String json = objectMapper.writeValueAsString(errorMsg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Error sending error message to session {}: {}", session.getId(), e.getMessage());
        }
    }
} 