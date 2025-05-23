import React from 'react';

/**
 * Loading Indicator component for showing chunked data loading progress
 * 
 * Displays progress bar and loading status for initial grid data loading
 * Follows the pattern specified in .cursorrules
 */
const LoadingIndicator = ({ 
    chunksReceived = 0, 
    totalChunks = 0, 
    isLoading = false,
    loadingPercentage = 0,
    totalCells = 0,
    loadedCells = 0
}) => {
    if (!isLoading) return null;
    
    return (
        <div className="loading-container">
            <div className="flex flex-col items-center justify-center space-y-4">
                {/* Loading Spinner */}
                <div className="w-12 h-12 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin"></div>
                
                {/* Loading Text */}
                <div className="text-center">
                    <h3 className="text-lg font-semibold text-gray-800 mb-2">
                        Loading Grid Data...
                    </h3>
                    <p className="text-gray-600">
                        {totalChunks > 0 
                            ? `${chunksReceived}/${totalChunks} chunks loaded`
                            : 'Initializing connection...'
                        }
                    </p>
                    {totalCells > 0 && (
                        <p className="text-sm text-gray-500">
                            {loadedCells.toLocaleString()} of {totalCells.toLocaleString()} cells loaded
                        </p>
                    )}
                </div>
                
                {/* Progress Bar */}
                {totalChunks > 0 && (
                    <div className="w-80 max-w-md">
                        <div className="flex justify-between text-sm text-gray-600 mb-1">
                            <span>Progress</span>
                            <span>{loadingPercentage}%</span>
                        </div>
                        <div className="progress-bar">
                            <div 
                                className="progress-fill transition-all duration-300 ease-out" 
                                style={{ width: `${loadingPercentage}%` }}
                            />
                        </div>
                    </div>
                )}
                
                {/* Performance Info */}
                <div className="text-xs text-gray-500 text-center max-w-md">
                    <p>
                        Loading 100 × 10,000 cell grid with efficient chunked transfer.
                        This ensures optimal performance for large datasets.
                    </p>
                </div>
            </div>
        </div>
    );
};

export default LoadingIndicator; 