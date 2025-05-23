package com.viewserver.service;

import com.viewserver.dto.CellChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Message Size Validator for ensuring compliance with spec.md limits
 * 
 * According to spec.md Message Size Strategy:
 * - Kafka Message Size: < 100KB per message (target: 10-50KB)  
 * - Max 500 cells per Kafka message
 * - WebSocket batch max: 50 cells
 * - Typical cell size: ~100 bytes
 * 
 * Design Principles from spec.md:
 * - Delta-Only Updates: Never send full grid state via Kafka
 * - Small Batches: Maximum 500 cells per Kafka message
 * - Real-time Focus: Optimize for low-latency small updates
 * - Size Monitoring: Track and alert on message sizes
 */
@Service
public class MessageSizeValidator {
    private static final Logger log = LoggerFactory.getLogger(MessageSizeValidator.class);
    
    // Constants from spec.md
    private static final int MAX_KAFKA_MESSAGE_SIZE = 100_000; // 100KB hard limit
    private static final int TARGET_KAFKA_MESSAGE_SIZE = 20_000; // 20KB target
    private static final int MAX_CELLS_PER_MESSAGE = 500; // Max cells per Kafka message
    private static final int TYPICAL_CELL_SIZE = 100; // bytes per cell estimate
    private static final int MAX_WEBSOCKET_BATCH = 50; // Max cells per WebSocket batch
    
    /**
     * Validate if cell changes are valid for Kafka according to spec.md limits
     */
    public boolean isValidForKafka(List<CellChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return true;
        }
        
        // Check cell count limit
        if (changes.size() > MAX_CELLS_PER_MESSAGE) {
            log.warn("Message exceeds max cells: {} > {}", changes.size(), MAX_CELLS_PER_MESSAGE);
            return false;
        }
        
        // Check estimated size
        int estimatedSize = estimateKafkaMessageSize(changes);
        if (estimatedSize >= MAX_KAFKA_MESSAGE_SIZE) {
            log.warn("Message exceeds size limit: {} bytes > {} bytes", estimatedSize, MAX_KAFKA_MESSAGE_SIZE);
            return false;
        }
        
        return true;
    }
    
    /**
     * Simple batch size validation for quick checks
     */
    public boolean isValidBatchSize(List<CellChange> changes) {
        return changes == null || changes.size() <= MAX_CELLS_PER_MESSAGE;
    }
    
    /**
     * Validate for WebSocket transmission (smaller batches)
     */
    public boolean isValidForWebSocket(List<CellChange> changes) {
        return changes == null || changes.size() <= MAX_WEBSOCKET_BATCH;
    }
    
    /**
     * Check if message size is within target range (not just limits)
     */
    public boolean isWithinTargetSize(List<CellChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return true;
        }
        
        int estimatedSize = estimateKafkaMessageSize(changes);
        return estimatedSize <= TARGET_KAFKA_MESSAGE_SIZE;
    }
    
    /**
     * Split large batch into valid Kafka-sized batches
     */
    public List<List<CellChange>> splitIntoValidBatches(List<CellChange> changes) {
        List<List<CellChange>> batches = new ArrayList<>();
        
        if (changes == null || changes.isEmpty()) {
            return batches;
        }
        
        for (int i = 0; i < changes.size(); i += MAX_CELLS_PER_MESSAGE) {
            int end = Math.min(i + MAX_CELLS_PER_MESSAGE, changes.size());
            List<CellChange> batch = changes.subList(i, end);
            
            // Validate batch size
            if (isValidForKafka(batch)) {
                batches.add(new ArrayList<>(batch)); // Create defensive copy
            } else {
                // Further split if needed (shouldn't happen with current logic)
                batches.addAll(splitLargeBatch(batch));
            }
        }
        
        log.debug("Split {} changes into {} batches", changes.size(), batches.size());
        return batches;
    }
    
    /**
     * Split into WebSocket-sized batches (smaller than Kafka)
     */
    public List<List<CellChange>> splitIntoWebSocketBatches(List<CellChange> changes) {
        List<List<CellChange>> batches = new ArrayList<>();
        
        if (changes == null || changes.isEmpty()) {
            return batches;
        }
        
        for (int i = 0; i < changes.size(); i += MAX_WEBSOCKET_BATCH) {
            int end = Math.min(i + MAX_WEBSOCKET_BATCH, changes.size());
            batches.add(new ArrayList<>(changes.subList(i, end)));
        }
        
        return batches;
    }
    
    /**
     * Get message size classification for monitoring
     */
    public String getMessageSizeClassification(List<CellChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "EMPTY";
        }
        
        int size = estimateKafkaMessageSize(changes);
        int cellCount = changes.size();
        
        if (size > MAX_KAFKA_MESSAGE_SIZE) {
            return "OVERSIZED";
        } else if (size > TARGET_KAFKA_MESSAGE_SIZE) {
            return "LARGE";
        } else if (cellCount > MAX_WEBSOCKET_BATCH) {
            return "MEDIUM";
        } else if (cellCount <= 10) {
            return "SMALL";
        } else {
            return "NORMAL";
        }
    }
    
    /**
     * Estimate Kafka message size in bytes
     */
    private int estimateKafkaMessageSize(List<CellChange> changes) {
        // Base message overhead (JSON structure, metadata)
        int baseSize = 200;
        
        // Cell changes overhead (rough estimate based on JSON serialization)
        int cellsSize = changes.size() * TYPICAL_CELL_SIZE;
        
        return baseSize + cellsSize;
    }
    
    /**
     * Split large batch into smaller chunks (fallback method)
     */
    private List<List<CellChange>> splitLargeBatch(List<CellChange> largeBatch) {
        List<List<CellChange>> smallBatches = new ArrayList<>();
        int smallerBatchSize = MAX_CELLS_PER_MESSAGE / 2; // Split in half
        
        for (int i = 0; i < largeBatch.size(); i += smallerBatchSize) {
            int end = Math.min(i + smallerBatchSize, largeBatch.size());
            smallBatches.add(new ArrayList<>(largeBatch.subList(i, end)));
        }
        
        log.warn("Had to split large batch of {} cells into {} smaller batches", 
                largeBatch.size(), smallBatches.size());
        return smallBatches;
    }
    
    /**
     * Log message statistics for monitoring
     */
    public void logMessageStats(List<CellChange> changes, String source) {
        if (changes == null) {
            return;
        }
        
        int size = estimateKafkaMessageSize(changes);
        String classification = getMessageSizeClassification(changes);
        
        log.debug("Message from {}: {} cells, ~{} bytes, classification: {}", 
                 source, changes.size(), size, classification);
        
        // Log warnings for problematic messages
        if ("OVERSIZED".equals(classification)) {
            log.error("OVERSIZED message from {}: {} cells, ~{} bytes", source, changes.size(), size);
        } else if ("LARGE".equals(classification)) {
            log.warn("Large message from {}: {} cells, ~{} bytes", source, changes.size(), size);
        }
    }
} 