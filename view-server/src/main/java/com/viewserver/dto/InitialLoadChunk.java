package com.viewserver.dto;

import com.viewserver.model.Cell;
import java.util.List;

/**
 * WebSocket message for initial load chunks
 * 
 * According to spec.md WebSocket message format:
 * Initial Load Chunk (1000 cells, ~50KB)
 * {
 *   "type": "INITIAL_LOAD_CHUNK",
 *   "gridId": "user123_view456",
 *   "chunkIndex": 0,
 *   "isLast": false,
 *   "cells": [...]
 * }
 */
public class InitialLoadChunk {
    private String type = "INITIAL_LOAD_CHUNK";
    private String gridId;
    private int chunkIndex;
    private boolean isLast;
    private List<Cell> cells;

    public InitialLoadChunk() {
        // Default constructor for Jackson
    }

    public InitialLoadChunk(String gridId, int chunkIndex, List<Cell> cells, boolean isLast) {
        this.gridId = gridId;
        this.chunkIndex = chunkIndex;
        this.cells = cells;
        this.isLast = isLast;
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

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public boolean isLast() {
        return isLast;
    }

    public void setLast(boolean last) {
        isLast = last;
    }

    public List<Cell> getCells() {
        return cells;
    }

    public void setCells(List<Cell> cells) {
        this.cells = cells;
    }

    @Override
    public String toString() {
        return "InitialLoadChunk{" +
                "type='" + type + '\'' +
                ", gridId='" + gridId + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", isLast=" + isLast +
                ", cellsCount=" + (cells != null ? cells.size() : 0) +
                '}';
    }
} 