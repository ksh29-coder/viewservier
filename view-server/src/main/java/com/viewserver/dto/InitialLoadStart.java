package com.viewserver.dto;

/**
 * WebSocket message for initial load start
 * 
 * According to spec.md WebSocket message format:
 * Initial Load Start
 * {
 *   "type": "INITIAL_LOAD_START",
 *   "gridId": "user123_view456",
 *   "rows": 10000,
 *   "columns": 100,
 *   "totalCells": 1000000,
 *   "chunkSize": 1000
 * }
 */
public class InitialLoadStart {
    private String type = "INITIAL_LOAD_START";
    private String gridId;
    private int rows;
    private int columns;
    private int totalCells;
    private int chunkSize;

    public InitialLoadStart() {
        // Default constructor for Jackson
    }

    public InitialLoadStart(String gridId, int rows, int columns, int totalCells, int chunkSize) {
        this.gridId = gridId;
        this.rows = rows;
        this.columns = columns;
        this.totalCells = totalCells;
        this.chunkSize = chunkSize;
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGridId() {
        return gridId;
    }

    public void setGridId(String gridId) {
        this.gridId = gridId;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    public int getTotalCells() {
        return totalCells;
    }

    public void setTotalCells(int totalCells) {
        this.totalCells = totalCells;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    public String toString() {
        return "InitialLoadStart{" +
                "type='" + type + '\'' +
                ", gridId='" + gridId + '\'' +
                ", rows=" + rows +
                ", columns=" + columns +
                ", totalCells=" + totalCells +
                ", chunkSize=" + chunkSize +
                '}';
    }
} 