# View Server Performance POC - Implementation Plan

## Project Structure

```
viewserver-poc/
├── view-server/                    # Spring Boot backend
│   ├── src/main/java/com/viewserver/
│   │   ├── ViewServerApplication.java
│   │   ├── config/
│   │   │   ├── WebSocketConfig.java
│   │   │   ├── KafkaConfig.java
│   │   │   └── SchedulingConfig.java
│   │   ├── model/
│   │   │   ├── Grid.java
│   │   │   ├── Cell.java
│   │   │   ├── GridUpdate.java
│   │   │   └── WebSocketMessage.java
│   │   ├── service/
│   │   │   ├── GridManager.java
│   │   │   ├── WebSocketSessionManager.java
│   │   │   ├── KafkaEventProcessor.java
│   │   │   └── MetricsService.java
│   │   ├── controller/
│   │   │   ├── GridWebSocketHandler.java
│   │   │   ├── HealthController.java
│   │   │   └── MetricsController.java
│   │   └── dto/
│   │       ├── InitialLoadResponse.java
│   │       ├── CellUpdateMessage.java
│   │       └── SubscriptionMessage.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── logback-spring.xml
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                       # React frontend
│   ├── src/
│   │   ├── components/
│   │   │   ├── GridView.jsx
│   │   │   ├── VirtualizedGrid.jsx
│   │   │   ├── ConnectionStatus.jsx
│   │   │   └── MetricsDisplay.jsx
│   │   ├── hooks/
│   │   │   ├── useWebSocket.js
│   │   │   ├── useGridData.js
│   │   │   └── useMetrics.js
│   │   ├── services/
│   │   │   ├── WebSocketService.js
│   │   │   └── GridDataService.js
│   │   ├── utils/
│   │   │   ├── dataTypes.js
│   │   │   └── formatters.js
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── package.json
│   ├── vite.config.js
│   └── Dockerfile
├── mock-data-generator/            # Kafka event generator
│   ├── src/main/java/com/mockdata/
│   │   ├── MockDataGeneratorApplication.java
│   │   ├── config/
│   │   │   └── KafkaProducerConfig.java
│   │   ├── model/
│   │   │   ├── GridEvent.java
│   │   │   └── CellChange.java
│   │   ├── service/
│   │   │   ├── EventGenerator.java
│   │   │   ├── DataPatternService.java
│   │   │   └── LoadSimulator.java
│   │   └── scheduler/
│   │       └── EventScheduler.java
│   ├── pom.xml
│   └── Dockerfile
├── docker-compose.yml
├── setup.sh
└── README.md
```

## Implementation Steps

### Phase 1: Infrastructure Setup

#### Step 1.1: Project Initialization
```bash
# Create root directory structure
mkdir -p viewserver-poc/{view-server,frontend,mock-data-generator}
cd viewserver-poc

# Initialize Git repository
git init
```

#### Step 1.2: Docker Compose Setup
**File: `docker-compose.yml`**
```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true

  view-server:
    build: ./view-server
    ports:
      - "8080:8080"
    depends_on:
      - kafka
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - JVM_OPTS=-Xmx16g -XX:+UseG1GC

  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    depends_on:
      - view-server

  mock-data-generator:
    build: ./mock-data-generator
    depends_on:
      - kafka
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

### Phase 2: Backend Implementation (View Server)

#### Step 2.1: Spring Boot Project Setup
**File: `view-server/pom.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>
    
    <groupId>com.viewserver</groupId>
    <artifactId>view-server</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <properties>
        <java.version>17</java.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### Step 2.2: Core Data Models
**File: `view-server/src/main/java/com/viewserver/model/Grid.java`**
```java
@Component
public class Grid {
    private final String gridId;
    private final String userId;
    private final String viewId;
    private final int rows;
    private final int columns;
    private final ConcurrentHashMap<String, Cell> cells;
    private volatile long lastModified;
    
    // Constructor, getters, methods for cell operations
    public void setCell(int row, int col, Object value, String dataType) {
        String key = row + ":" + col;
        cells.put(key, new Cell(row, col, value, dataType, System.currentTimeMillis()));
        lastModified = System.currentTimeMillis();
    }
    
    public Cell getCell(int row, int col) {
        return cells.get(row + ":" + col);
    }
    
    public List<Cell> getAllCells() {
        return new ArrayList<>(cells.values());
    }
}
```

