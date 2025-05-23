package com.viewserver.dto;

/**
 * Base WebSocket message for client communication
 * 
 * According to spec.md WebSocket message formats
 */
public class WebSocketMessage {
    private String type;
    private String gridId;
    private Object data;

    public WebSocketMessage() {
        // Default constructor for Jackson
    }

    public WebSocketMessage(String type, String gridId, Object data) {
        this.type = type;
        this.gridId = gridId;
        this.data = data;
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGridId() {
        return gridId;
    }

    public void setGridId(String gridId) {
        this.gridId = gridId;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "type='" + type + '\'' +
                ", gridId='" + gridId + '\'' +
                ", data=" + data +
                '}';
    }
} 