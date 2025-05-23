# View Server Performance POC - Specification

## Project Overview

A performance proof-of-concept for a horizontally scalable view server that handles real-time grid updates via WebSocket connections. The system processes streaming events (Kafka) and pushes delta updates to connected frontend clients.

## Goals

1. **Performance Validation**: Measure throughput and latency for large-scale real-time grid updates
2. **Architecture Validation**: Prove the viability of the proposed multi-component architecture
3. **Scalability Assessment**: Understand bottlenecks and scaling characteristics
4. **Technology Validation**: Validate technology stack choices for production implementation

## Requirements

### Functional Requirements

#### FR1: Large Grid Support
- **Grid Size**: 100 columns × 10,000 rows (1 million cells)
- **Data Types**: Mixed (strings, numbers, timestamps)
- **Initial Load**: Full grid data on first connection via chunked WebSocket messages
- **Updates**: Small delta updates for changed cells only (max 500 cells per message)

#### FR2: Real-time Updates
- **WebSocket Connection**: Bidirectional communication
- **Update Frequency**: Up to 1000 updates/second per grid
- **Update Types**: Cell value changes only (no bulk operations)
- **Broadcasting**: Updates pushed to all subscribers of a view
- **Message Size**: Each Kafka message < 100KB (target: 10-50KB)

#### FR3: Event Streaming Integration
- **Source**: Kafka event stream
- **Event Types**: Small cell updates only (1-500 cells per message)
- **Processing**: Real-time event consumption and grid state updates
- **Durability**: In-memory state (POC phase)
- **Message Limits**: All Kafka messages under 100KB

#### FR4: Multi-View Support
- **User Views**: Each user can have multiple grid views
- **View Differentiation**: Different data sets, filters, or transformations per view
- **Subscription Management**: Users subscribe/unsubscribe to specific views

### Non-Functional Requirements

#### NFR1: Performance Targets
- **Concurrent Connections**: 1,000 WebSocket connections (POC phase)
- **Update Latency**: < 50ms from Kafka event to WebSocket client
- **Memory Usage**: < 16GB for POC dataset
- **CPU Usage**: < 80% under normal load
- **Message Throughput**: 10,000 small messages/second via Kafka

#### NFR2: Scalability
- **Horizontal Scaling**: Architecture must support multiple server instances
- **Load Distribution**: Even distribution of connections across servers
- **State Management**: Shared state between server instances

#### NFR3: Reliability
- **Connection Recovery**: Automatic WebSocket reconnection
- **Data Consistency**: Eventual consistency for grid state
- **Error Handling**: Graceful degradation on component failures

## Architecture Overview

### High-Level Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   React Grid    │────│   View Server    │────│  Kafka Cluster  │
│   Frontend      │    │  (Spring Boot)   │    │   (Small Msgs)  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                       ┌──────────────────┐
                       │   Mock Data      │
                       │   Generator      │
                       └──────────────────┘
```

### Data Flow Strategy

#### **Small Messages Only:**
- **Kafka**: Only small delta updates (1-500 cells, <100KB per message)
- **Initial Load**: Direct WebSocket chunked transfer (bypass Kafka)
- **Bulk Operations**: Not supported in POC (would be split into small updates)

### Component Architecture

#### View Server (Spring Boot)
- **WebSocket Handler**: Manages client connections and message routing
- **Grid Manager**: In-memory grid state management
- **Kafka Consumer**: Processes small streaming events only
- **Initial Load Handler**: Chunked WebSocket transfer for grid initialization
- **REST API**: Health checks and admin operations

#### Frontend (React)
- **WebSocket Client**: Connection management and message handling
- **Virtualized Grid**: Efficient rendering of large datasets
- **Chunked Loader**: Handles initial grid loading in small chunks
- **State Management**: Client-side state synchronization

#### Mock Data Generator
- **Event Producer**: Generates small, realistic grid update events
- **Load Simulation**: Configurable update frequency and patterns
- **Batch Controller**: Ensures all messages stay under size limits

## Technical Specifications

### Data Models

#### Grid Model
```java
public class Grid {
    private String gridId;
    private String userId;
    private String viewId;
    private int rows;
    private int columns;
    private Map<String, Cell> cells; // "row:col" -> Cell
    private long lastModified;
}

public class Cell {
    private int row;
    private int column;
    private Object value;
    private String dataType;
    private long timestamp;
}
```

#### Kafka Messages (Small Only)
```json
// Small Cell Update (typical: 5-50 cells, ~2-20KB)
{
    "eventType": "CELL_UPDATE",
    "gridId": "user123_view456",
    "timestamp": 1703123456789,
    "batchId": "batch_001",
    "changes": [
        {
            "row": 1234,
            "column": 56,
            "newValue": 789.12,
            "dataType": "number"
        }
        // Max 500 cells per message
    ]
}

// Message size validation
// 500 cells × ~100 bytes = ~50KB (well under 100KB limit)
```

#### WebSocket Messages
```json
// Initial Load Start
{
    "type": "INITIAL_LOAD_START",
    "gridId": "user123_view456",
    "rows": 10000,
    "columns": 100,
    "totalCells": 1000000,
    "chunkSize": 1000
}

// Initial Load Chunk (1000 cells, ~50KB)
{
    "type": "INITIAL_LOAD_CHUNK",
    "gridId": "user123_view456",
    "chunkIndex": 0,
    "isLast": false,
    "cells": [
        {"row": 0, "col": 0, "value": "data", "type": "string"},
        // ... 999 more cells
    ]
}