**File: `view-server/src/main/java/com/viewserver/model/Cell.java`**
```java
public class Cell {
    private final int row;
    private final int column;
    private final Object value;
    private final String dataType;
    private final long timestamp;
    
    // Constructor, getters, equals, hashCode
}
```

#### Step 2.3: WebSocket Configuration
**File: `view-server/src/main/java/com/viewserver/config/WebSocketConfig.java`**
```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private GridWebSocketHandler gridWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gridWebSocketHandler, "/ws/grid/{userId}/{viewId}")
                .setAllowedOrigins("*");
    }
}
```

#### Step 2.4: WebSocket Handler Implementation
**File: `view-server/src/main/java/com/viewserver/controller/GridWebSocketHandler.java`**
```java
@Component
public class GridWebSocketHandler extends TextWebSocketHandler {
    
    private final GridManager gridManager;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Extract userId and viewId from session URI
        // Register session with SessionManager
        // Send initial grid data
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Handle subscription/unsubscription messages
        // Process client commands
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Clean up session
        // Remove subscriptions
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // Log error and close session
    }
}
```

#### Step 2.5: Grid Manager Service
**File: `view-server/src/main/java/com/viewserver/service/GridManager.java`**
```java
@Service
public class GridManager {
    private final ConcurrentHashMap<String, Grid> grids = new ConcurrentHashMap<>();
    private final WebSocketSessionManager sessionManager;
    private final MessageSizeValidator messageValidator;
    private static final int INITIAL_LOAD_CHUNK_SIZE = 1000;
    
    public Grid createGrid(String userId, String viewId, int rows, int columns) {
        String gridId = userId + "_" + viewId;
        Grid grid = new Grid(gridId, userId, viewId, rows, columns);
        
        // Initialize with mock data
        initializeGridData(grid);
        
        grids.put(gridId, grid);
        return grid;
    }
    
    public void updateCell(String gridId, int row, int col, Object value, String dataType) {
        Grid grid = grids.get(gridId);
        if (grid != null) {
            grid.setCell(row, col, value, dataType);
            
            // Broadcast update to all subscribers
            broadcastCellUpdate(gridId, row, col, value, dataType);
        }
    }
    
    // Process small batch updates from Kafka
    public void updateCells(String gridId, List<CellChange> changes) {
        Grid grid = grids.get(gridId);
        if (grid != null) {
            // Validate batch size
            if (!messageValidator.isValidBatchSize(changes)) {
                log.warn("Batch too large: {} cells for grid {}", changes.size(), gridId);
                return;
            }
            
            // Apply all changes
            List<CellUpdate> updates = new ArrayList<>();
            for (CellChange change : changes) {
                grid.setCell(change.getRow(), change.getColumn(), 
                           change.getNewValue(), change.getDataType());
                updates.add(new CellUpdate(change.getRow(), change.getColumn(), 
                                         change.getNewValue(), change.getDataType()));
            }
            
            // Broadcast batch update
            broadcastBatchUpdate(gridId, updates);
        }
    }
    
    // Send initial load in small chunks via WebSocket
    public void sendInitialLoad(WebSocketSession session, String gridId) {
        Grid grid = grids.get(gridId);
        if (grid == null) {
            log.error("Grid not found: {}", gridId);
            return;
        }
        
        try {
            // Send initial load start message
            InitialLoadStart startMsg = new InitialLoadStart(
                gridId, grid.getRows(), grid.getColumns(), 
                grid.getRows() * grid.getColumns(), INITIAL_LOAD_CHUNK_SIZE
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(startMsg)));
            
            // Send data in chunks
            List<Cell> allCells = grid.getAllCells();
            for (int i = 0; i < allCells.size(); i += INITIAL_LOAD_CHUNK_SIZE) {
                int end = Math.min(i + INITIAL_LOAD_CHUNK_SIZE, allCells.size());
                List<Cell> chunk = allCells.subList(i, end);
                
                InitialLoadChunk chunkMsg = new InitialLoadChunk(
                    gridId, i / INITIAL_LOAD_CHUNK_SIZE, chunk, 
                    end == allCells.size()
                );
                
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chunkMsg)));
                
                // Small delay to avoid overwhelming client
                Thread.sleep(5);
            }
        } catch (Exception e) {
            log.error("Error sending initial load for grid: {}", gridId, e);
        }
    }
    
    private void broadcastCellUpdate(String gridId, int row, int col, Object value, String dataType) {
        CellUpdate update = new CellUpdate(row, col, value, dataType);
        CellUpdateMessage message = new CellUpdateMessage(gridId, Collections.singletonList(update));
        sessionManager.broadcastToGridSubscribers(gridId, message);
    }
    
    private void broadcastBatchUpdate(String gridId, List<CellUpdate> updates) {
        // Split large batches for WebSocket (even though Kafka was small)
        int maxWebSocketBatch = 50; // Keep WebSocket messages small too
        
        for (int i = 0; i < updates.size(); i += maxWebSocketBatch) {
            int end = Math.min(i + maxWebSocketBatch, updates.size());
            List<CellUpdate> batch = updates.subList(i, end);
            
            CellUpdateMessage message = new CellUpdateMessage(gridId, batch);
            sessionManager.broadcastToGridSubscribers(gridId, message);
        }
    }
    
    private void initializeGridData(Grid grid) {
        // Generate initial mock data
        Random random = new Random();
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getColumns(); col++) {
                if (col % 5 == 0) {
                    grid.setCell(row, col, "Row" + row + "Col" + col, "string");
                } else {
                    grid.setCell(row, col, random.nextDouble() * 1000, "number");
                }
            }
        }
    }
}
```

