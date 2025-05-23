    // Show loading indicator if loading or connecting
    if (loading || isConnecting) {
        console.log('GridView: Still loading/connecting', { loading, isConnecting, loadingProgress });
        return (
            <div className="grid-container">
                <div className="grid-header">
                    <h1 className="text-2xl font-bold text-gray-800 mb-4">
                        View Server Performance POC
                    </h1>
                    <ConnectionStatus
                        connectionStatus={connectionStatus}
                        error={connectionError}
                        onRetryConnection={handleRetryConnection}
                        userId={userId}
                        viewId={viewId}
                    />
                </div>
                <div className="grid-content">
                    <LoadingIndicator
                        chunksReceived={loadingProgress.chunksReceived}
                        totalChunks={loadingProgress.totalChunks}
                        isLoading={loading || isConnecting}
                        loadingPercentage={loadingPercentage}
                        totalCells={gridMetadata.totalCells}
                        loadedCells={gridMetadata.loadedCells}
                    />
                </div>
            </div>
        );
    }
    
    // Debug: Log grid data when rendering
    console.log('GridView: Rendering grid', { 
        gridData: Object.keys(gridData).length, 
        gridMetadata, 
        loading, 
        isConnected,
        connectionStatus,
        gridError 
    });
    console.log('GridView: Sample grid data:', Object.entries(gridData).slice(0, 3)); 