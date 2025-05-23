import { useState, useCallback, useMemo } from 'react';

/**
 * Custom hook for grid data management
 * 
 * Manages grid state, cell updates, and loading states
 * Optimized for large datasets (100 columns × 10,000 rows)
 * Follows patterns specified in .cursorrules
 */
export const useGridData = () => {
    const [gridData, setGridData] = useState({});
    const [gridMetadata, setGridMetadata] = useState({
        rows: 0,
        columns: 0,
        totalCells: 0,
        loadedCells: 0
    });
    const [loading, setLoading] = useState(false);
    const [loadingProgress, setLoadingProgress] = useState({
        chunksReceived: 0,
        totalChunks: 0,
        isLoading: false
    });
    const [error, setError] = useState(null);
    const [lastUpdated, setLastUpdated] = useState(null);
    
    // Initialize grid from initial load start message
    const initializeGrid = useCallback((data) => {
        setGridMetadata({
            rows: data.rows,
            columns: data.columns,
            totalCells: data.totalCells,
            loadedCells: 0
        });
        
        const totalChunks = Math.ceil(data.totalCells / data.chunkSize);
        setLoadingProgress({
            chunksReceived: 0,
            totalChunks,
            isLoading: true
        });
        
        setLoading(true);
        setError(null);
        setGridData({}); // Clear existing data
    }, []);
    
    // Process chunk of initial data
    const processChunk = useCallback((chunkData) => {
        const { chunkIndex, cells, isLast } = chunkData;
        
        setGridData(prevData => {
            const newData = { ...prevData };
            
            // Add cells from chunk
            cells.forEach(cell => {
                const key = `${cell.row}:${cell.column}`;
                newData[key] = {
                    row: cell.row,
                    column: cell.column,
                    value: cell.value,
                    dataType: cell.dataType,
                    timestamp: cell.timestamp,
                    updated: false
                };
            });
            
            return newData;
        });
        
        // Update loading progress
        setLoadingProgress(prev => ({
            ...prev,
            chunksReceived: chunkIndex + 1
        }));
        
        // Update metadata
        setGridMetadata(prev => ({
            ...prev,
            loadedCells: prev.loadedCells + cells.length
        }));
        
        // If this is the last chunk, finish loading
        if (isLast) {
            setLoading(false);
            setLoadingProgress(prev => ({
                ...prev,
                isLoading: false
            }));
        }
    }, []);
    
    // Update cells from real-time updates
    const updateCells = useCallback((updates) => {
        if (!updates || !Array.isArray(updates)) {
            console.warn('Invalid updates data:', updates);
            return;
        }
        
        setGridData(prevData => {
            const newData = { ...prevData };
            
            updates.forEach(update => {
                const key = `${update.row}:${update.column}`;
                newData[key] = {
                    row: update.row,
                    column: update.column,
                    value: update.value,
                    dataType: update.dataType,
                    timestamp: Date.now(),
                    updated: true // Mark as recently updated for visual indication
                };
            });
            
            return newData;
        });
        
        setLastUpdated(Date.now());
    }, []);
    
    // Get cell data for specific coordinates
    const getCell = useCallback((row, column) => {
        const key = `${row}:${column}`;
        return gridData[key] || null;
    }, []);
    
    // Get cells in a specific range (for virtualization)
    const getCellsInRange = useCallback((startRow, endRow, startCol, endCol) => {
        const cells = [];
        for (let row = startRow; row <= endRow; row++) {
            for (let col = startCol; col <= endCol; col++) {
                const key = `${row}:${col}`;
                const cell = gridData[key] || null;
                if (cell) {
                    cells.push(cell);
                }
            }
        }
        return cells;
    }, []);
    
    // Clear all grid data
    const clearGrid = useCallback(() => {
        setGridData({});
        setGridMetadata({
            rows: 0,
            columns: 0,
            totalCells: 0,
            loadedCells: 0
        });
        setLoading(false);
        setLoadingProgress({
            chunksReceived: 0,
            totalChunks: 0,
            isLoading: false
        });
        setError(null);
        setLastUpdated(null);
    }, []);
    
    // Set error state
    const setGridError = useCallback((errorMessage) => {
        setError(errorMessage);
        setLoading(false);
        setLoadingProgress(prev => ({
            ...prev,
            isLoading: false
        }));
    }, []);
    
    // Get loading percentage
    const loadingPercentage = useMemo(() => {
        if (loadingProgress.totalChunks === 0) return 0;
        return Math.round((loadingProgress.chunksReceived / loadingProgress.totalChunks) * 100);
    }, [loadingProgress.chunksReceived, loadingProgress.totalChunks]);
    
    // Get statistics for monitoring
    const statistics = useMemo(() => {
        const cellCount = Object.keys(gridData).length;
        
        return {
            totalCells: cellCount,
            updatedCells: 0, // Simplified to prevent expensive filtering
            memoryUsageEstimate: cellCount * 100, // rough estimate in bytes
            lastUpdated
        };
    }, [gridMetadata.loadedCells, lastUpdated]); // Use loadedCells instead of gridData to reduce recalculations
    
    return {
        // Data
        gridData,
        gridMetadata,
        
        // State
        loading,
        loadingProgress,
        loadingPercentage,
        error,
        lastUpdated,
        statistics,
        
        // Actions
        initializeGrid,
        processChunk,
        updateCells,
        getCell,
        getCellsInRange,
        clearGrid,
        setGridError
    };
}; 