#### Step 2.6: Message Size Validator
**File: `view-server/src/main/java/com/viewserver/service/MessageSizeValidator.java`**
```java
@Service
public class MessageSizeValidator {
    private static final int MAX_KAFKA_MESSAGE_SIZE = 100_000; // 100KB
    private static final int MAX_CELLS_PER_MESSAGE = 500;
    private static final int TYPICAL_CELL_SIZE = 100; // bytes
    private static final int MAX_WEBSOCKET_BATCH = 50; // cells
    
    public boolean isValidForKafka(List<CellChange> changes) {
        if (changes.size() > MAX_CELLS_PER_MESSAGE) {
            return false;
        }
        
        int estimatedSize = estimateKafkaMessageSize(changes);
        return estimatedSize < MAX_KAFKA_MESSAGE_SIZE;
    }
    
    public boolean isValidBatchSize(List<CellChange> changes) {
        return changes.size() <= MAX_CELLS_PER_MESSAGE;
    }
    
    public List<List<CellChange>> splitIntoValidBatches(List<CellChange> changes) {
        List<List<CellChange>> batches = new ArrayList<>();
        
        for (int i = 0; i < changes.size(); i += MAX_CELLS_PER_MESSAGE) {
            int end = Math.min(i + MAX_CELLS_PER_MESSAGE, changes.size());
            List<CellChange> batch = changes.subList(i, end);
            
            // Validate batch size
            if (isValidForKafka(batch)) {
                batches.add(batch);
            } else {
                // Further split if needed
                batches.addAll(splitLargeBatch(batch));
            }
        }
        
        return batches;
    }
    
    private List<List<CellChange>> splitLargeBatch(List<CellChange> largeBatch) {
        List<List<CellChange>> smallBatches = new ArrayList<>();
        int smallerBatchSize = MAX_CELLS_PER_MESSAGE / 2; // Split in half
        
        for (int i = 0; i < largeBatch.size(); i += smallerBatchSize) {
            int end = Math.min(i + smallerBatchSize, largeBatch.size());
            smallBatches.add(largeBatch.subList(i, end));
        }
        
        return smallBatches;
    }
    
    private int estimateKafkaMessageSize(List<CellChange> changes) {
        // Estimate JSON size
        int baseSize = 200; // Base message overhead
        int cellsSize = changes.size() * TYPICAL_CELL_SIZE;
        return baseSize + cellsSize;
    }
}
```

