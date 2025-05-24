# View Server Performance POC - Architecture

## System Overview
A horizontally scalable view server designed for real-time grid updates via WebSocket connections, with Kafka event streaming and a React frontend. The system is optimized for low latency (<50ms end-to-end) and high throughput (10,000+ messages/second).

## Architecture Diagram

```
┌─────────────────┐    WebSocket (ws://localhost:8080)    ┌─────────────────┐
│   Frontend      │◄──────────────────────────────────────┤   View Server   │
│   (React/Vite)  │                                       │  (Spring Boot)  │
│   Port: 5173    │                                       │   Port: 8080    │
└─────────────────┘                                       └─────────────────┘
         │                                                         │
         │ HTTP REST API                                           │
         │ (Debug endpoints)                                       │
         └─────────────────────────────────────────────────────────┘
                                                                   │
                                                                   │ Kafka Consumer
                                                                   │
┌─────────────────┐    Kafka Topics                      ┌─────────────────┐
│ Mock Data Gen   │────────────────────────────────────▶ │     Kafka       │
│ (Not Impl.)     │    grid-updates                      │   Port: 9092    │
│                 │                                      │                 │
└─────────────────┘                                      └─────────────────┘
                                                                   │
                                                          ┌─────────────────┐
                                                          │   Zookeeper     │
                                                          │   Port: 2181    │
                                                          └─────────────────┘
```

## Component Architecture

### 1. Frontend (React + Vite)
- **Location**: `frontend/`
- **Port**: 5173
- **Technology**: React 18, Vite, WebSocket client
- **Key Components**:
  - `App.jsx` - Main application
  - `SimpleGridDebug.jsx` - Grid visualization component
  - `GridView.jsx` - Original grid component
- **Responsibilities**:
  - WebSocket connection management
  - Real-time grid rendering
  - User interaction handling
  - Debug visualization

### 2. View Server (Spring Boot)
- **Location**: `view-server/`
- **Port**: 8080
- **Technology**: Spring Boot 3, WebSocket, Kafka Client
- **Key Components**:
  - `GridWebSocketHandler` - WebSocket connection management
  - `GridService` - Grid data management
  - `KafkaConsumer` - Real-time update processing
  - `DebugController` - Debug endpoints
- **Responsibilities**:
  - WebSocket session management
  - Initial grid data loading (chunked)
  - Real-time update distribution
  - Kafka message consumption

### 3. Message Infrastructure
- **Kafka**: Port 9092
- **Zookeeper**: Port 2181
- **Topics**: `grid-updates`
- **Message Format**: JSON with grid cell updates
- **Responsibilities**:
  - Event streaming
  - Message durability
  - Horizontal scaling support

### 4. Mock Data Generator (Planned)
- **Location**: `mock-data-generator/`
- **Status**: Directory exists, implementation pending
- **Planned Technology**: Java/Python Kafka Producer
- **Responsibilities**:
  - Generate realistic grid updates
  - Simulate high-throughput scenarios
  - Performance testing data

## Data Flow Diagrams

### Initial Grid Loading Flow
```
Frontend                View Server              Grid Service
   │                        │                        │
   │─── WebSocket Connect ──▶│                        │
   │◄── Connection OK ──────│                        │
   │                        │──── Create Grid ─────▶│
   │                        │◄── Grid Created ──────│
   │◄── Chunk 1 (5 cells) ──│                        │
   │◄── Chunk 2 (5 cells) ──│                        │
   │◄── Chunk 3 (5 cells) ──│                        │
   │◄── Chunk 4 (5 cells) ──│                        │
   │◄── Chunk 5 (5 cells) ──│                        │
   │◄── Load Complete ──────│                        │
```

### Real-time Update Flow (When Implemented)
```
Mock Generator          Kafka              View Server          Frontend
      │                   │                     │                  │
      │─── Publish ──────▶│                     │                  │
      │    Update         │──── Consume ──────▶│                  │
      │                   │                     │─── WebSocket ──▶│
      │                   │                     │    Update        │
```

