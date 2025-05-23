package com.viewserver.dto;

/**
 * DTO for individual cell updates sent via WebSocket
 * 
 * According to spec.md WebSocket message format:
 * Real-time Delta Update (1-50 cells, ~5KB)
 */
public class CellUpdate {
    private int row;
    private int column;
    private Object value;
    private String dataType;

    public CellUpdate() {
        // Default constructor for Jackson
    }

    public CellUpdate(int row, int column, Object value, String dataType) {
        this.row = row;
        this.column = column;
        this.value = value;
        this.dataType = dataType;
    }

    // Getters and setters
    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    @Override
    public String toString() {
        return "CellUpdate{" +
                "row=" + row +
                ", column=" + column +
                ", value=" + value +
                ", dataType='" + dataType + '\'' +
                '}';
    }
} 