#### Step 2.7: Kafka Event Processor (Updated)
**File: `view-server/src/main/java/com/viewserver/service/KafkaEventProcessor.java`**
```java
@Service
public class KafkaEventProcessor {
    
    private final GridManager gridManager;
    private final ObjectMapper objectMapper;
    private final MessageSizeValidator messageValidator;
    
    @KafkaListener(topics = "grid-events", groupId = "view-server-group")
    public void processGridEvent(String eventJson) {
        try {
            GridEvent event = objectMapper.readValue(eventJson, GridEvent.class);
            
            // Validate message size (should always be small)
            if (!messageValidator.isValidBatchSize(event.getChanges())) {
                log.error("Received oversized message: {} cells for grid {}", 
                         event.getChanges().size(), event.getGridId());
                return;
            }
            
            switch (event.getEventType()) {
                case "CELL_UPDATE":
                    processCellUpdate(event);
                    break;
                default:
                    log.warn("Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing Kafka event: {}", eventJson, e);
        }
    }
    
    private void processCellUpdate(GridEvent event) {
        // Process small batch of cell changes
        gridManager.updateCells(event.getGridId(), event.getChanges());
        
        // Log message stats for monitoring
        log.debug("Processed {} cell updates for grid {}", 
                 event.getChanges().size(), event.getGridId());
    }
}
```

#### Step 2.8: WebSocket Handler (Updated)
**File: `view-server/src/main/java/com/viewserver/controller/GridWebSocketHandler.java`**
```java
@Component
public class GridWebSocketHandler extends TextWebSocketHandler {
    
    private final GridManager gridManager;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            // Extract userId and viewId from session URI
            String path = session.getUri().getPath();
            String[] pathParts = path.split("/");
            String userId = pathParts[3];
            String viewId = pathParts[4];
            String gridId = userId + "_" + viewId;
            
            log.info("WebSocket connected: userId={}, viewId={}", userId, viewId);
            
            // Register session with SessionManager
            sessionManager.addSession(gridId, session);
            
            // Create or get existing grid
            Grid grid = gridManager.getOrCreateGrid(userId, viewId, 10000, 100);
            
            // Send initial grid data in chunks
            gridManager.sendInitialLoad(session, gridId);
            
        } catch (Exception e) {
            log.error("Error in WebSocket connection establishment", e);
            try {
                session.close();
            } catch (Exception closeEx) {
                log.error("Error closing WebSocket session", closeEx);
            }
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.debug("Received WebSocket message: {}", payload);
            
            // Handle client messages (subscriptions, etc.)
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            
            switch (wsMessage.getType()) {
                case "PING":
                    // Respond to ping
                    session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
                    break;
                case "SUBSCRIBE":
                    // Handle additional subscriptions
                    handleSubscription(session, wsMessage);
                    break;
                default:
                    log.warn("Unknown WebSocket message type: {}", wsMessage.getType());
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            // Clean up session
            sessionManager.removeSession(session);
            log.info("WebSocket disconnected: {}", session.getId());
        } catch (Exception e) {
            log.error("Error cleaning up WebSocket session", e);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: {}", session.getId(), exception);
        try {
            session.close();
        } catch (Exception e) {
            log.error("Error closing WebSocket session after transport error", e);
        }
    }
    
    private void handleSubscription(WebSocketSession session, WebSocketMessage message) {
        // Handle additional grid subscriptions
        // For POC, this is minimal
        log.debug("Subscription request: {}", message);
    }
}
```

### Phase 3: Frontend Implementation

#### Step 3.1: React Project Setup
**File: `frontend/package.json`**
```json
{
  "name": "view-server-frontend",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "react-window": "^1.8.8",
    "react-window-infinite-loader": "^1.0.9"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.0.3",
    "vite": "^4.4.5"
  }
}
```

