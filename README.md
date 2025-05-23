# View Server Performance POC

A performance proof-of-concept for a horizontally scalable view server that handles real-time grid updates via WebSocket connections, with Kafka event streaming and React frontend.

## Project Overview

This POC demonstrates a scalable architecture for real-time data grid updates using:
- **Spring Boot** view server with WebSocket connections
- **Apache Kafka** for event streaming (small message strategy)
- **React** frontend with virtualized grid rendering
- **Mock data generator** for realistic load testing

## Architecture

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

## Key Features

- **Small Message Strategy**: All Kafka messages < 100KB (target 10-50KB)
- **Chunked Initial Load**: Large datasets loaded via WebSocket chunks
- **Real-time Updates**: <50ms latency from Kafka to frontend
- **Virtualized Rendering**: Handles 1M+ cell grids efficiently
- **Horizontal Scaling**: Stateless architecture with shared state

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17 (for local development)
- Node.js 18+ (for frontend development)
- 16GB+ RAM recommended for full load testing

### 1. Start Infrastructure

```bash
# Start Kafka, Zookeeper, and monitoring
docker-compose up -d zookeeper kafka kafka-ui prometheus grafana
```

### 2. Build and Start Applications

```bash
# Build all components
./build.sh

# Start all services
docker-compose up --build
```

### 3. Access Applications

- **Frontend**: http://localhost:3000
- **View Server API**: http://localhost:8080
- **Kafka UI**: http://localhost:8081
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/admin)

## Performance Testing

### Load Testing Endpoints

```bash
# Start load test mode
curl -X POST http://localhost:8080/api/simulation/start-load-test

# Generate burst of messages
curl -X POST "http://localhost:8080/api/simulation/generate-burst?messageCount=1000&delayMs=10"

# Stop load test
curl -X POST http://localhost:8080/api/simulation/stop-load-test
```

### Monitoring

- **Grafana Dashboards**: View real-time metrics at http://localhost:3001
- **Prometheus Metrics**: Raw metrics at http://localhost:9090
- **Application Logs**: Available in `./logs/` directory

## Message Size Strategy

### Kafka Messages
- **Target Size**: 10-50KB per message
- **Hard Limit**: 100KB per message
- **Batch Size**: 1-500 cell updates per message
- **Validation**: Built-in size checking

### Initial Load
- **Method**: Chunked WebSocket transfer (bypasses Kafka)
- **Chunk Size**: 1000 cells per chunk (~50KB)
- **Progress**: Real-time loading indicators

## Grid Specifications

- **Size**: 100 columns × 10,000 rows (1M cells)
- **Data Types**: Mixed (strings, numbers, timestamps)
- **Memory**: ~50MB per grid, 5.1GB for 100 concurrent grids
- **Updates**: Delta-only, no full grid transfers

## Development

### Local Development Setup

```bash
# Backend (requires Java 17)
cd view-server
mvn spring-boot:run

# Frontend (requires Node.js 18+)
cd frontend
npm install
npm run dev

# Mock Data Generator
cd mock-data-generator
mvn spring-boot:run
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka connection |
| `LOAD_TEST_ENABLED` | `false` | Enable load testing |
| `USERS_COUNT` | `10` | Number of simulated users |
| `VIEWS_PER_USER` | `5` | Views per user |
| `UPDATE_FREQUENCY` | `100` | Update frequency (ms) |

## Component Structure

```
├── view-server/          # Spring Boot backend
├── frontend/             # React frontend  
├── mock-data-generator/  # Kafka event producer
├── monitoring/           # Prometheus & Grafana config
├── logs/                 # Application logs
└── docker-compose.yml    # Infrastructure setup
```

## Performance Targets

- **Latency**: <50ms (Kafka → WebSocket)
- **Throughput**: 10,000+ messages/second
- **Connections**: 1,000+ concurrent WebSocket connections
- **Memory**: <16GB for POC dataset
- **Message Size**: 95% under 20KB

## Troubleshooting

### Common Issues

1. **Kafka Connection Failed**
   ```bash
   # Check Kafka health
   docker-compose logs kafka
   ```

2. **High Memory Usage**
   ```bash
   # Monitor JVM memory
   curl http://localhost:8080/actuator/metrics/jvm.memory.used
   ```

3. **WebSocket Disconnections**
   ```bash
   # Check view server logs
   docker-compose logs view-server
   ```

### Health Checks

```bash
# All services health
curl http://localhost:8080/actuator/health

# Kafka topics
curl http://localhost:8081/api/clusters/viewserver-cluster/topics
```

## License

This is a performance POC for evaluation purposes. 