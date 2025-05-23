package com.viewserver.dto;

import java.util.List;

/**
 * DTO for Kafka grid events
 * 
 * According to spec.md Kafka message format:
 * {
 *   "eventType": "CELL_UPDATE",
 *   "gridId": "user123_view456", 
 *   "timestamp": 1703123456789,
 *   "batchId": "batch_001",
 *   "changes": [...]
 * }
 * 
 * Message size validation: 500 cells × ~100 bytes = ~50KB (well under 100KB limit)
 */
public class GridEvent {
    private String eventType;
    private String gridId;
    private long timestamp;
    private String batchId;
    private List<CellChange> changes;

    public GridEvent() {
        // Default constructor for Jackson
    }

    public GridEvent(String eventType, String gridId, long timestamp, String batchId, List<CellChange> changes) {
        this.eventType = eventType;
        this.gridId = gridId;
        this.timestamp = timestamp;
        this.batchId = batchId;
        this.changes = changes;
    }

    // Getters and setters
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getGridId() {
        return gridId;
    }

    public void setGridId(String gridId) {
        this.gridId = gridId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public List<CellChange> getChanges() {
        return changes;
    }

    public void setChanges(List<CellChange> changes) {
        this.changes = changes;
    }

    /**
     * Get estimated message size in bytes for validation
     */
    public int getEstimatedSize() {
        // Base message overhead
        int baseSize = 200;
        
        // Changes overhead (rough estimate)
        int changesSize = changes != null ? changes.size() * 100 : 0;
        
        return baseSize + changesSize;
    }

    /**
     * Check if message is within spec.md size limits
     */
    public boolean isWithinSizeLimits() {
        return getEstimatedSize() < 100_000 && // 100KB limit
               (changes == null || changes.size() <= 500); // Max 500 cells
    }

    @Override
    public String toString() {
        return "GridEvent{" +
                "eventType='" + eventType + '\'' +
                ", gridId='" + gridId + '\'' +
                ", timestamp=" + timestamp +
                ", batchId='" + batchId + '\'' +
                ", changesCount=" + (changes != null ? changes.size() : 0) +
                ", estimatedSize=" + getEstimatedSize() + " bytes" +
                '}';
    }
} 