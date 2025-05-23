package com.viewserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viewserver.dto.*;
import com.viewserver.model.Cell;
import com.viewserver.model.Grid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grid Manager Service for in-memory grid state management
 * 
 * According to spec.md requirements:
 * - Grid Size: 100 columns × 10,000 rows (1 million cells)
 * - Memory: ~50MB per grid, 5.1GB for 100 concurrent grids
 * - Initial Load: Chunked WebSocket transfer (1000 cells per chunk)
 * - Real-time Updates: Small delta updates (1-500 cells per message)
 * - Thread-safe operations for concurrent access
 */
@Service
public class GridManager {
    private static final Logger log = LoggerFactory.getLogger(GridManager.class);
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    @Autowired
    private MessageSizeValidator messageValidator;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // In-memory grid storage: gridId -> Grid
    private final ConcurrentHashMap<String, Grid> grids = new ConcurrentHashMap<>();
    
    // Configuration from application.yml
    @Value("${viewserver.grid.initial-load-chunk-size:1000}")
    private int INITIAL_LOAD_CHUNK_SIZE;
    
    @Value("${viewserver.grid.default-rows:10000}")
    private int DEFAULT_ROWS;
    
    @Value("${viewserver.grid.default-columns:100}")
    private int DEFAULT_COLUMNS;
    
    @Value("${viewserver.grid.max-grids:100}")
    private int MAX_GRIDS;
    
    /**
     * Create or get existing grid for user/view
     */
    public Grid getOrCreateGrid(String userId, String viewId, int rows, int columns) {
        String gridId = createGridId(userId, viewId);
        
        return grids.computeIfAbsent(gridId, k -> {
            // Check grid limit per spec.md (100 concurrent grids)
            if (grids.size() >= MAX_GRIDS) {
                log.warn("Maximum grid limit ({}) reached, cannot create new grid: {}", MAX_GRIDS, gridId);
                return null;
            }
            
            Grid grid = new Grid(gridId, userId, viewId, rows, columns);
            initializeGridData(grid);
            
            log.info("Created new grid: {} with {} rows × {} columns", gridId, rows, columns);
            log.debug("Grid memory usage: {} bytes", grid.getEstimatedMemoryUsage());
            
            return grid;
        });
    }
    
    /**
     * Get existing grid
     */
    public Grid getGrid(String gridId) {
        return grids.get(gridId);
    }
    
    /**
     * Update single cell and broadcast to subscribers
     */
    public void updateCell(String gridId, int row, int col, Object value, String dataType) {
        Grid grid = grids.get(gridId);
        if (grid != null) {
            grid.setCell(row, col, value, dataType);
            
            // Create single cell update
            CellUpdate update = new CellUpdate(row, col, value, dataType);
            CellUpdateMessage message = new CellUpdateMessage(gridId, Collections.singletonList(update));
            
            // Broadcast to all subscribers
            sessionManager.broadcastToGridSubscribers(gridId, message);
            
            log.debug("Updated cell [{},{}] in grid {} and broadcast to {} subscribers", 
                     row, col, gridId, sessionManager.getSubscriberCount(gridId));
        } else {
            log.warn("Attempted to update cell in non-existent grid: {}", gridId);
        }
    }
    
    /**
     * Process small batch updates from Kafka
     */
    public void updateCells(String gridId, List<CellChange> changes) {
        Grid grid = grids.get(gridId);
        if (grid == null) {
            log.warn("Attempted to update cells in non-existent grid: {}", gridId);
            return;
        }
        
        // Validate batch size according to spec.md
        if (!messageValidator.isValidBatchSize(changes)) {
            log.warn("Batch too large: {} cells for grid {}", changes.size(), gridId);
            return;
        }
        
        // Apply all changes to grid
        List<CellUpdate> updates = new ArrayList<>();
        for (CellChange change : changes) {
            grid.setCell(change.getRow(), change.getColumn(), 
                        change.getNewValue(), change.getDataType());
            
            updates.add(new CellUpdate(change.getRow(), change.getColumn(), 
                                     change.getNewValue(), change.getDataType()));
        }
        
        // Broadcast batch update (split if needed for WebSocket)
        broadcastBatchUpdate(gridId, updates);
        
        log.debug("Processed {} cell updates for grid {} and broadcast to {} subscribers", 
                 changes.size(), gridId, sessionManager.getSubscriberCount(gridId));
    }
    