// Real-time Delta Update (1-50 cells, ~5KB)
{
    "type": "CELL_UPDATE",
    "gridId": "user123_view456",
    "updates": [
        {"row": 1234, "col": 56, "value": 789.12, "type": "number"}
    ]
}
```

### Message Size Strategy

#### **Kafka Message Limits**
```java
// Message size validation
public class MessageSizeValidator {
    private static final int MAX_KAFKA_MESSAGE_SIZE = 100_000; // 100KB
    private static final int MAX_CELLS_PER_MESSAGE = 500;
    private static final int TYPICAL_CELL_SIZE = 100; // bytes
    
    public boolean isValidForKafka(List<CellChange> changes) {
        return changes.size() <= MAX_CELLS_PER_MESSAGE && 
               estimateSize(changes) < MAX_KAFKA_MESSAGE_SIZE;
    }
}
```

#### **WebSocket Chunk Strategy**
```java
// Initial load chunking
public class InitialLoadChunker {
    private static final int CHUNK_SIZE = 1000; // cells per chunk
    private static final int MAX_CHUNK_SIZE_BYTES = 100_000; // 100KB
    
    public List<List<Cell>> createChunks(List<Cell> allCells) {
        // Split into chunks of 1000 cells each
        // Validate each chunk is under 100KB
    }
}
```

### API Specifications

#### WebSocket Endpoints
- **Connect**: `/ws/grid/{userId}/{viewId}`
- **Subscribe**: Send subscription message for specific grid
- **Request Initial Load**: Automatic on connection
- **Unsubscribe**: Remove subscription for grid

#### REST Endpoints
- **Health Check**: `GET /health`
- **Grid Status**: `GET /api/grids/{gridId}/status`
- **Metrics**: `GET /api/metrics`
- **Message Stats**: `GET /api/metrics/messages` (size, throughput)

### Technology Stack

#### Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.2
- **WebSocket**: Spring WebSocket
- **Kafka Client**: Spring Kafka
- **Build Tool**: Maven
- **JVM Settings**: -Xmx16g -XX:+UseG1GC

#### Frontend
- **Framework**: React 18
- **Grid Component**: React Window (virtualization)
- **WebSocket**: Native WebSocket API
- **Build Tool**: Vite
- **Styling**: Tailwind CSS

#### Infrastructure
- **Message Broker**: Apache Kafka 3.6 (default 1MB message limit)
- **Container**: Docker & Docker Compose
- **Monitoring**: Micrometer + Prometheus (optional)

### Performance Specifications

#### Memory Layout
```
Grid Storage (per grid):
- 1M cells × 50 bytes/cell = 50MB
- Metadata overhead = 1MB
- Total per grid = ~51MB

POC Limits:
- 100 concurrent grids = 5.1GB
- JVM overhead = 2GB
- Available heap = 16GB
- Safety margin = 8.9GB
```

#### Message Size Specifications
```
Kafka Messages:
- Typical cell update: 5-50 cells = 2-20KB
- Maximum cell update: 500 cells = ~50KB
- Hard limit: 100KB per message
- Target: 90% of messages under 20KB

WebSocket Messages:
- Initial load chunk: 1000 cells = ~50KB
- Real-time updates: 1-50 cells = 1-10KB
- Progress notifications: <1KB
```

#### Network Specifications
- **Kafka Message Size**: < 100KB per message (target: 10-50KB)
- **WebSocket Frame Size**: < 100KB per frame
- **Message Compression**: Optional gzip compression
- **Heartbeat Interval**: 30 seconds
- **Reconnection Strategy**: Exponential backoff

#### Update Batching Strategy
```java
// Small batch configuration
public class UpdateBatcher {
    private static final int MAX_BATCH_SIZE = 50;        // cells
    private static final int MAX_BATCH_WAIT_MS = 10;     // milliseconds
    private static final int MAX_BATCH_SIZE_BYTES = 20_000; // 20KB target
    
    // Batch updates but keep messages small
    public void batchUpdates(List<CellUpdate> updates) {
        // Split into small batches
        // Send immediately if batch gets too large
        // Don't wait more than 10ms
    }
}
```

## Success Criteria

### Performance Metrics
1. **Latency**: 95th percentile < 50ms (Kafka → WebSocket)
2. **Throughput**: 10,000 small messages/second across all grids
3. **Memory**: Stable memory usage under sustained load
4. **Connections**: 1,000 concurrent WebSocket connections
5. **Message Size**: 95% of Kafka messages under 20KB

### Functional Validation
1. **Data Integrity**: No data loss or corruption
2. **Real-time Updates**: All subscribed clients receive updates
3. **Grid Operations**: Initial load, updates, subscriptions work correctly
4. **Error Recovery**: System handles network failures gracefully
5. **Message Size Compliance**: All Kafka messages under 100KB

### Scalability Assessment
1. **Resource Utilization**: CPU, memory, network usage under load
2. **Bottleneck Identification**: Primary scaling limitations
3. **Horizontal Scaling**: Multiple server instance compatibility
4. **Load Distribution**: Even resource usage across components
5. **Message Throughput**: Sustainable small message processing

## Message Size Constraints

### **Design Principles**
1. **Delta-Only Updates**: Never send full grid state via Kafka
2. **Small Batches**: Maximum 500 cells per Kafka message
3. **Chunked Initial Load**: Use WebSocket for large data transfer
4. **Real-time Focus**: Optimize for low-latency small updates
5. **Size Monitoring**: Track and alert on message sizes

### **Out of Scope (POC Phase)**
- **Large Bulk Operations**: No support for >500 cell updates
- **File Attachments**: No binary data in cells
- **Complex Data Types**: No nested objects in cell values
- **Schema Changes**: No dynamic column additions
- **Persistence**: No database integration
- **Authentication**: No user authentication/authorization
- **Cross-Region**: Single region deployment only
- **Production Monitoring**: Basic metrics only 