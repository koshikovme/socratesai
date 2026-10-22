import { onScopeDispose, ref, toValue, watch } from 'vue'
import { createMentorSocket } from '../services/websocketService'
import { nativeMentorSnapshot, requestNativeMentor } from '../services/mentorService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'

const DEBOUNCE_MS = 350
const RESPONSE_TIMEOUT_MS = 20000
const MAX_REVISION_ENTRIES = 64
const contextFields = ['problemId', 'problemVersionId', 'languageKey', 'editorSessionId', 'codeRevision']
const sameContext = (left, right) => left && right && contextFields.every((field) => left[field] === right[field])

export async function sourceCodeHash(code) {
    const bytes = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(code))
    return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/** Full snapshots scoped to a mounted workspace. Recovery shares one revision stream. */
export function useNativeMentor({ snapshot, token, advanceRevision }) {
    const feedback = ref(null)
    const status = ref('offline')
    const pending = ref(false)
    const error = ref('')
    let socket = null
    let debounceTimer, fallbackTimer, responseTimer
    let requestController = null
    const attemptedRevisions = new Map()
    let stopped = false
    let connectionGeneration = 0
    let problemContext = null
    const current = () => nativeMentorSnapshot(toValue(snapshot))

    function cancelRequest() {
        requestController?.abort()
        requestController = null
        clearTimeout(responseTimer)
    }

    async function receive(value, transport) {
        const expected = current()
        if (stopped || !sameContext(value, expected) || typeof value.codeHash !== 'string') return
        try {
            const hash = await sourceCodeHash(expected.code)
            if (stopped || !sameContext(expected, current()) || current().code !== expected.code || hash !== value.codeHash) return
            feedback.value = { ...value, transport }
            pending.value = false
            error.value = ''
            clearTimeout(responseTimer)
            return true
        } catch {
            pending.value = false
            error.value = 'Secure mentor response verification requires HTTPS or localhost.'
        }
    }

    function schedule() {
        clearTimeout(debounceTimer)
        if (!stopped && current() && toValue(token)) debounceTimer = setTimeout(dispatch, DEBOUNCE_MS)
    }

    function refresh() {
        if (stopped || !current() || !toValue(token)) return
        advanceRevision()
        schedule()
    }

    function recover() {
        const value = current()
        if (value && attemptedRevisions.get(value.editorSessionId) === value.codeRevision) refresh()
        else schedule()
    }

    function fallback() {
        if (stopped || !toValue(token)) return
        status.value = 'http'
        recover()
    }

    async function dispatch() {
        const value = current()
        if (stopped || !value || !toValue(token) || status.value === 'connecting' || status.value === 'offline') return
        if (attemptedRevisions.get(value.editorSessionId) === value.codeRevision) return
        attemptedRevisions.delete(value.editorSessionId)
        attemptedRevisions.set(value.editorSessionId, value.codeRevision)
        if (attemptedRevisions.size > MAX_REVISION_ENTRIES) attemptedRevisions.delete(attemptedRevisions.keys().next().value)
        cancelRequest()
        pending.value = true
        error.value = ''
        if (status.value === 'connected' && socket?.send(value)) {
            responseTimer = setTimeout(fallback, RESPONSE_TIMEOUT_MS)
            return
        }
        status.value = 'http'
        const controller = new AbortController()
        requestController = controller
        try {
            const result = await requestNativeMentor(value, { signal: controller.signal })
            if (!controller.signal.aborted) {
                const accepted = await receive(result, 'REST')
                if (!accepted && !stopped && sameContext(value, current())) {
                    pending.value = false
                    error.value ||= 'The mentor response could not be verified. Try again.'
                }
            }
        } catch (failure) {
            if (!controller.signal.aborted && !stopped && sameContext(value, current())) {
                error.value = apiErrorMessage(failure)
                pending.value = false
            }
        } finally {
            if (requestController === controller) requestController = null
        }
    }

    function disconnect() {
        connectionGeneration++
        clearTimeout(debounceTimer)
        clearTimeout(fallbackTimer)
        cancelRequest()
        socket?.disconnect()
        socket = null
        pending.value = false
    }

    const stopSnapshotWatch = watch(current, () => {
        feedback.value = null
        error.value = ''
        pending.value = false
        cancelRequest()
        recover()
    }, { deep: true })

    function connect(bearer) {
        disconnect()
        feedback.value = null
        if (!bearer || stopped) { status.value = 'offline'; return }
        status.value = 'connecting'
        const generation = connectionGeneration
        const active = () => !stopped && generation === connectionGeneration
        fallbackTimer = setTimeout(() => { if (active()) fallback() }, 9000)
        socket = createMentorSocket({
            token: bearer,
            onFeedback: (value) => { if (active()) void receive(value, 'WS') },
            onConnect: () => {
                if (!active()) return
                clearTimeout(fallbackTimer)
                status.value = 'connected'
                recover()
            },
            onDisconnect: () => {
                if (!active()) return
                status.value = 'offline'
                cancelRequest()
                pending.value = false
                clearTimeout(fallbackTimer)
                fallbackTimer = setTimeout(fallback, 1000)
            },
            onError: (failure) => {
                if (!active()) return
                error.value = apiErrorMessage({ response: { data: failure } })
                clearTimeout(fallbackTimer)
                fallbackTimer = setTimeout(fallback, 1000)
            }
        })
    }

    const stopTokenWatch = watch(() => toValue(token), connect, { immediate: true })
    const stopContextWatch = watch(() => {
        const value = current()
        return value ? `${value.problemId}:${value.problemVersionId}` : null
    }, (context) => {
        if (!context) return
        // DISCONNECT lets the server release editors from the preceding problem.
        // HTTP-only editors still expire according to the backend's bounded TTL.
        if (problemContext && problemContext !== context && toValue(token)) connect(toValue(token))
        problemContext = context
    }, { immediate: true })

    let unregister = () => {}
    function stop() {
        if (stopped) return
        stopped = true
        stopSnapshotWatch()
        stopTokenWatch()
        stopContextWatch()
        disconnect()
        feedback.value = null
        error.value = ''
        status.value = 'offline'
        attemptedRevisions.clear()
        unregister()
    }
    unregister = registerSessionCleanup(stop)
    onScopeDispose(stop)
    return { feedback, status, pending, error, refresh, stop }
}
