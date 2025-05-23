#!/bin/bash

set -e

echo "🚀 Building View Server Performance POC..."

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

# Check prerequisites
print_status "Checking prerequisites..."

if ! command -v docker &> /dev/null; then
    print_error "Docker is required but not installed"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    print_error "Docker Compose is required but not installed"
    exit 1
fi

print_success "Prerequisites check passed"

# Create log directories
print_status "Creating log directories..."
mkdir -p logs/view-server logs/frontend logs/mock-data-generator
print_success "Log directories created"

# Build Java applications (if Maven is available locally)
if command -v mvn &> /dev/null; then
    print_status "Building Java applications with Maven..."
    
    if [ -d "view-server" ] && [ -f "view-server/pom.xml" ]; then
        print_status "Building view-server..."
        cd view-server
        mvn clean package -DskipTests -q
        cd ..
        print_success "View server built successfully"
    else
        print_warning "View server Maven project not found, will build in Docker"
    fi
    
    if [ -d "mock-data-generator" ] && [ -f "mock-data-generator/pom.xml" ]; then
        print_status "Building mock-data-generator..."
        cd mock-data-generator
        mvn clean package -DskipTests -q
        cd ..
        print_success "Mock data generator built successfully"
    else
        print_warning "Mock data generator Maven project not found, will build in Docker"
    fi
else
    print_warning "Maven not found locally, will build in Docker containers"
fi

# Build frontend (if Node.js is available locally)
if command -v npm &> /dev/null; then
    if [ -d "frontend" ] && [ -f "frontend/package.json" ]; then
        print_status "Building frontend..."
        cd frontend
        npm install --silent
        npm run build --silent
        cd ..
        print_success "Frontend built successfully"
    else
        print_warning "Frontend npm project not found, will build in Docker"
    fi
else
    print_warning "Node.js/npm not found locally, will build in Docker containers"
fi

# Validate Docker Compose file
print_status "Validating Docker Compose configuration..."
if docker-compose config > /dev/null 2>&1; then
    print_success "Docker Compose configuration is valid"
else
    print_error "Docker Compose configuration is invalid"
    exit 1
fi

print_success "Build completed! 🎉"
echo
print_status "Next steps:"
echo "  1. Start infrastructure: docker-compose up -d zookeeper kafka kafka-ui"
echo "  2. Start applications: docker-compose up --build"
echo "  3. Access frontend: http://localhost:3000"
echo "  4. View metrics: http://localhost:3001 (Grafana)"
echo
print_status "For load testing:"
echo "  curl -X POST http://localhost:8080/api/simulation/start-load-test" 