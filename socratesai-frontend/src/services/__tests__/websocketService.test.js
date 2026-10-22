import { beforeEach, describe, expect, test, vi } from 'vitest'
const { sockJsFactory, clients, clientConstructor } = vi.hoisted(() => {
    const clients = []
    const sockJsFactory = vi.fn(function () { return {} })
    const clientConstructor = vi.fn().mockImplementation(function (config) {
        const client = {
            ...config, connected: false, subscribe: vi.fn(), publish: vi.fn(), deactivate: vi.fn(),
            activate() { this.webSocketFactory(); this.connected = true; this.onConnect?.() }
        }
        clients.push(client)
        return client
    })
    return { sockJsFactory, clients, clientConstructor }
})
vi.mock('@stomp/stompjs', () => ({ Client: clientConstructor }))
vi.mock('sockjs-client/dist/sockjs.min.js', () => ({ default: sockJsFactory }))
vi.mock('../api', () => ({ default: {}, WEBSOCKET_BASE_URL: 'http://localhost:8080' }))
import { createMentorSocket } from '../websocketService'

const snapshot = { problemId: 1, problemVersionId: 2, languageKey: 'python3', editorSessionId: 'editor', codeRevision: 4, code: 'print(1)' }
describe('native STOMP transport', () => {
    beforeEach(() => { clients.length = 0 })
    test('authenticates CONNECT header only and subscribes to private user queue', () => {
        const onFeedback = vi.fn()
        createMentorSocket({ token: 'secret-jwt', onFeedback })
        const client = clients[0]
        expect(sockJsFactory).toHaveBeenCalledWith('http://localhost:8080/ws')
        expect(client.connectHeaders).toEqual({ Authorization: 'Bearer secret-jwt' })
        expect(client.subscribe).toHaveBeenCalledWith('/user/queue/feedback', expect.any(Function))
        client.subscribe.mock.calls[0][1]({ body: '{"policyMode":"RULE"}' })
        expect(onFeedback).toHaveBeenCalledWith({ policyMode: 'RULE' })
    })
    test('sends full allowed snapshot without identity and drops sends after disconnect', () => {
        const socket = createMentorSocket({ token: 'token' })
        expect(socket.send({ ...snapshot, studentId: 88 })).toBe(true)
        expect(clients[0].publish).toHaveBeenCalledWith({ destination: '/app/code.update', body: JSON.stringify(snapshot) })
        socket.disconnect()
        expect(clients[0].connectHeaders).toEqual({})
        expect(clients[0].deactivate).toHaveBeenCalledOnce()
        expect(socket.send(snapshot)).toBe(false)
    })
    test('sanitizes malformed messages, safe envelopes and ERROR frames without logging bodies', () => {
        const onError = vi.fn(), onFeedback = vi.fn()
        const log = vi.spyOn(console, 'error').mockImplementation(() => {})
        createMentorSocket({ token: 'token', onError, onFeedback })
        const client = clients[0], handler = client.subscribe.mock.calls[0][1]
        handler({ body: 'private-source-not-json' })
        expect(onError).toHaveBeenLastCalledWith({ code: 'MENTOR_UNAVAILABLE' })
        handler({ body: '{"code":"CAPACITY_EXCEEDED","message":"secret database detail"}' })
        expect(onError).toHaveBeenLastCalledWith({ code: 'CAPACITY_EXCEEDED' })
        client.onStompError({ body: 'token and private source' })
        expect(onError).toHaveBeenLastCalledWith({ code: 'REQUEST_REJECTED' })
        expect(client.deactivate).toHaveBeenCalledOnce()
        expect(onFeedback).not.toHaveBeenCalled()
        expect(log).not.toHaveBeenCalled()
        log.mockRestore()
    })
    test('isolates simultaneous transports and suppresses callbacks after disposal', () => {
        const firstFeedback = vi.fn(), secondFeedback = vi.fn()
        const first = createMentorSocket({ token: 'first', onFeedback: firstFeedback })
        createMentorSocket({ token: 'second', onFeedback: secondFeedback })
        first.disconnect()
        clients[0].subscribe.mock.calls[0][1]({ body: '{"action":"NO_FEEDBACK"}' })
        clients[1].subscribe.mock.calls[0][1]({ body: '{"action":"NO_FEEDBACK"}' })
        expect(firstFeedback).not.toHaveBeenCalled()
        expect(secondFeedback).toHaveBeenCalledOnce()
        expect(clients[1].deactivate).not.toHaveBeenCalled()
    })
})
