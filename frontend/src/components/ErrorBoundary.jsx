import React from 'react';

/**
 * Error Boundary component for catching and handling React component errors
 * 
 * Provides graceful error recovery for the grid application
 * Follows the pattern specified in .cursorrules
 */
class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            hasError: false,
            error: null,
            errorInfo: null
        };
    }
    
    static getDerivedStateFromError(error) {
        // Update state so the next render will show the fallback UI
        return {
            hasError: true,
            error
        };
    }
    
    componentDidCatch(error, errorInfo) {
        // Log the error for debugging
        console.error('Grid component error:', error, errorInfo);
        
        this.setState({
            error,
            errorInfo
        });
    }
    
    handleRetry = () => {
        // Reset error state to retry
        this.setState({
            hasError: false,
            error: null,
            errorInfo: null
        });
    }
    
    handleReload = () => {
        // Reload the entire page
        window.location.reload();
    }
    
    render() {
        if (this.state.hasError) {
            // Custom error UI
            return (
                <div className="error-fallback">
                    <h2 className="text-2xl font-bold text-red-600 mb-4">
                        Something went wrong with the grid
                    </h2>
                    
                    <p className="text-gray-600 mb-4">
                        An unexpected error occurred while rendering the grid component.
                    </p>
                    
                    {this.state.error && (
                        <div className="bg-red-50 border border-red-200 rounded p-4 mb-4 text-left">
                            <p className="font-semibold text-red-800 mb-2">Error Details:</p>
                            <p className="text-red-700 text-sm font-mono">
                                {this.state.error.toString()}
                            </p>
                        </div>
                    )}
                    
                    <div className="space-x-4">
                        <button
                            onClick={this.handleRetry}
                            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded transition-colors"
                        >
                            Try Again
                        </button>
                        
                        <button
                            onClick={this.handleReload}
                            className="bg-red-600 hover:bg-red-700 text-white px-4 py-2 rounded transition-colors"
                        >
                            Reload Page
                        </button>
                    </div>
                    
                    {process.env.NODE_ENV === 'development' && this.state.errorInfo && (
                        <details className="mt-6 text-left">
                            <summary className="cursor-pointer text-gray-600 hover:text-gray-800">
                                Technical Details (Development Only)
                            </summary>
                            <pre className="mt-2 p-4 bg-gray-100 border rounded text-xs overflow-auto">
                                {this.state.errorInfo.componentStack}
                            </pre>
                        </details>
                    )}
                </div>
            );
        }
        
        // No error, render children normally
        return this.props.children;
    }
}

export default ErrorBoundary; 