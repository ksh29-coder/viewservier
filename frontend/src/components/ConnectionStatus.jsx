import React from 'react';

/**
 * Connection Status component for displaying WebSocket connection state
 * 
 * Shows connection indicator and status information
 * Follows the pattern specified in .cursorrules
 */
const ConnectionStatus = ({ 
    connectionStatus = 'disconnected',
    error = null,
    onRetryConnection = null,
    userId = null,
    viewId = null
}) => {
    const getStatusConfig = () => {
        switch (connectionStatus) {
            case 'connected':
                return {
                    indicator: 'connected',
                    text: 'Connected',
                    color: 'text-green-600',
                    bgColor: 'bg-green-50',
                    borderColor: 'border-green-200'
                };
            case 'connecting':
                return {
                    indicator: 'connecting',
                    text: 'Connecting...',
                    color: 'text-yellow-600',
                    bgColor: 'bg-yellow-50',
                    borderColor: 'border-yellow-200'
                };
            case 'disconnected':
            default:
                return {
                    indicator: 'disconnected',
                    text: error ? 'Connection Error' : 'Disconnected',
                    color: 'text-red-600',
                    bgColor: 'bg-red-50',
                    borderColor: 'border-red-200'
                };
        }
    };
    
    const statusConfig = getStatusConfig();
    
    return (
        <div className={`connection-status p-3 rounded-lg border ${statusConfig.bgColor} ${statusConfig.borderColor}`}>
            <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                    {/* Connection Indicator */}
                    <div className={`connection-indicator ${statusConfig.indicator}`}></div>
                    
                    {/* Status Text */}
                    <div>
                        <span className={`font-medium ${statusConfig.color}`}>
                            {statusConfig.text}
                        </span>
                        {userId && viewId && (
                            <span className="text-gray-600 text-sm ml-2">
                                ({userId}/{viewId})
                            </span>
                        )}
                    </div>
                </div>
                
                {/* Action Button */}
                {connectionStatus === 'disconnected' && onRetryConnection && (
                    <button
                        onClick={onRetryConnection}
                        className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1 rounded text-sm transition-colors"
                    >
                        Retry
                    </button>
                )}
            </div>
            
            {/* Error Message */}
            {error && (
                <div className="mt-2 text-sm text-red-600">
                    {error.message || 'An error occurred with the WebSocket connection'}
                </div>
            )}
            
            {/* Connection Details */}
            {connectionStatus === 'connected' && (
                <div className="mt-2 text-xs text-gray-500">
                    WebSocket connection established • Real-time updates active
                </div>
            )}
            
            {connectionStatus === 'connecting' && (
                <div className="mt-2 text-xs text-gray-500">
                    Establishing WebSocket connection...
                </div>
            )}
        </div>
    );
};

export default ConnectionStatus; 