package com.viewserver.dto;

/**
 * DTO for cell changes received from Kafka
 * 
 * According to spec.md Kafka message format:
 * Small Cell Update (typical: 5-50 cells, ~2-20KB)
 * Max 500 cells per message, <100KB limit
 */
public class CellChange {
    private int row;
    private int column;
    private Object oldValue;
    private Object newValue;
    private String dataType;

    public CellChange() {
        // Default constructor for Jackson
    }

    public CellChange(int row, int column, Object oldValue, Object newValue, String dataType) {
        this.row = row;
        this.column = column;
        this.oldValue = oldValue;
        this.newValue = newValue;
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

    public Object getOldValue() {
        return oldValue;
    }

    public void setOldValue(Object oldValue) {
        this.oldValue = oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    public void setNewValue(Object newValue) {
        this.newValue = newValue;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    @Override
    public String toString() {
        return "CellChange{" +
                "row=" + row +
                ", column=" + column +
                ", oldValue=" + oldValue +
                ", newValue=" + newValue +
                ", dataType='" + dataType + '\'' +
                '}';
    }
} 