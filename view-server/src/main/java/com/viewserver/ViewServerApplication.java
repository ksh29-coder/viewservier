package com.viewserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * View Server Performance POC
 * 
 * Main application class for the horizontally scalable view server
 * that handles real-time grid updates via WebSocket connections.
 * 
 * Features:
 * - WebSocket connections for real-time updates
 * - Kafka event streaming integration
 * - Small message strategy (<100KB)
 * - In-memory grid state management
 * - Performance metrics and monitoring
 */
@SpringBootApplication
@EnableScheduling
public class ViewServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViewServerApplication.class, args);
    }
} 