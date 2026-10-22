import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { WEBSOCKET_BASE_URL } from './api'
import { nativeMentorSnapshot } from './mentorService'

/** One transport per workspace; neither bearer tokens nor source enter diagnostics. */
export function createMentorSocket({ token, onFeedback, onConnect, onDisconnect, onError }) {
    let closed = false
    const client = new Client({
        webSocketFactory: () => new SockJS(`${WEBSOCKET_BASE_URL || ''}/ws`),
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 5000,
        connectionTimeout: 8000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: () => {}
    })

    const transport = {
        send(snapshot) {
            if (closed || !client.connected) return false
            try {
                client.publish({ destination: '/app/code.update', body: JSON.stringify(nativeMentorSnapshot(snapshot)) })
                return true
            } catch {
                onError?.({ code: 'MENTOR_UNAVAILABLE' })
                return false
            }
        },
        disconnect() {
            closed = true
            client.connectHeaders = {}
            void client.deactivate()
        }
    }

    client.onConnect = () => {
        if (closed) return
        client.subscribe('/user/queue/feedback', (message) => {
            if (closed) return
            try {
                if (message.body.length > 131072) throw new Error()
                const payload = JSON.parse(message.body)
                if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error()
                if (typeof payload.code === 'string') onError?.({ code: payload.code })
                else onFeedback?.(payload)
            } catch {
                onError?.({ code: 'MENTOR_UNAVAILABLE' })
            }
        })
        onConnect?.()
    }
    client.onStompError = () => {
        if (closed) return
        transport.disconnect()
        onError?.({ code: 'REQUEST_REJECTED' })
    }
    client.onWebSocketError = () => {
        if (!closed) onError?.({ code: 'MENTOR_UNAVAILABLE' })
    }
    client.onWebSocketClose = () => {
        if (!closed) onDisconnect?.()
    }
    client.activate()
    return transport
}
