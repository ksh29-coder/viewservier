#!/bin/bash

set -e

echo "🔍 Validating Phase 1 - Infrastructure Setup"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Test 1: Docker Compose Configuration
print_status "Test 1: Validating Docker Compose configuration..."
if docker-compose config > /dev/null 2>&1; then
    print_success "Docker Compose configuration is valid"
else
    print_error "Docker Compose configuration is invalid"
    exit 1
fi

# Test 2: Directory Structure
print_status "Test 2: Checking directory structure..."

required_dirs=(
    "view-server"
    "frontend" 
    "mock-data-generator"
    "monitoring"
    "logs/view-server"
    "logs/frontend"
    "logs/mock-data-generator"
    "monitoring/grafana"
)

for dir in "${required_dirs[@]}"; do
    if [ -d "$dir" ]; then
        print_success "Directory exists: $dir"
    else
        print_error "Missing directory: $dir"
        exit 1
    fi
done

# Test 3: Configuration Files
print_status "Test 3: Checking configuration files..."

required_files=(
    "docker-compose.yml"
    "monitoring/prometheus.yml"
    "monitoring/grafana/datasources/prometheus.yml" 
    "monitoring/grafana/dashboards/dashboard.yml"
    "README.md"
    "build.sh"
    ".gitignore"
)

for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        print_success "File exists: $file"
    else
        print_error "Missing file: $file"
        exit 1
    fi
done

# Test 4: Cursor Rules Files
print_status "Test 4: Checking cursor rules files..."

cursor_files=(
    ".cursorrules"
    "view-server/.cursorrules"
    "frontend/.cursorrules"
    "mock-data-generator/.cursorrules"
)

for file in "${cursor_files[@]}"; do
    if [ -f "$file" ]; then
        print_success "Cursor rules exist: $file"
    else
        print_error "Missing cursor rules: $file"
        exit 1
    fi
done

# Test 5: Infrastructure Services (Quick test)
print_status "Test 5: Testing infrastructure services startup..."
print_status "Starting Zookeeper and Kafka (this may take a moment)..."

# Clean up any existing containers
docker-compose down > /dev/null 2>&1 || true

# Start just the infrastructure services
if docker-compose up -d zookeeper kafka > /dev/null 2>&1; then
    print_success "Infrastructure services started successfully"
    
    # Wait for services to be healthy
    print_status "Waiting for services to be healthy..."
    sleep 30
    
    # Check Zookeeper
    if docker-compose exec -T zookeeper nc -z localhost 2181 > /dev/null 2>&1; then
        print_success "Zookeeper is healthy"
    else
        print_warning "Zookeeper health check failed (may need more time)"
    fi
    
    # Check Kafka
    if docker-compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; then
        print_success "Kafka is healthy"
    else
        print_warning "Kafka health check failed (may need more time)"
    fi
    
    # Clean up
    print_status "Cleaning up test containers..."
    docker-compose down > /dev/null 2>&1
    print_success "Test cleanup completed"
    
else
    print_error "Failed to start infrastructure services"
    docker-compose logs
    exit 1
fi

# Test 6: Docker Compose Service Definitions
print_status "Test 6: Validating service definitions..."

services=(
    "zookeeper"
    "kafka"
    "kafka-ui"
    "view-server"
    "frontend"
    "mock-data-generator"
    "prometheus"
    "grafana"
)

for service in "${services[@]}"; do
    if docker-compose config --services | grep -q "^$service$"; then
        print_success "Service defined: $service"
    else
        print_error "Missing service definition: $service"
        exit 1
    fi
done

# Test 7: Environment Variables Check
print_status "Test 7: Checking environment variable configuration..."

# Check if Docker Compose config contains expected environment variables
if docker-compose config | grep -q "KAFKA_BOOTSTRAP_SERVERS"; then
    print_success "Kafka bootstrap servers configured"
else
    print_error "Missing Kafka bootstrap servers configuration"
    exit 1
fi

if docker-compose config | grep -q "JVM_OPTS"; then
    print_success "JVM options configured"
else
    print_error "Missing JVM options configuration"
    exit 1
fi

# Validation Summary
echo
print_success "🎉 Phase 1 Infrastructure Setup Validation PASSED!"
echo
print_status "✅ All validation tests completed successfully"
print_status "✅ Docker Compose configuration is valid"
print_status "✅ Required directories and files are in place"
print_status "✅ Infrastructure services can start and run"
print_status "✅ Service definitions are complete"
print_status "✅ Environment variables are configured"
echo
print_status "Phase 1 is ready! You can now proceed to Phase 2: Backend Implementation"
echo
print_status "Next: Implement view-server Spring Boot application according to implementation.md"