package com.viewserver.config;

import com.viewserver.controller.GridWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket Configuration for View Server POC
 * 
 * According to spec.md API Specifications:
 * - Connect: /ws/grid/{userId}/{viewId}
 * - Subscribe: Send subscription message for specific grid
 * - Request Initial Load: Automatic on connection
 * - Unsubscribe: Remove subscription for grid
 * 
 * Performance considerations:
 * - CORS enabled for development (can be restricted for production)
 * - Support for 1,000+ concurrent WebSocket connections
 * - Efficient message routing to subscribers
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private GridWebSocketHandler gridWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gridWebSocketHandler, "/ws/grid/{userId}/{viewId}")
                .setAllowedOrigins("*") // TODO: Restrict for production
                .withSockJS(); // Enable SockJS fallback for better browser compatibility
        
        // Also register without SockJS for native WebSocket clients
        registry.addHandler(gridWebSocketHandler, "/ws/grid/{userId}/{viewId}")
                .setAllowedOrigins("*");
    }
} 