package com.viewserver.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grid model representing the complete grid state.
 * 
 * According to spec.md:
 * - Grid Size: 100 columns × 10,000 rows (1 million cells)
 * - Memory: ~50MB per grid, 5.1GB for 100 concurrent grids
 * - Thread-safe for concurrent access
 * - Efficient cell lookup using ConcurrentHashMap
 */
@Component
public class Grid {
    private final String gridId;
    private final String userId;
    private final String viewId;
    private final int rows;
    private final int columns;
    private final ConcurrentHashMap<String, Cell> cells;
    private volatile long lastModified;

    public Grid() {
        // Default constructor for Spring
        this("", "", "", 0, 0);
    }

    public Grid(String gridId, String userId, String viewId, int rows, int columns) {
        this.gridId = gridId;
        this.userId = userId;
        this.viewId = viewId;
        this.rows = rows;
        this.columns = columns;
        this.cells = new ConcurrentHashMap<>(rows * columns / 4); // Load factor optimization
        this.lastModified = System.currentTimeMillis();
    }

    /**
     * Set cell value with automatic timestamp and last modified update
     */
    public void setCell(int row, int col, Object value, String dataType) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) {
            throw new IllegalArgumentException("Cell coordinates out of bounds: " + row + "," + col);
        }
        
        String key = Cell.createKey(row, col);
        Cell cell = new Cell(row, col, value, dataType, System.currentTimeMillis());
        cells.put(key, cell);
        lastModified = System.currentTimeMillis();
    }

    /**
     * Get cell by coordinates
     */
    public Cell getCell(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) {
            return null;
        }
        return cells.get(Cell.createKey(row, col));
    }

    /**
     * Get all cells (for initial load chunking)
     * Returns a snapshot of current cells
     */
    public List<Cell> getAllCells() {
        return new ArrayList<>(cells.values());
    }

    /**
     * Get cells by range (for chunked loading)
     */
    public List<Cell> getCellsInRange(int startRow, int endRow, int startCol, int endCol) {
        List<Cell> rangeCells = new ArrayList<>();
        for (int row = startRow; row <= Math.min(endRow, rows - 1); row++) {
            for (int col = startCol; col <= Math.min(endCol, columns - 1); col++) {
                Cell cell = getCell(row, col);
                if (cell != null) {
                    rangeCells.add(cell);
                }
            }
        }
        return rangeCells;
    }

    /**
     * Get total number of cells actually stored
     */
    public int getCellCount() {
        return cells.size();
    }

    /**
     * Get estimated memory usage in bytes
     */
    public long getEstimatedMemoryUsage() {
        // Rough estimation: Cell object overhead + HashMap overhead
        long cellMemory = cells.size() * 100; // ~100 bytes per cell (rough estimate)
        long mapMemory = cells.size() * 64;   // HashMap overhead
        return cellMemory + mapMemory;
    }

    /**
     * Clear all cells (for testing/cleanup)
     */
    public void clear() {
        cells.clear();
        lastModified = System.currentTimeMillis();
    }

    /**
     * Check if grid is within spec.md memory limits
     */
    public boolean isWithinMemoryLimits() {
        long estimatedSize = getEstimatedMemoryUsage();
        long maxSizePerGrid = 51 * 1024 * 1024; // 51MB as per spec.md
        return estimatedSize <= maxSizePerGrid;
    }

    // Getters
    public String getGridId() {
        return gridId;
    }

    public String getUserId() {
        return userId;
    }

    public String getViewId() {
        return viewId;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public long getLastModified() {
        return lastModified;
    }

    @Override
    public String toString() {
        return "Grid{" +
                "gridId='" + gridId + '\'' +
                ", userId='" + userId + '\'' +
                ", viewId='" + viewId + '\'' +
                ", rows=" + rows +
                ", columns=" + columns +
                ", cellCount=" + cells.size() +
                ", lastModified=" + lastModified +
                ", memoryUsage=" + getEstimatedMemoryUsage() + " bytes" +
                '}';
    }
} 