#### Step 3.2: WebSocket Service
**File: `frontend/src/services/WebSocketService.js`**
```javascript
class WebSocketService {
  constructor() {
    this.socket = null;
    this.listeners = new Map();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 1000;
  }

  connect(userId, viewId) {
    const url = `ws://localhost:8080/ws/grid/${userId}/${viewId}`;
    
    this.socket = new WebSocket(url);
    
    this.socket.onopen = () => {
      console.log('WebSocket connected');
      this.reconnectAttempts = 0;
      this.notifyListeners('connected', null);
    };
    
    this.socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      this.notifyListeners(message.type, message);
    };
    
    this.socket.onclose = () => {
      console.log('WebSocket disconnected');
      this.attemptReconnect(userId, viewId);
    };
    
    this.socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.notifyListeners('error', error);
    };
  }

  addListener(type, callback) {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, []);
    }
    this.listeners.get(type).push(callback);
  }

  removeListener(type, callback) {
    const callbacks = this.listeners.get(type);
    if (callbacks) {
      const index = callbacks.indexOf(callback);
      if (index > -1) {
        callbacks.splice(index, 1);
      }
    }
  }

  send(message) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    }
  }

  notifyListeners(type, data) {
    const callbacks = this.listeners.get(type);
    if (callbacks) {
      callbacks.forEach(callback => callback(data));
    }
  }

  attemptReconnect(userId, viewId) {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      setTimeout(() => {
        console.log(`Reconnecting... Attempt ${this.reconnectAttempts}`);
        this.connect(userId, viewId);
      }, this.reconnectDelay * this.reconnectAttempts);
    }
  }
}

export default new WebSocketService();
```

#### Step 3.3: Virtualized Grid Component
**File: `frontend/src/components/VirtualizedGrid.jsx`**
```javascript
import React, { useMemo } from 'react';
import { FixedSizeGrid as Grid } from 'react-window';

const VirtualizedGrid = ({ gridData, rows, columns, onCellClick }) => {
  const Cell = ({ columnIndex, rowIndex, style }) => {
    const cellKey = `${rowIndex}:${columnIndex}`;
    const cell = gridData[cellKey];
    
    return (
      <div
        style={{
          ...style,
          border: '1px solid #ddd',
          padding: '4px',
          overflow: 'hidden',
          backgroundColor: cell?.updated ? '#ffeb3b' : 'white'
        }}
        onClick={() => onCellClick && onCellClick(rowIndex, columnIndex)}
      >
        {cell ? cell.value : ''}
      </div>
    );
  };

  return (
    <Grid
      columnCount={columns}
      columnWidth={120}
      height={600}
      rowCount={rows}
      rowHeight={35}
      width={800}
    >
      {Cell}
    </Grid>
  );
};

export default VirtualizedGrid;
```

#### Step 3.4: Main Grid View Component
**File: `frontend/src/components/GridView.jsx`**
```javascript
import React, { useState, useEffect, useCallback } from 'react';
import VirtualizedGrid from './VirtualizedGrid';
import WebSocketService from '../services/WebSocketService';

