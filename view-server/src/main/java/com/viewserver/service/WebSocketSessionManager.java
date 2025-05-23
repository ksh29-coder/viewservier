package com.viewserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket Session Manager for handling session lifecycle and broadcasting
 * 
 * According to spec.md Performance Requirements:
 * - Support 1,000+ concurrent WebSocket connections
 * - <50ms latency for message broadcasting
 * - Efficient message routing to subscribers
 * - Thread-safe session management
 */
@Service
public class WebSocketSessionManager {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Session management: sessionId -> session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Grid subscriptions: gridId -> set of sessionIds
    private final Map<String, Set<String>> gridSubscriptions = new ConcurrentHashMap<>();
    
    // Session to grid mapping: sessionId -> gridId (for cleanup)
    private final Map<String, String> sessionToGrid = new ConcurrentHashMap<>();
    
    /**
     * Add a new WebSocket session
     */
    public void addSession(String gridId, WebSocketSession session) {
        String sessionId = session.getId();
        
        sessions.put(sessionId, session);
        sessionToGrid.put(sessionId, gridId);
        
        // Subscribe session to grid updates
        gridSubscriptions.computeIfAbsent(gridId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        
        log.info("Added WebSocket session {} for grid {}", sessionId, gridId);
        log.debug("Current sessions: {}, Grid subscribers: {}", 
                 sessions.size(), gridSubscriptions.get(gridId).size());
    }
    
    /**
     * Remove a WebSocket session and clean up subscriptions
     */
    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        String gridId = sessionToGrid.remove(sessionId);
        
        sessions.remove(sessionId);
        
        if (gridId != null) {
            Set<String> subscribers = gridSubscriptions.get(gridId);
            if (subscribers != null) {
                subscribers.remove(sessionId);
                
                // Clean up empty subscription sets
                if (subscribers.isEmpty()) {
                    gridSubscriptions.remove(gridId);
                }
            }
        }
        
        log.info("Removed WebSocket session {} from grid {}", sessionId, gridId);
        log.debug("Remaining sessions: {}", sessions.size());
    }
    
    /**
     * Broadcast message to all subscribers of a specific grid
     */
    public void broadcastToGridSubscribers(String gridId, Object message) {
        Set<String> subscribers = gridSubscriptions.get(gridId);
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for grid {}", gridId);
            return;
        }
        
        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize message for grid {}: {}", gridId, e.getMessage());
            return;
        }
        
        TextMessage textMessage = new TextMessage(messageJson);
        int successCount = 0;
        int failureCount = 0;
        
        // Broadcast to all subscribers (thread-safe iteration)
        for (String sessionId : subscribers) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                    successCount++;
                } catch (IOException e) {
                    log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
                    // Mark for cleanup
                    markSessionForCleanup(sessionId);
                    failureCount++;
                }
            } else {
                // Session is closed or null, mark for cleanup
                markSessionForCleanup(sessionId);
                failureCount++;
            }
        }
        
        log.debug("Broadcast to grid {}: {} successful, {} failed", gridId, successCount, failureCount);
    }
    
    /**
     * Send message to a specific session
     */
    public boolean sendToSession(String sessionId, Object message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            log.warn("Session {} not found or closed", sessionId);
            return false;
        }
        
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(messageJson));
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to session {}: {}", sessionId, e.getMessage());
            markSessionForCleanup(sessionId);
            return false;
        }
    }
    
    /**
     * Get subscriber count for a grid
     */
    public int getSubscriberCount(String gridId) {
        Set<String> subscribers = gridSubscriptions.get(gridId);
        return subscribers != null ? subscribers.size() : 0;
    }
    
    /**
     * Get total active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * Get all active grid IDs
     */
    public Set<String> getActiveGridIds() {
        return gridSubscriptions.keySet();
    }
    
    /**
     * Check if a specific session is active
     */
    public boolean isSessionActive(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }
    
    /**
     * Mark session for cleanup (called from broadcast when send fails)
     */
    private void markSessionForCleanup(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                log.debug("Error closing session {} during cleanup: {}", sessionId, e.getMessage());
            }
            removeSession(session);
        }
    }
    
    /**
     * Get session statistics for monitoring
     */
    public SessionStats getSessionStats() {
        return new SessionStats(
            sessions.size(),
            gridSubscriptions.size(),
            gridSubscriptions.values().stream().mapToInt(Set::size).sum()
        );
    }
    
    /**
     * Session statistics for monitoring
     */
    public static class SessionStats {
        private final int activeSessions;
        private final int activeGrids;
        private final int totalSubscriptions;
        
        public SessionStats(int activeSessions, int activeGrids, int totalSubscriptions) {
            this.activeSessions = activeSessions;
            this.activeGrids = activeGrids;
            this.totalSubscriptions = totalSubscriptions;
        }
        
        public int getActiveSessions() { return activeSessions; }
        public int getActiveGrids() { return activeGrids; }
        public int getTotalSubscriptions() { return totalSubscriptions; }
        
        @Override
        public String toString() {
            return "SessionStats{" +
                    "activeSessions=" + activeSessions +
                    ", activeGrids=" + activeGrids +
                    ", totalSubscriptions=" + totalSubscriptions +
                    '}';
        }
    }
} 