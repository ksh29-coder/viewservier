package com.viewserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viewserver.dto.GridEvent;
import com.viewserver.dto.CellChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kafka Event Processor for consuming grid update events
 * 
 * According to spec.md Message Size Strategy:
 * - Kafka Message Size: < 100KB per message (target: 10-50KB)
 * - Max 500 cells per message
 * - Small message strategy for low-latency processing
 * - Delta-only updates (never full grid state)
 * - Real-time processing and WebSocket broadcasting
 * 
 * Design Principles from spec.md:
 * - Process small batches quickly (<50ms target)
 * - Validate message sizes and content
 * - Update in-memory grid state
 * - Broadcast to WebSocket subscribers
 * - Handle processing errors gracefully
 */
@Service
@ConditionalOnProperty(value = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventProcessor.class);
    
    @Autowired
    private GridManager gridManager;
    
    @Autowired
    private MessageSizeValidator messageValidator;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Performance tracking
    private volatile long totalMessagesProcessed = 0;
    private volatile long totalCellsProcessed = 0;
    private volatile long lastProcessedTimestamp = System.currentTimeMillis();
    
    /**
     * Kafka listener for grid update events
     * 
     * Topic: grid-updates
     * Processing: Small batch updates (typically 5-50 cells, max 500)
     * Target latency: <50ms per message
     */
    @KafkaListener(
        topics = "grid-updates",
        groupId = "view-server-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processGridUpdateEvent(
            @Payload String messagePayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Received Kafka message from topic: {}, partition: {}, offset: {}", 
                     topic, partition, offset);
            
            // Parse grid event
            GridEvent gridEvent = objectMapper.readValue(messagePayload, GridEvent.class);
            
            // Validate message according to spec.md requirements
            if (!validateGridEvent(gridEvent)) {
                log.warn("Invalid grid event received: {}", gridEvent);
                return;
            }
            
            // Process the event
            processValidGridEvent(gridEvent);
            
            // Update performance metrics
            updatePerformanceMetrics(gridEvent, startTime);
            
            log.debug("Successfully processed grid event for {}: {} cells in {}ms", 
                     gridEvent.getGridId(), 
                     gridEvent.getChanges() != null ? gridEvent.getChanges().size() : 0,
                     System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            log.error("Error processing Kafka message from topic {}, partition {}, offset {}: {}", 
                     topic, partition, offset, e.getMessage(), e);
            
            // Log additional context for debugging
            log.debug("Failed message payload: {}", messagePayload);
        }
    }
    
    /**
     * Kafka listener for grid metadata events (grid creation, deletion)
     */
    @KafkaListener(
        topics = "grid-metadata",
        groupId = "view-server-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processGridMetadataEvent(
            @Payload String messagePayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        
        try {
            log.debug("Received grid metadata event from topic: {}", topic);
            
            // Parse metadata event (could be grid creation, deletion, etc.)
            GridEvent metadataEvent = objectMapper.readValue(messagePayload, GridEvent.class);
            
            switch (metadataEvent.getEventType()) {
                case "GRID_CREATED":
                    handleGridCreated(metadataEvent);
                    break;
                case "GRID_DELETED":
                    handleGridDeleted(metadataEvent);
                    break;
                case "GRID_CLEARED":
                    handleGridCleared(metadataEvent);
                    break;
                default:
                    log.warn("Unknown metadata event type: {}", metadataEvent.getEventType());
            }
            
        } catch (Exception e) {
            log.error("Error processing grid metadata event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Validate grid event according to spec.md requirements
     */
    private boolean validateGridEvent(GridEvent gridEvent) {
        if (gridEvent == null) {
            log.warn("Null grid event received");
            return false;
        }
        
        if (gridEvent.getGridId() == null || gridEvent.getGridId().isEmpty()) {
            log.warn("Grid event missing gridId");
            return false;
        }
        
        if (gridEvent.getChanges() == null) {
            log.warn("Grid event missing changes for grid: {}", gridEvent.getGridId());
            return false;
        }
        
        // Validate message size according to spec.md
        if (!messageValidator.isValidForKafka(gridEvent.getChanges())) {
            log.warn("Grid event exceeds size limits for grid {}: {} cells", 
                    gridEvent.getGridId(), gridEvent.getChanges().size());
            return false;
        }
        
        // Log message size classification for monitoring
        messageValidator.logMessageStats(gridEvent.getChanges(), "Kafka");
        
        return true;
    }
    
    /**
     * Process valid grid event
     */
    private void processValidGridEvent(GridEvent gridEvent) {
        String gridId = gridEvent.getGridId();
        List<CellChange> changes = gridEvent.getChanges();
        
        if (changes.isEmpty()) {
            log.debug("Empty changes list for grid: {}", gridId);
            return;
        }
        
        // Check if there are subscribers for this grid
        int subscriberCount = sessionManager.getSubscriberCount(gridId);
        if (subscriberCount == 0) {
            log.debug("No subscribers for grid {}, skipping cell updates", gridId);
            return;
        }
        
        // Update grid state and broadcast to subscribers
        // This will automatically handle WebSocket broadcasting
        gridManager.updateCells(gridId, changes);
        
        log.info("Processed {} cell changes for grid {} with {} subscribers", 
                changes.size(), gridId, subscriberCount);
    }
    
    /**
     * Handle grid creation metadata event
     */
    private void handleGridCreated(GridEvent event) {
        log.info("Grid created event for: {}", event.getGridId());
        // Grid will be created automatically when first WebSocket connection is made
        // This event can be used for logging/monitoring purposes
    }
    
    /**
     * Handle grid deletion metadata event
     */
    private void handleGridDeleted(GridEvent event) {
        log.info("Grid deletion event for: {}", event.getGridId());
        
        // Remove grid from memory
        boolean removed = gridManager.removeGrid(event.getGridId());
        if (removed) {
            log.info("Grid {} removed from memory", event.getGridId());
        } else {
            log.warn("Grid {} was not found for deletion", event.getGridId());
        }
    }
    
    /**
     * Handle grid clear metadata event
     */
    private void handleGridCleared(GridEvent event) {
        log.info("Grid clear event for: {}", event.getGridId());
        
        // Get grid and clear it
        var grid = gridManager.getGrid(event.getGridId());
        if (grid != null) {
            grid.clear();
            log.info("Grid {} cleared", event.getGridId());
            
            // Could broadcast a clear message to WebSocket subscribers here
        } else {
            log.warn("Grid {} not found for clearing", event.getGridId());
        }
    }
    
    /**
     * Update performance metrics for monitoring
     */
    private void updatePerformanceMetrics(GridEvent gridEvent, long startTime) {
        totalMessagesProcessed++;
        if (gridEvent.getChanges() != null) {
            totalCellsProcessed += gridEvent.getChanges().size();
        }
        lastProcessedTimestamp = System.currentTimeMillis();
        
        long processingTime = lastProcessedTimestamp - startTime;
        
        // Log performance warnings if processing takes too long
        if (processingTime > 50) { // spec.md: <50ms target
            log.warn("Slow Kafka message processing: {}ms for {} cells in grid {}", 
                    processingTime, 
                    gridEvent.getChanges() != null ? gridEvent.getChanges().size() : 0,
                    gridEvent.getGridId());
        }
        
        // Log periodic statistics
        if (totalMessagesProcessed % 100 == 0) {
            log.info("Kafka processing stats: {} messages, {} cells processed, avg processing time: {}ms", 
                    totalMessagesProcessed, totalCellsProcessed, 
                    processingTime);
        }
    }
    
    /**
     * Get processing statistics for monitoring
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(
            totalMessagesProcessed,
            totalCellsProcessed,
            lastProcessedTimestamp,
            System.currentTimeMillis() - lastProcessedTimestamp
        );
    }
    
    /**
     * Reset processing statistics (for testing/monitoring)
     */
    public void resetStats() {
        totalMessagesProcessed = 0;
        totalCellsProcessed = 0;
        lastProcessedTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Processing statistics for monitoring
     */
    public static class ProcessingStats {
        private final long totalMessages;
        private final long totalCells;
        private final long lastProcessedTimestamp;
        private final long timeSinceLastMessage;
        
        public ProcessingStats(long totalMessages, long totalCells, long lastProcessedTimestamp, long timeSinceLastMessage) {
            this.totalMessages = totalMessages;
            this.totalCells = totalCells;
            this.lastProcessedTimestamp = lastProcessedTimestamp;
            this.timeSinceLastMessage = timeSinceLastMessage;
        }
        
        public long getTotalMessages() { return totalMessages; }
        public long getTotalCells() { return totalCells; }
        public long getLastProcessedTimestamp() { return lastProcessedTimestamp; }
        public long getTimeSinceLastMessage() { return timeSinceLastMessage; }
        
        public double getAverageCellsPerMessage() {
            return totalMessages > 0 ? (double) totalCells / totalMessages : 0.0;
        }
        
        @Override
        public String toString() {
            return "ProcessingStats{" +
                    "totalMessages=" + totalMessages +
                    ", totalCells=" + totalCells +
                    ", lastProcessedTimestamp=" + lastProcessedTimestamp +
                    ", timeSinceLastMessage=" + timeSinceLastMessage +
                    ", avgCellsPerMessage=" + String.format("%.1f", getAverageCellsPerMessage()) +
                    '}';
        }
    }
} 