const GridView = ({ userId, viewId }) => {
  const [gridData, setGridData] = useState({});
  const [dimensions, setDimensions] = useState({ rows: 0, columns: 0 });
  const [connected, setConnected] = useState(false);
  const [metrics, setMetrics] = useState({ updates: 0, latency: 0 });

  useEffect(() => {
    // Setup WebSocket listeners
    const handleInitialLoad = (data) => {
      setDimensions({ rows: data.rows, columns: data.columns });
      const cellMap = {};
      data.data.forEach(cell => {
        cellMap[`${cell.row}:${cell.col}`] = cell;
      });
      setGridData(cellMap);
    };

    const handleCellUpdate = (data) => {
      setGridData(prev => {
        const updated = { ...prev };
        data.updates.forEach(update => {
          const key = `${update.row}:${update.col}`;
          updated[key] = { ...update, updated: true };
          
          // Remove highlight after 2 seconds
          setTimeout(() => {
            setGridData(current => ({
              ...current,
              [key]: { ...current[key], updated: false }
            }));
          }, 2000);
        });
        return updated;
      });
      
      setMetrics(prev => ({ ...prev, updates: prev.updates + data.updates.length }));
    };

    const handleConnected = () => setConnected(true);
    const handleError = () => setConnected(false);

    WebSocketService.addListener('INITIAL_LOAD', handleInitialLoad);
    WebSocketService.addListener('CELL_UPDATE', handleCellUpdate);
    WebSocketService.addListener('connected', handleConnected);
    WebSocketService.addListener('error', handleError);

    // Connect to WebSocket
    WebSocketService.connect(userId, viewId);

    return () => {
      WebSocketService.removeListener('INITIAL_LOAD', handleInitialLoad);
      WebSocketService.removeListener('CELL_UPDATE', handleCellUpdate);
      WebSocketService.removeListener('connected', handleConnected);
      WebSocketService.removeListener('error', handleError);
    };
  }, [userId, viewId]);

  return (
    <div>
      <div style={{ marginBottom: '20px' }}>
        <h2>Grid View - User: {userId}, View: {viewId}</h2>
        <div>
          Status: {connected ? '🟢 Connected' : '🔴 Disconnected'} | 
          Updates: {metrics.updates} | 
          Dimensions: {dimensions.rows} × {dimensions.columns}
        </div>
      </div>
      
      <VirtualizedGrid
        gridData={gridData}
        rows={dimensions.rows}
        columns={dimensions.columns}
      />
    </div>
  );
};

export default GridView;
```

### Phase 4: Mock Data Generator

#### Step 4.1: Event Generator Service (Updated for Small Messages)
**File: `mock-data-generator/src/main/java/com/mockdata/service/EventGenerator.java`**
```java
@Service
public class EventGenerator {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    
    // Small message configuration
    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 50;        // Keep batches small
    private static final int MAX_CELLS_PER_MESSAGE = 500; // Hard limit
    private static final int TARGET_MESSAGE_SIZE = 20_000; // 20KB target
    
    @Scheduled(fixedRate = 100) // Generate events every 100ms
    public void generateGridEvents() {
        try {
            // Generate multiple small batches
            int batchCount = 1 + random.nextInt(3); // 1-3 batches per cycle
            
            for (int i = 0; i < batchCount; i++) {
                GridEvent event = createSmallCellUpdateEvent();
                String eventJson = objectMapper.writeValueAsString(event);
                
                // Validate message size before sending
                if (eventJson.getBytes().length < TARGET_MESSAGE_SIZE) {
                    kafkaTemplate.send("grid-events", event.getGridId(), eventJson);
                } else {
                    log.warn("Generated message too large: {} bytes, splitting...", 
                            eventJson.getBytes().length);
                    splitAndSendEvent(event);
                }
            }
        } catch (Exception e) {
            log.error("Error generating events", e);
        }
    }
    
    private GridEvent createSmallCellUpdateEvent() {
        // Generate small batch of updates
        int updateCount = MIN_BATCH_SIZE + random.nextInt(MAX_BATCH_SIZE - MIN_BATCH_SIZE);
        String gridId = "user" + (1 + random.nextInt(10)) + "_view" + (1 + random.nextInt(5));
        
        List<CellChange> changes = new ArrayList<>();
        for (int i = 0; i < updateCount; i++) {
            CellChange change = new CellChange(
                random.nextInt(10000), // row
                random.nextInt(100),   // column
                null, // oldValue - not needed for POC
                generateRandomValue(),
                detectDataType(generateRandomValue())
            );
            changes.add(change);
        }
        
        return new GridEvent("CELL_UPDATE", gridId, System.currentTimeMillis(), 
                           "batch_" + System.nanoTime(), changes);
    }
    
