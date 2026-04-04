import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

const { sockJsFactory, subscribe, publish, deactivate, clientConstructor } = vi.hoisted(() => {
    const sockJsFactory = vi.fn(() => ({ tag: 'socket' }))
    const subscribe = vi.fn()
    const publish = vi.fn()
    const deactivate = vi.fn()
    const clientConstructor = vi.fn().mockImplementation((config) => {
        return {
            ...config,
            connected: false,
            subscribe,
            publish,
            deactivate,
            activate() {
                this.webSocketFactory?.()
                this.connected = true
                this.onConnect?.()
            }
        }
    })

    return { sockJsFactory, subscribe, publish, deactivate, clientConstructor }
})

vi.mock('@stomp/stompjs', () => ({
    Client: clientConstructor
}))

vi.mock('sockjs-client/dist/sockjs.min.js', () => ({
    default: sockJsFactory
}))

vi.mock('../api', () => ({
    WEBSOCKET_BASE_URL: 'http://localhost:8080'
}))

import { createMentorSocket, disconnectSocket, sendCodeUpdate } from '../websocketService'

describe('websocketService', () => {
    beforeEach(() => {
        subscribe.mockReset()
        publish.mockReset()
        deactivate.mockReset()
        sockJsFactory.mockClear()
        clientConstructor.mockClear()
    })

    afterEach(() => {
        disconnectSocket()
    })

    test('createMentorSocket subscribes to feedback queue and forwards messages', () => {
        const onFeedback = vi.fn()
        let subscriptionHandler
        subscribe.mockImplementation((destination, handler) => {
            subscriptionHandler = handler
        })

        createMentorSocket({ token: 'jwt-token', onFeedback })
        subscriptionHandler({ body: '{"action":"CODE_HIGHLIGHT"}' })

        expect(clientConstructor).toHaveBeenCalled()
        expect(sockJsFactory).toHaveBeenCalledWith('http://localhost:8080/ws')
        expect(subscribe).toHaveBeenCalledWith('/user/queue/feedback', expect.any(Function))
        expect(onFeedback).toHaveBeenCalledWith({ action: 'CODE_HIGHLIGHT' })
    })

    test('sendCodeUpdate publishes payload when client is connected', () => {
        createMentorSocket({ token: 'jwt-token' })

        sendCodeUpdate({ code: 'print(1)' })

        expect(publish).toHaveBeenCalledWith({
            destination: '/app/code.update',
            body: JSON.stringify({ code: 'print(1)' })
        })
    })
})
