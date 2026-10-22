import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { createHash } from 'node:crypto'
import { effectScope, nextTick, ref } from 'vue'
const { createSocket, request } = vi.hoisted(() => ({ createSocket: vi.fn(), request: vi.fn() }))
vi.mock('../../services/websocketService', () => ({ createMentorSocket: createSocket }))
vi.mock('../../services/mentorService', async (original) => ({ ...(await original()), requestNativeMentor: request }))
import { sourceCodeHash, useNativeMentor } from '../useNativeMentor'
import { clearSessionResources } from '../../services/sessionLifecycle'

const hash = code => createHash('sha256').update(code).digest('hex')
const initial = () => ({ problemId: 1, problemVersionId: 2, languageKey: 'python3', editorSessionId: 'editor-one', codeRevision: 0, code: 'print(1)' })
const reply = value => ({ ...value, code: undefined, codeHash: hash(value.code), policyMode: 'RULE', feedbackText: 'Check your condition.' })
let hooks, send, disconnect, scope
function setup() {
    const snapshot = ref(initial()), token = ref('jwt')
    scope = effectScope()
    const advanceRevision = vi.fn(() => { snapshot.value.codeRevision++ })
    const mentor = scope.run(() => useNativeMentor({ snapshot, token, advanceRevision }))
    return { snapshot, token, mentor, advanceRevision }
}
async function settle() { await nextTick(); await vi.advanceTimersByTimeAsync(0) }
async function connected() { hooks.onConnect(); await vi.advanceTimersByTimeAsync(350) }

