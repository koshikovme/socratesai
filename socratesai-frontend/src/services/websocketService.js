import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { WEBSOCKET_BASE_URL } from './api'

let client = null

function buildSocketUrl() {
    return WEBSOCKET_BASE_URL ? `${WEBSOCKET_BASE_URL}/ws` : '/ws'
}

export function createMentorSocket({ token, onFeedback, onConnect, onDisconnect, onError }) {
    disconnectSocket()

    client = new Client({
        webSocketFactory: () => new SockJS(buildSocketUrl()),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        reconnectDelay: 5000,
        connectionTimeout: 8000,
        debug: () => {}
    })

    client.onConnect = () => {
        client.subscribe('/user/queue/feedback', (message) => {
            const body = JSON.parse(message.body)
            onFeedback?.(body)
        })
        onConnect?.()
    }

    client.onStompError = (frame) => {
        console.error('STOMP error', frame)
        onError?.(frame)
    }

    client.onWebSocketError = (event) => {
        onError?.(event)
    }

    client.onWebSocketClose = () => {
        onDisconnect?.()
    }

    client.activate()
    return client
}

export function sendCodeUpdate(payload) {
    if (!client || !client.connected) return

    client.publish({
        destination: '/app/code.update',
        body: JSON.stringify(payload)
    })
}

export function disconnectSocket() {
    if (client) {
        client.deactivate()
        client = null
    }
}