    /**
     * Send initial load in small chunks via WebSocket
     * According to spec.md: Chunked WebSocket transfer (bypass Kafka)
     */
    public void sendInitialLoad(WebSocketSession session, String gridId) {
        Grid grid = grids.get(gridId);
        if (grid == null) {
            log.error("Grid not found for initial load: {}", gridId);
            return;
        }
        
        try {
            log.info("Starting initial load for grid {} to session {}", gridId, session.getId());
            
            // Send initial load start message
            InitialLoadStart startMsg = new InitialLoadStart(
                gridId, grid.getRows(), grid.getColumns(), 
                grid.getCellCount(), INITIAL_LOAD_CHUNK_SIZE
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(startMsg)));
            
            // Send data in chunks (spec.md: 1000 cells per chunk, ~50KB)
            List<Cell> allCells = grid.getAllCells();
            int totalChunks = (int) Math.ceil((double) allCells.size() / INITIAL_LOAD_CHUNK_SIZE);
            
            for (int i = 0; i < allCells.size(); i += INITIAL_LOAD_CHUNK_SIZE) {
                int end = Math.min(i + INITIAL_LOAD_CHUNK_SIZE, allCells.size());
                List<Cell> chunk = allCells.subList(i, end);
                
                int chunkIndex = i / INITIAL_LOAD_CHUNK_SIZE;
                boolean isLast = (end == allCells.size());
                
                InitialLoadChunk chunkMsg = new InitialLoadChunk(
                    gridId, chunkIndex, chunk, isLast
                );
                
                String chunkJson = objectMapper.writeValueAsString(chunkMsg);
                session.sendMessage(new TextMessage(chunkJson));
                
                log.debug("Sent chunk {}/{} ({} cells) for grid {}", 
                         chunkIndex + 1, totalChunks, chunk.size(), gridId);
                
                // Small delay to avoid overwhelming client (spec.md: <50ms latency target)
                Thread.sleep(5);
            }
            
            log.info("Completed initial load for grid {}: {} chunks, {} total cells", 
                    gridId, totalChunks, allCells.size());
            
        } catch (Exception e) {
            log.error("Error sending initial load for grid {}: {}", gridId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast batch update, splitting for WebSocket if needed
     */
    private void broadcastBatchUpdate(String gridId, List<CellUpdate> updates) {
        // Split large batches for WebSocket (even though Kafka was small)
        // spec.md: WebSocket max 50 cells per batch
        List<List<CellUpdate>> batches = splitUpdatesForWebSocket(updates);
        
        for (List<CellUpdate> batch : batches) {
            CellUpdateMessage message = new CellUpdateMessage(gridId, batch);
            sessionManager.broadcastToGridSubscribers(gridId, message);
        }
        
        log.debug("Broadcast {} updates in {} batches for grid {}", 
                 updates.size(), batches.size(), gridId);
    }
    
    /**
     * Split updates into WebSocket-appropriate batches
     */
    private List<List<CellUpdate>> splitUpdatesForWebSocket(List<CellUpdate> updates) {
        List<List<CellUpdate>> batches = new ArrayList<>();
        int maxWebSocketBatch = 50; // spec.md: max 50 cells per WebSocket batch
        
        for (int i = 0; i < updates.size(); i += maxWebSocketBatch) {
            int end = Math.min(i + maxWebSocketBatch, updates.size());
            batches.add(new ArrayList<>(updates.subList(i, end)));
        }
        
        return batches;
    }
    
    /**
     * Initialize grid with mock data according to spec.md
     */
    private void initializeGridData(Grid grid) {
        log.info("Initializing grid {} with mock data ({} × {} cells)", 
                grid.getGridId(), grid.getRows(), grid.getColumns());
        
        Random random = new Random();
        
        // Generate initial mock data for entire grid
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getColumns(); col++) {
                Object value;
                String dataType;
                
                // Varied data types as per spec.md (strings, numbers, timestamps)
                switch (col % 5) {
                    case 0:
                        value = "Row" + row + "Col" + col;
                        dataType = "string";
                        break;
                    case 1:
                        value = Math.round(random.nextDouble() * 1000 * 100.0) / 100.0;
                        dataType = "number";
                        break;
                    case 2:
                        value = random.nextInt(1000);
                        dataType = "integer";
                        break;
                    case 3:
                        value = System.currentTimeMillis() + random.nextInt(86400000); // Random timestamp
                        dataType = "timestamp";
                        break;
                    default:
                        value = random.nextBoolean();
                        dataType = "boolean";
                        break;
                }
                
                grid.setCell(row, col, value, dataType);
            }
        }
        
        log.info("Grid {} initialized with {} cells, estimated memory: {} bytes", 
                grid.getGridId(), grid.getCellCount(), grid.getEstimatedMemoryUsage());
        
        // Validate memory limits per spec.md
        if (!grid.isWithinMemoryLimits()) {
            log.warn("Grid {} exceeds memory limits: {} bytes", 
                    grid.getGridId(), grid.getEstimatedMemoryUsage());
        }
    }
    
    /**
     * Create standardized grid ID
     */
    private String createGridId(String userId, String viewId) {
        return userId + "_" + viewId;
    }
    
    /**
     * Get grid statistics for monitoring
     */
    public GridStats getGridStats() {
        long totalMemory = grids.values().stream()
                .mapToLong(Grid::getEstimatedMemoryUsage)
                .sum();
        
        int totalCells = grids.values().stream()
                .mapToInt(Grid::getCellCount)
                .sum();
        
        return new GridStats(grids.size(), totalCells, totalMemory);
    }
    
    /**
     * Remove grid (for cleanup/testing)
     */
    public boolean removeGrid(String gridId) {
        Grid removed = grids.remove(gridId);
        if (removed != null) {
            log.info("Removed grid: {}", gridId);
            return true;
        }
        return false;
    }
    
    /**
     * Grid statistics for monitoring
     */
    public static class GridStats {
        private final int gridCount;
        private final int totalCells;
        private final long totalMemoryUsage;
        
        public GridStats(int gridCount, int totalCells, long totalMemoryUsage) {
            this.gridCount = gridCount;
            this.totalCells = totalCells;
            this.totalMemoryUsage = totalMemoryUsage;
        }
        
        public int getGridCount() { return gridCount; }
        public int getTotalCells() { return totalCells; }
        public long getTotalMemoryUsage() { return totalMemoryUsage; }
        
        @Override
        public String toString() {
            return "GridStats{" +
                    "gridCount=" + gridCount +
                    ", totalCells=" + totalCells +
                    ", totalMemoryUsage=" + totalMemoryUsage + " bytes" +
                    '}';
        }
    }
} 