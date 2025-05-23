package com.viewserver.controller;

import com.viewserver.service.GridManager;
import com.viewserver.service.WebSocketSessionManager;
import com.viewserver.model.Grid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug Controller for testing connectivity
 */
@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = "*") // Allow all origins for testing
public class DebugController {
    
    @Autowired
    private GridManager gridManager;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    // Configuration from application.yml
    @Value("${viewserver.grid.default-rows:10000}")
    private int DEFAULT_ROWS;
    
    @Value("${viewserver.grid.default-columns:100}")
    private int DEFAULT_COLUMNS;
    
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Backend is reachable");
        response.put("timestamp", System.currentTimeMillis());
        response.put("websocketEndpoint", "ws://localhost:8080/ws/grid/{userId}/{viewId}");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cors-test")
    public ResponseEntity<Map<String, Object>> corsTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "CORS is working");
        response.put("origin", "frontend can reach backend");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/grid/{userId}/{viewId}")
    public ResponseEntity<Map<String, Object>> getGridInfo(@PathVariable String userId, @PathVariable String viewId) {
        Map<String, Object> response = new HashMap<>();
        String gridId = userId + "_" + viewId;
        
        Grid grid = gridManager.getGrid(gridId);
        if (grid == null) {
            // Try to create the grid
            grid = gridManager.getOrCreateGrid(userId, viewId, DEFAULT_ROWS, DEFAULT_COLUMNS);
        }
        
        if (grid != null) {
            response.put("gridId", gridId);
            response.put("rows", grid.getRows());
            response.put("columns", grid.getColumns());
            response.put("totalCells", grid.getCellCount());
            response.put("exists", true);
            response.put("sampleCells", grid.getAllCells().subList(0, Math.min(5, grid.getAllCells().size())));
        } else {
            response.put("gridId", gridId);
            response.put("exists", false);
            response.put("error", "Could not create grid");
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessionInfo() {
        Map<String, Object> response = new HashMap<>();
        WebSocketSessionManager.SessionStats stats = sessionManager.getSessionStats();
        
        response.put("activeSessions", stats.getActiveSessions());
        response.put("activeGrids", stats.getActiveGrids());
        response.put("totalSubscriptions", stats.getTotalSubscriptions());
        response.put("activeGridIds", sessionManager.getActiveGridIds());
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/grid/{userId}/{viewId}")
    public ResponseEntity<Map<String, Object>> recreateGrid(@PathVariable String userId, @PathVariable String viewId) {
        Map<String, Object> response = new HashMap<>();
        String gridId = userId + "_" + viewId;
        
        // Remove existing grid
        boolean removed = gridManager.removeGrid(gridId);
        
        // Create new grid with current configuration
        Grid grid = gridManager.getOrCreateGrid(userId, viewId, DEFAULT_ROWS, DEFAULT_COLUMNS);
        
        if (grid != null) {
            response.put("message", "Grid recreated successfully");
            response.put("gridId", gridId);
            response.put("previousGridRemoved", removed);
            response.put("newRows", grid.getRows());
            response.put("newColumns", grid.getColumns());
            response.put("newTotalCells", grid.getCellCount());
        } else {
            response.put("message", "Failed to recreate grid");
            response.put("gridId", gridId);
            response.put("previousGridRemoved", removed);
        }
        
        return ResponseEntity.ok(response);
    }
} 