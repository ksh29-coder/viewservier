import { useState, useEffect, useCallback } from 'react';
import webSocketService from '../services/WebSocketService';

/**
 * Custom hook for WebSocket connection management
 * 
 * Manages WebSocket connection state, message handling, and cleanup
 * Follows the patterns specified in .cursorrules
 */
export const useWebSocket = (userId, viewId) => {
    const [connectionStatus, setConnectionStatus] = useState('disconnected');
    const [lastMessage, setLastMessage] = useState(null);
    const [error, setError] = useState(null);
    
    // Message handlers
    const handleConnection = useCallback(() => {
        setConnectionStatus('connected');
        setError(null);
    }, []);
    
    const handleDisconnection = useCallback(() => {
        setConnectionStatus('disconnected');
    }, []);
    
    const handleError = useCallback((errorData) => {
        setError(errorData);
        setConnectionStatus('disconnected');
    }, []);
    
    const handleMessage = useCallback((type, data) => {
        setLastMessage({
            type,
            data,
            timestamp: Date.now()
        });
    }, []);
    
    const handleMaxReconnectAttemptsReached = useCallback(() => {
        setError({ message: 'Maximum reconnection attempts reached. Please refresh the page.' });
        setConnectionStatus('disconnected');
    }, []);
    
    // Connection effect
    useEffect(() => {
        if (!userId || !viewId) {
            return;
        }
        
        // Set up event listeners
        webSocketService.addListener('connected', handleConnection);
        webSocketService.addListener('disconnected', handleDisconnection);
        webSocketService.addListener('error', handleError);
        webSocketService.addListener('maxReconnectAttemptsReached', handleMaxReconnectAttemptsReached);
        
        // Message type listeners
        webSocketService.addListener('initialLoadStart', (data) => handleMessage('initialLoadStart', data));
        webSocketService.addListener('initialLoadChunk', (data) => handleMessage('initialLoadChunk', data));
        webSocketService.addListener('cellUpdate', (data) => handleMessage('cellUpdate', data));
        webSocketService.addListener('pong', (data) => handleMessage('pong', data));
        webSocketService.addListener('subscribeAck', (data) => handleMessage('subscribeAck', data));
        webSocketService.addListener('updateAck', (data) => handleMessage('updateAck', data));
        
        // Set connecting status and connect
        setConnectionStatus('connecting');
        webSocketService.connect(userId, viewId);
        
        // Cleanup function
        return () => {
            webSocketService.removeAllListeners();
            webSocketService.disconnect();
            setConnectionStatus('disconnected');
            setLastMessage(null);
            setError(null);
        };
    }, [userId, viewId]); // Only userId and viewId in dependencies - callbacks are stable
    
    // Ping function for connection health check
    const ping = useCallback(() => {
        webSocketService.ping();
    }, []);
    
    // Subscribe function
    const subscribe = useCallback(() => {
        webSocketService.subscribe();
    }, []);
    
    // Unsubscribe function
    const unsubscribe = useCallback(() => {
        webSocketService.unsubscribe();
    }, []);
    
    // Send custom message
    const sendMessage = useCallback((message) => {
        webSocketService.send(message);
    }, []);
    
    return {
        connectionStatus,
        lastMessage,
        error,
        ping,
        subscribe,
        unsubscribe,
        sendMessage,
        isConnected: connectionStatus === 'connected',
        isConnecting: connectionStatus === 'connecting'
    };
}; 