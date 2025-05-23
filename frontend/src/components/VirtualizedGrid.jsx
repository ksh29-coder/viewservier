import React, { useCallback, useMemo } from 'react';
import { FixedSizeGrid as Grid } from 'react-window';

/**
 * Virtualized Grid component for efficiently rendering large datasets
 * 
 * Uses react-window for virtualization to handle 100 × 10,000 cells (1M cells)
 * Optimized for performance with proper memoization
 * Follows the pattern specified in .cursorrules
 */
const VirtualizedGrid = ({
    gridData = {},
    rows = 10000,
    columns = 100,
    width = 800,
    height = 600,
    onCellClick = null
}) => {
    // Memoized cell component for optimal performance
    const Cell = React.memo(({ columnIndex, rowIndex, style }) => {
        const cellKey = `${rowIndex}:${columnIndex}`;
        const cell = gridData[cellKey];
        
        const cellValue = cell ? cell.value : '';
        const isUpdated = cell ? cell.updated : false;
        const hasError = cell ? cell.error : false;
        
        // Format cell value based on data type
        const formatValue = useCallback((value, dataType) => {
            if (value === null || value === undefined) return '';
            
            switch (dataType) {
                case 'number':
                    return typeof value === 'number' ? value.toLocaleString() : value;
                case 'timestamp':
                    return new Date(value).toLocaleTimeString();
                case 'boolean':
                    return value ? 'True' : 'False';
                default:
                    return String(value);
            }
        }, []);
        
        const displayValue = formatValue(cellValue, cell?.dataType);
        
        const handleClick = useCallback(() => {
            if (onCellClick) {
                onCellClick(rowIndex, columnIndex, cell);
            }
        }, [rowIndex, columnIndex, cell]);
        
        return (
            <div
                style={{
                    ...style,
                    display: 'flex',
                    alignItems: 'center',
                    paddingLeft: '8px',
                    paddingRight: '8px',
                    fontSize: '14px',
                    cursor: onCellClick ? 'pointer' : 'default',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                }}
                className={`
                    grid-cell 
                    ${isUpdated ? 'updated' : ''} 
                    ${hasError ? 'error' : ''}
                `}
                onClick={handleClick}
                title={`Row: ${rowIndex}, Col: ${columnIndex}, Value: ${displayValue}`}
            >
                {displayValue}
            </div>
        );
    });
    
    // Cell component display name for debugging
    Cell.displayName = 'GridCell';
    
    // Grid cell size constants
    const CELL_WIDTH = 120;
    const CELL_HEIGHT = 35;
    
    // Calculate grid dimensions
    const gridWidth = Math.min(width, columns * CELL_WIDTH);
    const gridHeight = Math.min(height, rows * CELL_HEIGHT);
    
    // Grid statistics for monitoring
    const statistics = useMemo(() => {
        const totalCells = Object.keys(gridData).length;
        const updatedCells = Object.values(gridData).filter(cell => cell.updated).length;
        const visibleCellsWidth = Math.ceil(gridWidth / CELL_WIDTH);
        const visibleCellsHeight = Math.ceil(gridHeight / CELL_HEIGHT);
        const visibleCells = visibleCellsWidth * visibleCellsHeight;
        
        return {
            totalCells,
            updatedCells,
            visibleCells,
            gridDimensions: `${rows} × ${columns}`,
            memoryEstimate: `${Math.round(totalCells * 100 / 1024)} KB`
        };
    }, [gridData, rows, columns, gridWidth, gridHeight]);
    
    return (
        <div className="virtualized-grid-container">
            {/* Grid Header with coordinates */}
            <div className="grid-header-row" style={{ 
                display: 'flex', 
                height: '30px',
                backgroundColor: '#f8fafc',
                borderBottom: '2px solid #e2e8f0',
                position: 'sticky',
                top: 0,
                zIndex: 10
            }}>
                <div style={{ 
                    width: '60px', 
                    display: 'flex', 
                    alignItems: 'center', 
                    justifyContent: 'center',
                    backgroundColor: '#f1f5f9',
                    borderRight: '1px solid #e2e8f0',
                    fontSize: '12px',
                    fontWeight: 'bold'
                }}>
                    
                </div>
                {Array.from({ length: Math.min(columns, Math.ceil(gridWidth / CELL_WIDTH)) }, (_, i) => (
                    <div
                        key={i}
                        style={{
                            width: `${CELL_WIDTH}px`,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            backgroundColor: '#f1f5f9',
                            borderRight: '1px solid #e2e8f0',
                            fontSize: '12px',
                            fontWeight: 'bold'
                        }}
                    >
                        {i}
                    </div>
                ))}
            </div>
            
            {/* Main grid container */}
            <div style={{ display: 'flex' }}>
                {/* Row headers */}
                <div style={{ 
                    width: '60px',
                    backgroundColor: '#f1f5f9',
                    borderRight: '2px solid #e2e8f0'
                }}>
                    {Array.from({ length: Math.min(rows, Math.ceil(gridHeight / CELL_HEIGHT)) }, (_, i) => (
                        <div
                            key={i}
                            style={{
                                height: `${CELL_HEIGHT}px`,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                borderBottom: '1px solid #e2e8f0',
                                fontSize: '12px',
                                fontWeight: 'bold'
                            }}
                        >
                            {i}
                        </div>
                    ))}
                </div>
                
                {/* Virtualized grid */}
                <Grid
                    columnCount={columns}
                    columnWidth={CELL_WIDTH}
                    height={gridHeight}
                    rowCount={rows}
                    rowHeight={CELL_HEIGHT}
                    width={gridWidth}
                    className="grid-content"
                    overscanRowCount={5}
                    overscanColumnCount={3}
                >
                    {Cell}
                </Grid>
            </div>
            
            {/* Grid statistics footer */}
            <div className="grid-footer" style={{
                padding: '8px 16px',
                backgroundColor: '#f8fafc',
                borderTop: '1px solid #e2e8f0',
                fontSize: '12px',
                color: '#6b7280',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
            }}>
                <span>
                    Grid: {statistics.gridDimensions} • 
                    Loaded: {statistics.totalCells.toLocaleString()} cells • 
                    Updated: {statistics.updatedCells}
                </span>
                <span>
                    Visible: {statistics.visibleCells} • 
                    Memory: {statistics.memoryEstimate}
                </span>
            </div>
        </div>
    );
};

export default VirtualizedGrid; 