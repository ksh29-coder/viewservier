package com.viewserver.dto;

import java.util.List;

/**
 * WebSocket message for cell updates
 * 
 * According to spec.md WebSocket message format:
 * Real-time Delta Update (1-50 cells, ~5KB)
 * {
 *   "type": "CELL_UPDATE",
 *   "gridId": "user123_view456",
 *   "updates": [...]
 * }
 */
public class CellUpdateMessage {
    private String type = "CELL_UPDATE";
    private String gridId;
    private List<CellUpdate> updates;

    public CellUpdateMessage() {
        // Default constructor for Jackson
    }

    public CellUpdateMessage(String gridId, List<CellUpdate> updates) {
        this.gridId = gridId;
        this.updates = updates;
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

    public List<CellUpdate> getUpdates() {
        return updates;
    }

    public void setUpdates(List<CellUpdate> updates) {
        this.updates = updates;
    }

    @Override
    public String toString() {
        return "CellUpdateMessage{" +
                "type='" + type + '\'' +
                ", gridId='" + gridId + '\'' +
                ", updatesCount=" + (updates != null ? updates.size() : 0) +
                '}';
    }
}