describe('native mentor snapshot orchestration', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.stubGlobal('crypto', { subtle: { digest: vi.fn(async (_, value) => createHash('sha256').update(value).digest()) } })
        send = vi.fn(() => true); disconnect = vi.fn()
        createSocket.mockImplementation(value => { hooks = value; return { send, disconnect } })
        request.mockReset()
    })
    afterEach(() => {
        scope?.stop(); clearSessionResources(); vi.useRealTimers(); vi.unstubAllGlobals()
    })
    test('hashes full UTF-8 buffer exactly and debounces rapid edits into one full snapshot', async () => {
        expect(await sourceCodeHash('print("ж")\r\n')).toBe(hash('print("ж")\r\n'))
        const { snapshot, mentor } = setup()
        hooks.onConnect()
        snapshot.value = { ...snapshot.value, codeRevision: 1, code: 'p' }
        await nextTick()
        await vi.advanceTimersByTimeAsync(200)
        snapshot.value = { ...snapshot.value, codeRevision: 2, code: 'print(2)' }
        await nextTick()
        await vi.advanceTimersByTimeAsync(350)
        expect(send).toHaveBeenCalledTimes(1)
        expect(send).toHaveBeenCalledWith(snapshot.value)
        expect(mentor.status.value).toBe('connected')
    })
    test('accepts matching mode and evidence only after context, revision and hash match', async () => {
        const { snapshot, mentor } = setup()
        await connected()
        const value = reply(snapshot.value)
        hooks.onFeedback(value)
        await settle()
        expect(mentor.feedback.value).toEqual({ ...value, transport: 'WS' })
        expect(mentor.pending.value).toBe(false)
        snapshot.value.codeRevision++
        await nextTick()
        expect(mentor.feedback.value).toBeNull()
    })
    test.each([
        { codeRevision: -1 }, { editorSessionId: 'another-tab' }, { languageKey: 'java' },
        { problemId: 999 }, { problemVersionId: 999 }, { codeHash: 'wrong-hash' }
    ])('discards stale/cross-context feedback %j', async mismatch => {
        const { snapshot, mentor } = setup()
        await connected()
        hooks.onFeedback({ ...reply(snapshot.value), ...mismatch })
        await settle()
        expect(mentor.feedback.value).toBeNull()
    })
    test('rechecks latest buffer after asynchronous digest completion', async () => {
        const { snapshot, mentor } = setup()
        await connected()
        let completeDigest
        crypto.subtle.digest.mockImplementationOnce(() => new Promise(resolve => { completeDigest = resolve }))
        const oldCode = snapshot.value.code
        hooks.onFeedback(reply(snapshot.value))
        snapshot.value = { ...snapshot.value, codeRevision: 1, code: 'newer-code' }
        await nextTick()
        completeDigest(createHash('sha256').update(oldCode).digest())
        await settle()
        expect(mentor.feedback.value).toBeNull()
    })
    test('uses native HTTP recovery with a higher revision after failed WS and aborts on logout', async () => {
        const { snapshot, mentor, advanceRevision } = setup()
        await connected()
        request.mockImplementation(() => new Promise(() => {}))
        hooks.onError({ code: 'REQUEST_REJECTED' })
        await vi.advanceTimersByTimeAsync(1350)
        expect(mentor.status.value).toBe('http')
        expect(advanceRevision).toHaveBeenCalledOnce()
        expect(request).toHaveBeenCalledWith(expect.objectContaining({ codeRevision: 1, code: 'print(1)' }), { signal: expect.any(AbortSignal) })
        const signal = request.mock.calls[0][1].signal
        const lateResponse = reply(snapshot.value)
        clearSessionResources()
        expect(signal.aborted).toBe(true)
        expect(disconnect).toHaveBeenCalledOnce()
        hooks.onFeedback(lateResponse)
        await vi.advanceTimersByTimeAsync(30000)
        expect(mentor.feedback.value).toBeNull()
        expect(mentor.status.value).toBe('offline')
        expect(request).toHaveBeenCalledOnce()
    })
    test('uses HTTP fallback on initial connection timeout and accepts correlated result', async () => {
        const { mentor } = setup()
        request.mockImplementation(async value => reply(value))
        await vi.advanceTimersByTimeAsync(9350)
        expect(request).toHaveBeenCalledOnce()
        expect(mentor.status.value).toBe('http')
        expect(mentor.feedback.value?.policyMode).toBe('RULE')
        expect(mentor.pending.value).toBe(false)
    })
    test('reconnect sends latest buffer with increasing revision', async () => {
        const { snapshot, advanceRevision } = setup()
        await connected()
        hooks.onDisconnect()
        hooks.onConnect()
        await vi.advanceTimersByTimeAsync(350)
        expect(advanceRevision).toHaveBeenCalledOnce()
        expect(snapshot.value.codeRevision).toBe(1)
        expect(send.mock.calls.at(-1)[0].codeRevision).toBe(1)
        expect(request).not.toHaveBeenCalled()
    })
    test('switching back to an earlier language advances its previously sent revision', async () => {
        const { snapshot, advanceRevision } = setup()
        await connected()
        const python = { ...snapshot.value }
        snapshot.value = { ...initial(), languageKey: 'java', editorSessionId: 'java-editor' }
        await nextTick()
        await vi.advanceTimersByTimeAsync(350)
        snapshot.value = python
        await nextTick()
        await vi.advanceTimersByTimeAsync(350)
        expect(advanceRevision).toHaveBeenCalledOnce()
        expect(send.mock.calls.at(-1)[0]).toMatchObject({ editorSessionId: 'editor-one', languageKey: 'python3', codeRevision: 1 })
    })
    test('recycles socket across problem navigation and rejects preceding transport callbacks', async () => {
        const { snapshot, mentor } = setup()
        await connected()
        const oldHooks = hooks
        const oldReply = reply(snapshot.value)
        snapshot.value = null
        await nextTick()
        snapshot.value = { ...initial(), problemId: 7, problemVersionId: 8, editorSessionId: 'new-problem' }
        await nextTick()
        expect(disconnect).toHaveBeenCalledOnce()
        expect(createSocket).toHaveBeenCalledTimes(2)
        oldHooks.onFeedback(oldReply)
        oldHooks.onConnect()
        expect(mentor.status.value).toBe('connecting')
        await connected()
        expect(send.mock.calls.at(-1)[0]).toMatchObject({ problemId: 7, problemVersionId: 8 })
        expect(mentor.feedback.value).toBeNull()
    })
    test('recovers from a lost reply with one bounded native HTTP attempt', async () => {
        const { mentor } = setup()
        await connected()
        request.mockRejectedValue({ response: { status: 503, data: { code: 'MENTOR_UNAVAILABLE' } } })
        await vi.advanceTimersByTimeAsync(20350)
        expect(request).toHaveBeenCalledOnce()
        expect(mentor.pending.value).toBe(false)
        expect(mentor.error.value).toBe('Mentor feedback is temporarily unavailable.')
        await vi.advanceTimersByTimeAsync(60000)
        expect(request).toHaveBeenCalledOnce()
    })
    test('drops pending HTTP when editing and masks backend exception bodies', async () => {
        const { snapshot, mentor } = setup()
        request.mockImplementationOnce(() => new Promise(() => {}))
        await vi.advanceTimersByTimeAsync(9350)
        const signal = request.mock.calls[0][1].signal
        request.mockRejectedValueOnce({ response: { status: 500, data: { message: 'secret source/database/token' } } })
        snapshot.value = { ...snapshot.value, codeRevision: 1, code: 'new-code' }
        await nextTick()
        expect(signal.aborted).toBe(true)
        await vi.advanceTimersByTimeAsync(350)
        expect(mentor.error.value).toBe('The request could not be completed. Please try again.')
        expect(mentor.pending.value).toBe(false)
    })
    test('rejects mismatched HTTP result without leaving an endless loading state', async () => {
        const { mentor } = setup()
        request.mockImplementation(async value => ({ ...reply(value), editorSessionId: 'other' }))
        await vi.advanceTimersByTimeAsync(9350)
        expect(mentor.feedback.value).toBeNull()
        expect(mentor.pending.value).toBe(false)
        expect(mentor.error.value).toContain('could not be verified')
    })
    test('token loss closes socket and cancels debounce without sending source', async () => {
        const { token, mentor } = setup()
        hooks.onConnect()
        token.value = null
        await nextTick()
        await vi.advanceTimersByTimeAsync(30000)
        expect(disconnect).toHaveBeenCalledOnce()
        expect(send).not.toHaveBeenCalled()
        expect(request).not.toHaveBeenCalled()
        expect(mentor.status.value).toBe('offline')
    })
})
