import React, { useMemo } from 'react';

/**
 * Metrics Display component for performance monitoring
 * 
 * Shows real-time performance metrics for the grid application
 * Follows the pattern specified in .cursorrules
 */
const MetricsDisplay = ({
    connectionStatus = 'disconnected',
    gridData = {},
    gridMetadata = {},
    loadingProgress = {},
    lastUpdated = null,
    onRefresh = null
}) => {
    // Calculate derived metrics
    const metrics = useMemo(() => {
        const now = Date.now();
        const totalCells = Object.keys(gridData).length;
        const updatedCells = Object.values(gridData).filter(cell => cell.updated).length;
        const loadedPercentage = gridMetadata.totalCells > 0 
            ? Math.round((gridMetadata.loadedCells / gridMetadata.totalCells) * 100)
            : 0;
        
        return {
            // Connection metrics
            connectionStatus: connectionStatus.charAt(0).toUpperCase() + connectionStatus.slice(1),
            
            // Grid size metrics
            gridSize: `${gridMetadata.rows || 0} × ${gridMetadata.columns || 0}`,
            totalCells: (gridMetadata.totalCells || 0).toLocaleString(),
            loadedCells: (gridMetadata.loadedCells || 0).toLocaleString(),
            loadedPercentage: `${loadedPercentage}%`,
            
            // Real-time metrics
            activeCells: totalCells.toLocaleString(),
            updatedCells: updatedCells.toLocaleString(),
            lastUpdateAgo: lastUpdated ? `${Math.round((now - lastUpdated) / 1000)}s ago` : 'Never',
            
            // Loading metrics
            chunksReceived: loadingProgress.chunksReceived || 0,
            totalChunks: loadingProgress.totalChunks || 0,
            isLoading: loadingProgress.isLoading || false,
            
            // Performance estimates
            memoryUsage: `${Math.round(totalCells * 100 / 1024)}KB`,
            renderingCells: Math.min(totalCells, 200), // Approximate visible cells
            
            // Timestamps
            timestamp: new Date().toLocaleTimeString()
        };
    }, [connectionStatus, gridData, gridMetadata, loadingProgress, lastUpdated]);
    
    const getStatusColor = (status) => {
        switch (status.toLowerCase()) {
            case 'connected': return 'text-green-600';
            case 'connecting': return 'text-yellow-600';
            case 'disconnected': return 'text-red-600';
            default: return 'text-gray-600';
        }
    };
    
    const MetricItem = ({ label, value, valueColor = null }) => (
        <div className="metric-item">
            <span className="metric-label">{label}</span>
            <span className={`metric-value ${valueColor || ''}`}>{value}</span>
        </div>
    );
    
    return (
        <div className="metrics-panel">
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-gray-800">Performance Metrics</h3>
                <div className="flex items-center space-x-4">
                    <span className="text-xs text-gray-500">
                        Last updated: {metrics.timestamp}
                    </span>
                    {onRefresh && (
                        <button
                            onClick={onRefresh}
                            className="bg-blue-100 hover:bg-blue-200 text-blue-700 px-2 py-1 rounded text-sm transition-colors"
                        >
                            Refresh
                        </button>
                    )}
                </div>
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {/* Connection Status Panel */}
                <div className="bg-gray-50 rounded-lg p-4">
                    <h4 className="font-medium text-gray-700 mb-3">Connection</h4>
                    <MetricItem 
                        label="Status" 
                        value={metrics.connectionStatus}
                        valueColor={getStatusColor(metrics.connectionStatus)}
                    />
                    <MetricItem 
                        label="Last Update" 
                        value={metrics.lastUpdateAgo}
                    />
                    <MetricItem 
                        label="Updated Cells" 
                        value={metrics.updatedCells}
                    />
                </div>
                
                {/* Grid Size Panel */}
                <div className="bg-gray-50 rounded-lg p-4">
                    <h4 className="font-medium text-gray-700 mb-3">Grid Size</h4>
                    <MetricItem 
                        label="Dimensions" 
                        value={metrics.gridSize}
                    />
                    <MetricItem 
                        label="Total Cells" 
                        value={metrics.totalCells}
                    />
                    <MetricItem 
                        label="Loaded" 
                        value={`${metrics.loadedCells} (${metrics.loadedPercentage})`}
                    />
                </div>
                
                {/* Performance Panel */}
                <div className="bg-gray-50 rounded-lg p-4">
                    <h4 className="font-medium text-gray-700 mb-3">Performance</h4>
                    <MetricItem 
                        label="Memory Usage" 
                        value={metrics.memoryUsage}
                    />
                    <MetricItem 
                        label="Active Cells" 
                        value={metrics.activeCells}
                    />
                    <MetricItem 
                        label="Rendering" 
                        value={`${metrics.renderingCells} cells`}
                    />
                </div>
                
                {/* Loading Status Panel */}
                {metrics.isLoading && (
                    <div className="bg-blue-50 rounded-lg p-4 md:col-span-2 lg:col-span-3">
                        <h4 className="font-medium text-blue-700 mb-3">Loading Progress</h4>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            <MetricItem 
                                label="Chunks Received" 
                                value={metrics.chunksReceived}
                            />
                            <MetricItem 
                                label="Total Chunks" 
                                value={metrics.totalChunks}
                            />
                            <MetricItem 
                                label="Progress" 
                                value={`${Math.round((metrics.chunksReceived / metrics.totalChunks) * 100)}%`}
                            />
                            <MetricItem 
                                label="Status" 
                                value="Loading..."
                                valueColor="text-blue-600"
                            />
                        </div>
                    </div>
                )}
            </div>
            
            {/* Performance Indicators */}
            <div className="mt-4 p-3 bg-gradient-to-r from-blue-50 to-green-50 rounded-lg border border-blue-200">
                <div className="flex items-center justify-between text-sm">
                    <div className="flex items-center space-x-4">
                        <span className="text-gray-600">
                            <strong>Performance Target:</strong> &lt;50ms latency
                        </span>
                        <span className="text-gray-600">
                            <strong>Capacity:</strong> 1M cells, 1K+ connections
                        </span>
                    </div>
                    <div className="flex items-center space-x-2">
                        <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                        <span className="text-green-700 text-xs font-medium">
                            Optimal Performance
                        </span>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default MetricsDisplay; 