    private void splitAndSendEvent(GridEvent largeEvent) {
        // Split large event into smaller chunks
        List<CellChange> allChanges = largeEvent.getChanges();
        
        for (int i = 0; i < allChanges.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, allChanges.size());
            List<CellChange> chunk = allChanges.subList(i, end);
            
            GridEvent smallEvent = new GridEvent(
                largeEvent.getEventType(),
                largeEvent.getGridId(),
                System.currentTimeMillis(),
                largeEvent.getBatchId() + "_" + (i / MAX_BATCH_SIZE),
                chunk
            );
            
            try {
                String eventJson = objectMapper.writeValueAsString(smallEvent);
                kafkaTemplate.send("grid-events", smallEvent.getGridId(), eventJson);
            } catch (Exception e) {
                log.error("Error sending split event", e);
            }
        }
    }
    
    private Object generateRandomValue() {
        int type = random.nextInt(4);
        switch (type) {
            case 0: return Math.round(random.nextDouble() * 1000 * 100.0) / 100.0; // 2 decimal places
            case 1: return "Data_" + random.nextInt(10000);
            case 2: return random.nextInt(1000);
            case 3: return System.currentTimeMillis(); // timestamp
            default: return "Unknown";
        }
    }
    
    private String detectDataType(Object value) {
        if (value instanceof String) return "string";
        if (value instanceof Integer) return "integer";
        if (value instanceof Double) return "number";
        if (value instanceof Long) return "timestamp";
        return "unknown";
    }
    
    // Load simulation for performance testing
    @Scheduled(fixedRate = 1000) // Every second
    public void generateLoadTestEvents() {
        if (isLoadTestMode()) {
            // Generate higher frequency updates for specific grids
            for (int i = 1; i <= 5; i++) { // First 5 users
                for (int j = 1; j <= 3; j++) { // First 3 views per user
                    generateHighFrequencyUpdates("user" + i + "_view" + j);
                }
            }
        }
    }
    
    private void generateHighFrequencyUpdates(String gridId) {
        try {
            // Generate 10-20 small updates for load testing
            int updateCount = 10 + random.nextInt(11);
            List<CellChange> changes = new ArrayList<>();
            
            for (int i = 0; i < updateCount; i++) {
                changes.add(new CellChange(
                    random.nextInt(1000),  // Focus on first 1000 rows for visible updates
                    random.nextInt(20),    // Focus on first 20 columns
                    null,
                    random.nextDouble() * 1000,
                    "number"
                ));
            }
            
            GridEvent event = new GridEvent("CELL_UPDATE", gridId, 
                                          System.currentTimeMillis(), 
                                          "load_test_" + System.nanoTime(), 
                                          changes);
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("grid-events", gridId, eventJson);
            
        } catch (Exception e) {
            log.error("Error generating load test events for grid: {}", gridId, e);
        }
    }
    
    private boolean isLoadTestMode() {
        // Simple toggle based on system property
        return Boolean.parseBoolean(System.getProperty("load.test.mode", "false"));
    }
}
```

#### Step 4.2: Message Size Monitor
**File: `mock-data-generator/src/main/java/com/mockdata/service/MessageSizeMonitor.java`**
```java
@Service
public class MessageSizeMonitor {
    private static final Logger log = LoggerFactory.getLogger(MessageSizeMonitor.class);
    
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicInteger oversizedMessages = new AtomicInteger(0);
    
    private static final int MAX_ALLOWED_SIZE = 100_000; // 100KB
    private static final int WARNING_SIZE = 50_000; // 50KB
    
