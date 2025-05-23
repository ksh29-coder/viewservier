package com.viewserver.model;

import java.util.Objects;

/**
 * Cell model representing individual grid cells.
 * 
 * According to spec.md:
 * - Supports mixed data types (strings, numbers, timestamps)
 * - Immutable cell data with timestamp
 * - Efficient memory layout for 1M+ cells
 */
public class Cell {
    private final int row;
    private final int column;
    private final Object value;
    private final String dataType;
    private final long timestamp;

    public Cell(int row, int column, Object value, String dataType, long timestamp) {
        this.row = row;
        this.column = column;
        this.value = value;
        this.dataType = dataType;
        this.timestamp = timestamp;
    }

    public Cell(int row, int column, Object value, String dataType) {
        this(row, column, value, dataType, System.currentTimeMillis());
    }

    // Getters
    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public Object getValue() {
        return value;
    }

    public String getDataType() {
        return dataType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Cell key for efficient HashMap lookups
    public String getCellKey() {
        return row + ":" + column;
    }

    // Create cell key statically
    public static String createKey(int row, int column) {
        return row + ":" + column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cell cell = (Cell) o;
        return row == cell.row && 
               column == cell.column && 
               Objects.equals(value, cell.value) && 
               Objects.equals(dataType, cell.dataType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, column, value, dataType);
    }

    @Override
    public String toString() {
        return "Cell{" +
                "row=" + row +
                ", column=" + column +
                ", value=" + value +
                ", dataType='" + dataType + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
} 