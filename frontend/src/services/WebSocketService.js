/**
 * WebSocket Service for View Server POC
 * 
 * Handles WebSocket connections to the backend view server
 * Implements singleton pattern for efficient connection management
 * Supports reconnection with exponential backoff
 */
class WebSocketService {
    constructor() {
        this.socket = null;
        this.listeners = new Map();
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 1000; // Start with 1 second
        this.isConnecting = false;
        this.shouldReconnect = true;
        this.userId = null;
        this.viewId = null;
        
        // Message queue for messages received before listeners are registered
        this.messageQueue = [];
        this.connected = false;
    }
    
    /**
     * Connect to the WebSocket server
     */
    connect(userId, viewId) {
        if (this.isConnecting || (this.socket && this.socket.readyState === WebSocket.OPEN)) {
            console.log('WebSocket already connecting or connected');
            return;
        }
        
        this.userId = userId;
        this.viewId = viewId;
        this.isConnecting = true;
        this.shouldReconnect = true;
        
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = window.location.hostname === 'localhost' ? 'localhost:8080' : window.location.host;
        const url = `${protocol}//${host}/ws/grid/${userId}/${viewId}`;
        
        console.log(`Connecting to WebSocket: ${url}`);
        
        try {
            this.socket = new WebSocket(url);
            this.setupEventHandlers();
        } catch (error) {
            console.error('Failed to create WebSocket connection:', error);
            this.isConnecting = false;
            this.scheduleReconnect();
        }
    }
    
    /**
     * Set up WebSocket event handlers
     */
    setupEventHandlers() {
        this.socket.onopen = (event) => {
            console.log('WebSocket connected successfully');
            this.isConnecting = false;
            this.connected = true;
            this.reconnectAttempts = 0;
            this.reconnectDelay = 1000; // Reset delay
            
            // Process any queued messages
            this.processMessageQueue();
            
            this.notifyListeners('connected', { event });
        };
        
        this.socket.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                console.log('Received WebSocket message:', message.type, message);
                
                // Add to queue if no listeners yet
                if (this.listeners.size === 0) {
                    this.messageQueue.push(message);
                } else {
                    this.handleMessage(message);
                }
            } catch (error) {
                console.error('Error parsing WebSocket message:', error, event.data);
            }
        };
        
        this.socket.onclose = (event) => {
            console.log('WebSocket connection closed:', event.code, event.reason);
            this.isConnecting = false;
            this.connected = false;
            
            this.notifyListeners('disconnected', { event });
            
            if (this.shouldReconnect && event.code !== 1000) { // 1000 = normal closure
                this.scheduleReconnect();
            }
        };
        
        this.socket.onerror = (error) => {
            console.error('WebSocket error:', error);
            this.isConnecting = false;
            this.connected = false;
            
            this.notifyListeners('error', { error });
        };
    }
    
    /**
     * Handle incoming messages
     */
    handleMessage(message) {
        const { type, ...data } = message;
        
        switch (type) {
            case 'INITIAL_LOAD_START':
                this.notifyListeners('initialLoadStart', data);
                break;
            case 'INITIAL_LOAD_CHUNK':
                this.notifyListeners('initialLoadChunk', data);
                break;
            case 'CELL_UPDATE':
                this.notifyListeners('cellUpdate', data);
                break;
            case 'PONG':
                this.notifyListeners('pong', data);
                break;
            case 'SUBSCRIBE_ACK':
                this.notifyListeners('subscribeAck', data);
                break;
            case 'UPDATE_ACK':
                this.notifyListeners('updateAck', data);
                break;
            case 'ERROR':
                this.notifyListeners('error', data);
                break;
            default:
                console.warn('Unknown message type:', type, data);
        }
    }
    
    /**
     * Process queued messages
     */
    processMessageQueue() {
        while (this.messageQueue.length > 0) {
            const message = this.messageQueue.shift();
            this.handleMessage(message);
        }
    }
    
    /**
     * Send a message to the server
     */
    send(message) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            try {
                this.socket.send(JSON.stringify(message));
                console.log('Sent WebSocket message:', message.type);
            } catch (error) {
                console.error('Error sending WebSocket message:', error);
            }
        } else {
            console.warn('WebSocket not connected, cannot send message:', message);
        }
    }
    
    /**
     * Send ping message for connection health check
     */
    ping() {
        this.send({
            type: 'PING',
            gridId: `${this.userId}_${this.viewId}`,
            data: 'ping'
        });
    }
    
    /**
     * Subscribe to grid updates (implicit in connection)
     */
    subscribe() {
        this.send({
            type: 'SUBSCRIBE',
            gridId: `${this.userId}_${this.viewId}`,
            data: 'subscribe'
        });
    }
    
    /**
     * Unsubscribe from grid updates
     */
    unsubscribe() {
        this.send({
            type: 'UNSUBSCRIBE',
            gridId: `${this.userId}_${this.viewId}`,
            data: 'unsubscribe'
        });
    }
    
    /**
     * Schedule reconnection with exponential backoff
     */
    scheduleReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('Max reconnection attempts reached. Giving up.');
            this.notifyListeners('maxReconnectAttemptsReached', {});
            return;
        }
        
        this.reconnectAttempts++;
        const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);
        
        console.log(`Scheduling reconnect attempt ${this.reconnectAttempts} in ${delay}ms`);
        
        setTimeout(() => {
            if (this.shouldReconnect && this.userId && this.viewId) {
                this.connect(this.userId, this.viewId);
            }
        }, delay);
    }
    
    /**
     * Disconnect from the WebSocket server
     */
    disconnect() {
        this.shouldReconnect = false;
        
        if (this.socket) {
            this.socket.close(1000, 'User disconnected');
            this.socket = null;
        }
        
        this.connected = false;
        this.isConnecting = false;
        this.userId = null;
        this.viewId = null;
        this.messageQueue = [];
        
        console.log('WebSocket disconnected by user');
    }
    
    /**
     * Add event listener
     */
    addListener(eventType, callback) {
        if (!this.listeners.has(eventType)) {
            this.listeners.set(eventType, []);
        }
        this.listeners.get(eventType).push(callback);
        
        // Process any queued messages for new listeners
        if (this.messageQueue.length > 0) {
            this.processMessageQueue();
        }
    }
    
    /**
     * Remove event listener
     */
    removeListener(eventType, callback) {
        if (this.listeners.has(eventType)) {
            const callbacks = this.listeners.get(eventType);
            const index = callbacks.indexOf(callback);
            if (index > -1) {
                callbacks.splice(index, 1);
            }
            
            if (callbacks.length === 0) {
                this.listeners.delete(eventType);
            }
        }
    }
    
    /**
     * Remove all listeners for an event type
     */
    removeAllListeners(eventType) {
        if (eventType) {
            this.listeners.delete(eventType);
        } else {
            this.listeners.clear();
        }
    }
    
    /**
     * Notify all listeners of an event
     */
    notifyListeners(eventType, data) {
        if (this.listeners.has(eventType)) {
            this.listeners.get(eventType).forEach(callback => {
                try {
                    callback(data);
                } catch (error) {
                    console.error(`Error in listener for ${eventType}:`, error);
                }
            });
        }
    }
    
    /**
     * Get connection status
     */
    getConnectionStatus() {
        if (this.isConnecting) return 'connecting';
        if (this.connected) return 'connected';
        return 'disconnected';
    }
    
    /**
     * Check if connected
     */
    isConnected() {
        return this.connected && this.socket && this.socket.readyState === WebSocket.OPEN;
    }
}

// Export singleton instance
const webSocketService = new WebSocketService();
export default webSocketService; 