## Configuration Details

### Current Grid Configuration
- **Grid Size**: 5×5 (25 total cells)
- **Chunk Size**: 5 cells per chunk
- **Total Chunks**: 5 chunks exactly
- **Load Pattern**: Sequential chunked loading

### WebSocket Configuration
- **Endpoint**: `/ws/grid/{userId}/{viewId}`
- **Current Test**: `user1/view1`
- **Message Format**: JSON
- **Connection Type**: Raw WebSocket (not SockJS)

### Kafka Configuration
- **Bootstrap Servers**: `localhost:9092`
- **Topics**: `grid-updates`
- **Consumer Group**: `view-server-group`
- **Auto Offset Reset**: `earliest`

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.x
- **WebSocket**: Spring WebSocket
- **Kafka**: Spring Kafka
- **Build Tool**: Maven
- **Java Version**: 17+

### Frontend
- **Framework**: React 18
- **Build Tool**: Vite
- **WebSocket**: Native WebSocket API
- **Styling**: CSS3

### Infrastructure
- **Message Broker**: Apache Kafka
- **Coordination**: Apache Zookeeper
- **Containerization**: Docker & Docker Compose
- **Monitoring**: Planned (mentioned in docker-compose)

## Port Mapping

| Service | Port | Protocol | Status |
|---------|------|----------|--------|
| Frontend | 5173 | HTTP | ✅ Running |
| View Server | 8080 | HTTP/WebSocket | ✅ Running |
| Kafka | 9092 | TCP | ✅ Running |
| Zookeeper | 2181 | TCP | ✅ Running |

## API Endpoints

### WebSocket
- `ws://localhost:8080/ws/grid/{userId}/{viewId}` - Grid updates

### REST API (Debug)
- `GET /debug/ping` - Health check with grid info
- `GET /debug/sessions` - Active WebSocket sessions
- `GET /debug/grids` - Active grids information

## Performance Characteristics

### Current Implementation
- **Grid Size**: 5×5 (optimized for testing)
- **Message Size**: Small JSON objects (~100-500 bytes)
- **Latency Target**: <50ms end-to-end
- **Chunk Loading**: Immediate sequential delivery
- **Memory Usage**: Minimal with current grid size

### Scalability Design
- **Horizontal Scaling**: Kafka partitioning support
- **Connection Management**: Spring WebSocket session handling
- **Data Structures**: ConcurrentHashMap for thread safety
- **Resource Management**: Connection pooling ready

## Current Implementation Status

### ✅ Completed
- Spring Boot backend with WebSocket support
- React frontend with WebSocket client
- Kafka infrastructure setup
- Initial grid loading with chunked data
- Debug visualization component
- Docker compose orchestration

### 🔄 In Progress
- Mock data generator implementation
- Real-time update flow testing

### 📋 Planned
- Performance optimization
- Load testing framework
- Monitoring and metrics
- Production configuration

## Development Setup

### Prerequisites
- Docker Desktop
- Java 17+
- Node.js 18+
- Maven

### Quick Start
```bash
# Start infrastructure
docker-compose up -d zookeeper kafka

# Start backend
cd view-server
mvn spring-boot:run

# Start frontend (new terminal)
cd frontend
npm run dev
```

### Access Points
- Frontend: http://localhost:5173
- Backend Health: http://localhost:8080/debug/ping
- WebSocket Debug: Browser developer tools

## Future Enhancements

### Phase 1: Core Functionality
- Implement mock data generator
- Add real-time update processing
- Performance baseline establishment

### Phase 2: Optimization
- Message batching optimization
- Connection pooling improvements
- Memory usage optimization
- Latency measurements

### Phase 3: Production Ready
- Security implementation
- Monitoring and alerting
- Load balancing support
- Production configuration 