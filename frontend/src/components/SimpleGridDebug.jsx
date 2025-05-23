import React, { useEffect, useState } from 'react';
import { useWebSocket } from '../hooks/useWebSocket';
import { useGridData } from '../hooks/useGridData';

/**
 * Simple Grid Debug component to troubleshoot data loading
 */
const SimpleGridDebug = ({ userId = 'user1', viewId = 'view1' }) => {
    const [flashingCells, setFlashingCells] = useState(new Set());
    const [updateCount, setUpdateCount] = useState(0);
    const [lastUpdateTime, setLastUpdateTime] = useState(null);
    
    // WebSocket connection management
    const {
        connectionStatus,
        lastMessage,
        error: connectionError,
        isConnected,
        isConnecting
    } = useWebSocket(userId, viewId);
    
    // Grid data management
    const {
        gridData,
        gridMetadata,
        loading,
        loadingProgress,
        error: _gridError,
        initializeGrid,
        processChunk,
        updateCells,
        setGridError
    } = useGridData();

    // Handle flashing animation for updated cells
    const flashCell = (cellKey) => {
        setFlashingCells(prev => new Set([...prev, cellKey]));
        setTimeout(() => {
            setFlashingCells(prev => {
                const newSet = new Set(prev);
                newSet.delete(cellKey);
                return newSet;
            });
        }, 1000); // Flash for 1 second
    };

    // Handle incoming WebSocket messages with better error handling
    useEffect(() => {
        if (!lastMessage) return;
        
        const { type, data } = lastMessage;
        console.log('🔄 DEBUG: Received message', { type, data, timestamp: new Date().toISOString() });
        
        try {
            switch (type) {
                case 'initialLoadStart':
                    console.log('🚀 DEBUG: Starting initial load:', data);
                    initializeGrid(data);
                    break;
                
                case 'initialLoadChunk':
                    console.log('📦 DEBUG: Processing chunk:', data.chunkIndex, 'cells:', data.cells?.length);
                    processChunk(data);
                    break;
                
                case 'cellUpdate':
                    console.log('⚡ DEBUG: Processing cell update:', data.updates?.length, 'cells');
                    if (data.updates && data.updates.length > 0) {
                        // Use the proper updateCells method for real-time updates
                        updateCells(data.updates);
                        
                        // Update stats and flash cells
                        setUpdateCount(prev => prev + data.updates.length);
                        setLastUpdateTime(new Date());
                        
                        // Flash each updated cell
                        data.updates.forEach(update => {
                            const cellKey = `${update.row}:${update.column}`;
                            flashCell(cellKey);
                        });
                    }
                    break;
                
                default:
                    console.log('❓ DEBUG: Unknown message type:', type, data);
            }
        } catch (error) {
            console.error('💥 DEBUG: Error processing message:', error, { type, data });
            setGridError(`Error processing ${type}: ${error.message}`);
        }
    }, [lastMessage, initializeGrid, processChunk, updateCells, setGridError]);

    // Auto-retry connection if disconnected
    useEffect(() => {
        if (connectionError && !isConnecting) {
            console.log('🔁 Auto-retrying connection in 3 seconds...');
            const retryTimer = setTimeout(() => {
                window.location.reload(); // Simple retry by refreshing
            }, 3000);
            return () => clearTimeout(retryTimer);
        }
    }, [connectionError, isConnecting]);

    const getConnectionStatusColor = () => {
        switch (connectionStatus) {
            case 'connected': return 'text-green-600';
            case 'connecting': return 'text-yellow-600';
            case 'disconnected': return 'text-red-600';
            default: return 'text-gray-600';
        }
    };

    const formatTimestamp = (timestamp) => {
        if (!timestamp) return 'Never';
        return timestamp.toLocaleTimeString();
    };

    return (
        <div style={{ 
            padding: '20px', 
            fontFamily: 'Arial, sans-serif',
            backgroundColor: '#f9f9f9',
            minHeight: '100vh'
        }}>
            <style>
                {`
                    @keyframes flash-green {
                        0% { background-color: #10b981; color: white; transform: scale(1.05); }
                        50% { background-color: #34d399; color: white; transform: scale(1.1); }
                        100% { background-color: inherit; color: inherit; transform: scale(1); }
                    }
                    .flash-update {
                        animation: flash-green 1s ease-in-out;
                    }
                `}
            </style>
            
            <h1 style={{ 
                fontSize: '28px', 
                fontWeight: 'bold', 
                color: '#1f2937', 
                marginBottom: '20px',
                textAlign: 'center'
            }}>
                🚀 View Server Performance POC - Debug Mode
            </h1>

            {/* Connection Status Panel */}
            <div style={{ 
                display: 'grid', 
                gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', 
                gap: '20px', 
                marginBottom: '30px' 
            }}>
                <div style={{ 
                    padding: '20px', 
                    backgroundColor: 'white', 
                    border: '2px solid #e5e7eb', 
                    borderRadius: '8px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                }}>
                    <h2 style={{ color: '#374151', fontSize: '18px', fontWeight: 'bold', marginBottom: '15px' }}>
                        🔌 Connection Status
                    </h2>
                    <div style={{ marginBottom: '10px' }}>
                        <strong>Status:</strong> 
                        <span className={getConnectionStatusColor()} style={{ marginLeft: '8px', fontWeight: 'bold' }}>
                            {connectionStatus?.toUpperCase() || 'UNKNOWN'}
                        </span>
                    </div>
                    <div style={{ marginBottom: '10px' }}>
                        <strong>Connected:</strong> 
                        <span style={{ marginLeft: '8px', color: isConnected ? '#10b981' : '#ef4444' }}>
                            {isConnected ? '✅ YES' : '❌ NO'}
                        </span>
                    </div>
                    <div style={{ marginBottom: '10px' }}>
                        <strong>Updates Received:</strong> 
                        <span style={{ marginLeft: '8px', color: '#3b82f6', fontWeight: 'bold' }}>
                            {updateCount}
                        </span>
                    </div>
                    <div>
                        <strong>Last Update:</strong> 
                        <span style={{ marginLeft: '8px', color: '#6b7280' }}>
                            {formatTimestamp(lastUpdateTime)}
                        </span>
                    </div>
                    {connectionError && (
                        <div style={{ 
                            marginTop: '15px', 
                            padding: '10px', 
                            backgroundColor: '#fef2f2', 
                            border: '1px solid #fecaca', 
                            borderRadius: '4px',
                            color: '#dc2626'
                        }}>
                            <strong>⚠️ Error:</strong> {typeof connectionError === 'string' ? connectionError : 'Connection failed'}
                        </div>
                    )}
                </div>

                <div style={{ 
                    padding: '20px', 
                    backgroundColor: 'white', 
                    border: '2px solid #e5e7eb', 
                    borderRadius: '8px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                }}>
                    <h2 style={{ color: '#374151', fontSize: '18px', fontWeight: 'bold', marginBottom: '15px' }}>
                        📊 Grid Information
                    </h2>
                    {gridMetadata ? (
                        <>
                            <div style={{ marginBottom: '10px' }}>
                                <strong>Grid Size:</strong> 
                                <span style={{ marginLeft: '8px', color: '#3b82f6', fontWeight: 'bold' }}>
                                    {gridMetadata.rows} × {gridMetadata.columns}
                                </span>
                            </div>
                            <div style={{ marginBottom: '10px' }}>
                                <strong>Total Cells:</strong> 
                                <span style={{ marginLeft: '8px', color: '#10b981', fontWeight: 'bold' }}>
                                    {gridMetadata.totalCells}
                                </span>
                            </div>
                            <div style={{ marginBottom: '10px' }}>
                                <strong>Loaded Cells:</strong> 
                                <span style={{ marginLeft: '8px', color: '#6366f1' }}>
                                    {Object.keys(gridData).length}
                                </span>
                            </div>
                            <div>
                                <strong>Progress:</strong> 
                                <span style={{ marginLeft: '8px', color: loading ? '#f59e0b' : '#10b981' }}>
                                    {loading ? `Loading... ${loadingProgress || 0}%` : '✅ Complete'}
                                </span>
                            </div>
                        </>
                    ) : (
                        <div style={{ color: '#6b7280' }}>No grid data available</div>
                    )}
                </div>
            </div>

            {/* Grid Display */}
            {gridMetadata && Object.keys(gridData).length > 0 && (
                <div style={{ 
                    padding: '20px', 
                    backgroundColor: 'white', 
                    border: '2px solid #e5e7eb', 
                    borderRadius: '8px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                }}>
                    <h2 style={{ color: '#374151', fontSize: '18px', fontWeight: 'bold', marginBottom: '15px' }}>
                        🎯 Live Grid ({gridMetadata.rows} × {gridMetadata.columns})
                    </h2>
                    <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '15px' }}>
                        💡 Tip: Updated cells will flash green when they change!
                    </div>
                    
                    <div style={{ 
                        display: 'grid', 
                        gridTemplateColumns: `repeat(${Math.min(gridMetadata.columns, 25)}, 1fr)`, 
                        gap: '2px',
                        maxWidth: '100%',
                        overflow: 'auto'
                    }}>
                        {Array.from({ length: Math.min(gridMetadata.rows, 20) }, (_, row) =>
                            Array.from({ length: Math.min(gridMetadata.columns, 25) }, (_, col) => {
                                const cellKey = `${row}:${col}`;
                                const cell = gridData[cellKey];
                                const isFlashing = flashingCells.has(cellKey);
                                
                                return (
                                    <div
                                        key={cellKey}
                                        className={isFlashing ? 'flash-update' : ''}
                                        style={{
                                            padding: '4px',
                                            border: '1px solid #374151',
                                            backgroundColor: cell ? '#f9fafb' : '#ffffff',
                                            fontSize: '10px',
                                            textAlign: 'center',
                                            minHeight: '20px',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            position: 'relative',
                                            color: '#1f2937',
                                            fontWeight: '500'
                                        }}
                                        title={cell ? `${cellKey}: ${cell.value} (${cell.dataType})` : `${cellKey}: Empty`}
                                    >
                                        {cell ? String(cell.value).substring(0, 8) : '—'}
                                    </div>
                                );
                            })
                        ).flat()}
                    </div>
                    
                    {(gridMetadata.rows > 20 || gridMetadata.columns > 25) && (
                        <div style={{ 
                            marginTop: '10px', 
                            fontSize: '12px', 
                            color: '#6b7280', 
                            textAlign: 'center' 
                        }}>
                            Showing first 20×25 cells of {gridMetadata.rows}×{gridMetadata.columns} grid
                        </div>
                    )}
                </div>
            )}

            {/* Loading State */}
            {(loading || isConnecting) && (
                <div style={{ 
                    textAlign: 'center', 
                    padding: '40px',
                    backgroundColor: 'white',
                    border: '2px solid #e5e7eb',
                    borderRadius: '8px',
                    marginTop: '20px'
                }}>
                    <div style={{ fontSize: '18px', color: '#3b82f6', marginBottom: '10px' }}>
                        🔄 {isConnecting ? 'Connecting to server...' : `Loading grid data... ${loadingProgress}%`}
                    </div>
                    <div style={{ fontSize: '14px', color: '#6b7280' }}>
                        {isConnecting ? 'Establishing WebSocket connection' : 'Receiving initial data chunks'}
                    </div>
                </div>
            )}
        </div>
    );
};

export default SimpleGridDebug; 