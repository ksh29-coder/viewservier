import React, { useState } from 'react';
import SimpleGridDebug from './components/SimpleGridDebug';
import ErrorBoundary from './components/ErrorBoundary';
import './index.css';

/**
 * Main App component
 * 
 * Entry point for the View Server Performance POC
 * Handles user/view configuration and routing
 * Follows the pattern specified in .cursorrules
 */
function App() {
    const [userId, setUserId] = useState('user1');
    const [viewId, setViewId] = useState('view1');
    const [showConfig, setShowConfig] = useState(false);
    
    const handleUserIdChange = (e) => {
        setUserId(e.target.value || 'user1');
    };
    
    const handleViewIdChange = (e) => {
        setViewId(e.target.value || 'view1');
    };
    
    return (
        <ErrorBoundary>
            <div className="min-h-screen bg-gray-50">
                {/* Configuration Panel (toggleable) */}
                {showConfig && (
                    <div className="bg-white border-b border-gray-200 p-4">
                        <div className="max-w-6xl mx-auto">
                            <h2 className="text-lg font-semibold text-gray-800 mb-4">Configuration</h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <label htmlFor="userId" className="block text-sm font-medium text-gray-700 mb-2">
                                        User ID
                                    </label>
                                    <input
                                        type="text"
                                        id="userId"
                                        value={userId}
                                        onChange={handleUserIdChange}
                                        placeholder="Enter user ID"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                    />
                                    <p className="mt-1 text-sm text-gray-600">
                                        Identifies the user session for WebSocket connection
                                    </p>
                                </div>
                                <div>
                                    <label htmlFor="viewId" className="block text-sm font-medium text-gray-700 mb-2">
                                        View ID
                                    </label>
                                    <input
                                        type="text"
                                        id="viewId"
                                        value={viewId}
                                        onChange={handleViewIdChange}
                                        placeholder="Enter view ID"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                    />
                                    <p className="mt-1 text-sm text-gray-600">
                                        Identifies the specific grid view to display
                                    </p>
                                </div>
                            </div>
                            <div className="mt-4 p-4 bg-blue-50 rounded-lg">
                                <h3 className="text-sm font-medium text-blue-800 mb-2">Connection Details</h3>
                                <p className="text-sm text-blue-700">
                                    WebSocket URL: <code className="bg-blue-100 px-1 rounded">ws://localhost:8080/ws/grid/{userId}/{viewId}</code>
                                </p>
                                <p className="text-sm text-blue-700 mt-1">
                                    Current: <code className="bg-blue-100 px-1 rounded">ws://localhost:8080/ws/grid/{userId}/{viewId}</code>
                                </p>
                            </div>
                        </div>
                    </div>
                )}
                
                {/* Toggle Configuration Button */}
                <div className="bg-white border-b border-gray-200 px-4 py-2">
                    <div className="max-w-6xl mx-auto flex justify-between items-center">
                        <div className="flex items-center space-x-4">
                            <h1 className="text-xl font-bold text-gray-800">
                                View Server Performance POC
                            </h1>
                            <span className="text-sm text-gray-600">
                                Connected as: {userId}/{viewId}
                            </span>
                        </div>
                        <button
                            onClick={() => setShowConfig(!showConfig)}
                            className="bg-gray-100 hover:bg-gray-200 text-gray-700 px-3 py-1 rounded text-sm transition-colors"
                        >
                            {showConfig ? 'Hide Config' : 'Show Config'}
                        </button>
                    </div>
                </div>
                
                {/* Main Application */}
                <div className="max-w-6xl mx-auto p-4">
                    <SimpleGridDebug userId={userId} viewId={viewId} key={`${userId}-${viewId}`} />
                </div>
                
                {/* Footer */}
                <footer className="bg-white border-t border-gray-200 mt-8">
                    <div className="max-w-6xl mx-auto px-4 py-6">
                        <div className="flex flex-col md:flex-row justify-between items-center">
                            <div className="text-sm text-gray-600">
                                <p>View Server Performance POC</p>
                                <p>Demonstrates horizontal scaling for real-time grid updates</p>
                            </div>
                            <div className="mt-4 md:mt-0 text-sm text-gray-500">
                                <div className="flex items-center space-x-4">
                                    <span>Target: &lt;50ms latency</span>
                                    <span>•</span>
                                    <span>Capacity: 1M cells</span>
                                    <span>•</span>
                                    <span>Connections: 1K+</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </footer>
            </div>
        </ErrorBoundary>
    );
}

export default App;
