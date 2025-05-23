package com.viewserver.controller;

import com.viewserver.service.GridManager;
import com.viewserver.service.KafkaEventProcessor;
import com.viewserver.service.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health Check and Monitoring Controller
 * 
 * According to spec.md monitoring requirements:
 * - Real-time performance metrics
 * - Connection and grid statistics
 * - System health status
 * - Performance monitoring for <50ms latency targets
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    
    @Autowired
    private GridManager gridManager;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired(required = false)
    private KafkaEventProcessor kafkaEventProcessor;
    
    /**
     * Basic health check endpoint
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "view-server");
        health.put("version", "1.0.0");
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Detailed system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Grid statistics
        GridManager.GridStats gridStats = gridManager.getGridStats();
        Map<String, Object> gridData = new HashMap<>();
        gridData.put("gridCount", gridStats.getGridCount());
        gridData.put("totalCells", gridStats.getTotalCells());
        gridData.put("totalMemoryUsage", gridStats.getTotalMemoryUsage());
        gridData.put("memoryUsageMB", gridStats.getTotalMemoryUsage() / (1024 * 1024));
        stats.put("grids", gridData);
        
        // WebSocket session statistics
        WebSocketSessionManager.SessionStats sessionStats = sessionManager.getSessionStats();
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("activeSessions", sessionStats.getActiveSessions());
        sessionData.put("activeGrids", sessionStats.getActiveGrids());
        sessionData.put("totalSubscriptions", sessionStats.getTotalSubscriptions());
        stats.put("websockets", sessionData);
        
        // Kafka processing statistics
        KafkaEventProcessor.ProcessingStats kafkaStats = kafkaEventProcessor != null ? kafkaEventProcessor.getProcessingStats() : null;
        Map<String, Object> kafkaData = new HashMap<>();
        if (kafkaStats != null) {
            kafkaData.put("totalMessages", kafkaStats.getTotalMessages());
            kafkaData.put("totalCells", kafkaStats.getTotalCells());
            kafkaData.put("averageCellsPerMessage", kafkaStats.getAverageCellsPerMessage());
            kafkaData.put("timeSinceLastMessage", kafkaStats.getTimeSinceLastMessage());
        }
        stats.put("kafka", kafkaData);
        
        // System information
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> systemData = new HashMap<>();
        systemData.put("maxMemory", runtime.maxMemory());
        systemData.put("totalMemory", runtime.totalMemory());
        systemData.put("freeMemory", runtime.freeMemory());
        systemData.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        systemData.put("memoryUtilization", (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory());
        stats.put("system", systemData);
        
        stats.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Performance metrics endpoint
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        Map<String, Object> performance = new HashMap<>();
        
        // Kafka processing performance
        KafkaEventProcessor.ProcessingStats kafkaStats = kafkaEventProcessor != null ? kafkaEventProcessor.getProcessingStats() : null;
        performance.put("kafkaProcessingStats", kafkaStats != null ? kafkaStats.toString() : null);
        
        // Memory performance
        Runtime runtime = Runtime.getRuntime();
        double memoryUtilization = (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        performance.put("memoryUtilization", memoryUtilization);
        performance.put("memoryStatus", memoryUtilization > 0.8 ? "HIGH" : memoryUtilization > 0.6 ? "MEDIUM" : "LOW");
        
        // Grid performance
        GridManager.GridStats gridStats = gridManager.getGridStats();
        performance.put("gridsPerformance", Map.of(
            "gridCount", gridStats.getGridCount(),
            "memoryUsageGB", gridStats.getTotalMemoryUsage() / (1024.0 * 1024.0 * 1024.0),
            "status", gridStats.getGridCount() > 80 ? "HIGH_LOAD" : "NORMAL"
        ));
        
        // WebSocket performance
        WebSocketSessionManager.SessionStats sessionStats = sessionManager.getSessionStats();
        performance.put("websocketPerformance", Map.of(
            "activeSessions", sessionStats.getActiveSessions(),
            "status", sessionStats.getActiveSessions() > 800 ? "HIGH_LOAD" : "NORMAL"
        ));
        
        performance.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(performance);
    }
    
    /**
     * Readiness check for Kubernetes/Docker
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readinessCheck() {
        Map<String, Object> readiness = new HashMap<>();
        
        boolean isReady = true;
        StringBuilder issues = new StringBuilder();
        
        // Check if services are initialized
        try {
            GridManager.GridStats gridStats = gridManager.getGridStats();
            WebSocketSessionManager.SessionStats sessionStats = sessionManager.getSessionStats();
            KafkaEventProcessor.ProcessingStats kafkaStats = kafkaEventProcessor != null ? kafkaEventProcessor.getProcessingStats() : null;
            
            // Services are accessible
            readiness.put("gridManager", "UP");
            readiness.put("sessionManager", "UP");
            readiness.put("kafkaProcessor", kafkaStats != null ? "UP" : "DOWN");
            
        } catch (Exception e) {
            isReady = false;
            issues.append("Service initialization error: ").append(e.getMessage());
        }
        
        // Check memory constraints (spec.md: <16GB total)
        Runtime runtime = Runtime.getRuntime();
        double memoryUtilization = (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        if (memoryUtilization > 0.9) {
            isReady = false;
            issues.append("High memory utilization: ").append(String.format("%.1f%%", memoryUtilization * 100));
        }
        
        readiness.put("ready", isReady);
        readiness.put("status", isReady ? "READY" : "NOT_READY");
        if (issues.length() > 0) {
            readiness.put("issues", issues.toString());
        }
        readiness.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(isReady ? 200 : 503).body(readiness);
    }
    
    /**
     * Liveness check for Kubernetes/Docker
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> livenessCheck() {
        Map<String, Object> liveness = new HashMap<>();
        
        // Simple liveness check - if we can respond, we're alive
        liveness.put("alive", true);
        liveness.put("status", "ALIVE");
        liveness.put("uptime", System.currentTimeMillis() - getStartTime());
        liveness.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(liveness);
    }
    
    /**
     * Reset statistics (for testing)
     */
    @GetMapping("/reset-stats")
    public ResponseEntity<Map<String, Object>> resetStats() {
        if (kafkaEventProcessor != null) {
            kafkaEventProcessor.resetStats();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Statistics reset successfully");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    // Approximate start time (would be better to inject this)
    private static final long START_TIME = System.currentTimeMillis();
    
    private long getStartTime() {
        return START_TIME;
    }
} 