    public void recordMessage(String eventJson) {
        int messageSize = eventJson.getBytes().length;
        
        totalMessages.incrementAndGet();
        totalBytes.addAndGet(messageSize);
        
        if (messageSize > MAX_ALLOWED_SIZE) {
            oversizedMessages.incrementAndGet();
            log.error("OVERSIZED MESSAGE: {} bytes (limit: {})", messageSize, MAX_ALLOWED_SIZE);
        } else if (messageSize > WARNING_SIZE) {
            log.warn("Large message: {} bytes", messageSize);
        }
    }
    
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void reportStats() {
        long messages = totalMessages.get();
        long bytes = totalBytes.get();
        int oversized = oversizedMessages.get();
        
        if (messages > 0) {
            double avgSize = (double) bytes / messages;
            log.info("Message Stats - Total: {}, Avg Size: {:.1f} bytes, Oversized: {}", 
                    messages, avgSize, oversized);
            
            // Reset counters
            totalMessages.set(0);
            totalBytes.set(0);
            oversizedMessages.set(0);
        }
    }
}
```

#### Step 4.3: Load Simulation Controller
**File: `mock-data-generator/src/main/java/com/mockdata/controller/LoadSimulationController.java`**
```java
@RestController
@RequestMapping("/api/simulation")
public class LoadSimulationController {
    
    private final EventGenerator eventGenerator;
    private final MessageSizeMonitor messageSizeMonitor;
    
    @PostMapping("/start-load-test")
    public ResponseEntity<String> startLoadTest() {
        System.setProperty("load.test.mode", "true");
        return ResponseEntity.ok("Load test mode started");
    }
    
    @PostMapping("/stop-load-test")
    public ResponseEntity<String> stopLoadTest() {
        System.setProperty("load.test.mode", "false");
        return ResponseEntity.ok("Load test mode stopped");
    }
    
    @GetMapping("/stats")
    public ResponseEntity<MessageStats> getStats() {
        return ResponseEntity.ok(new MessageStats(
            // Return current statistics
        ));
    }
    
    @PostMapping("/generate-burst")
    public ResponseEntity<String> generateBurst(@RequestParam int messageCount) {
        // Generate a burst of small messages for testing
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < messageCount; i++) {
                try {
                    eventGenerator.generateSmallEvent();
                    Thread.sleep(10); // Small delay between messages
                } catch (Exception e) {
                    log.error("Error in burst generation", e);
                }
            }
        });
        
        return ResponseEntity.ok("Generating " + messageCount + " burst messages");
    }
}
```

### Phase 5: Deployment and Testing

#### Step 5.1: Docker Build Scripts
**File: `view-server/Dockerfile`**
```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app
COPY target/view-server-1.0.0.jar app.jar

EXPOSE 8080

ENV JVM_OPTS="-Xmx16g -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JVM_OPTS -jar app.jar"]
```

**File: `frontend/Dockerfile`**
```dockerfile
FROM node:18-alpine

WORKDIR /app
COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

EXPOSE 3000
CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0"]
```

#### Step 5.2: Setup Script
**File: `setup.sh`**
```bash
#!/bin/bash

echo "Building View Server POC..."

# Build Java applications
cd view-server
mvn clean package -DskipTests
cd ..

cd mock-data-generator  
mvn clean package -DskipTests
cd ..

# Build frontend
cd frontend
npm install
npm run build
cd ..

# Start all services
docker-compose up --build

echo "Setup complete! Access the application at http://localhost:3000"
```

### Phase 6: Testing and Metrics

#### Step 6.1: Performance Testing Script
**File: `test-performance.sh`**
```bash
#!/bin/bash

echo "Starting performance test..."

# Open multiple browser tabs programmatically
for i in {1..10}; do
  open "http://localhost:3000?user=user$i&view=view1" &
done

echo "Opened 10 browser tabs for testing"
echo "Monitor metrics at http://localhost:8080/actuator/metrics"
```

#### Step 6.2: Metrics Collection
- **Memory usage**: JVM heap monitoring
- **WebSocket connections**: Active connection count
- **Message throughput**: Messages/second via Kafka and WebSocket
- **Latency**: End-to-end update latency measurement

### Implementation Timeline

1. **Day 1**: Infrastructure setup, Spring Boot skeleton
2. **Day 2**: Core Grid and WebSocket implementation  
3. **Day 3**: Kafka integration and event processing
4. **Day 4**: React frontend and WebSocket client
5. **Day 5**: Mock data generator and testing
6. **Day 6**: Performance testing and optimization
7. **Day 7**: Documentation and final validation

This implementation plan provides a complete roadmap for building the view server POC with all necessary components for performance testing. 