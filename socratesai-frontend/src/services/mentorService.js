import api from './api'

/** Explicit allowlist: identity and analyzer evidence always belong to the server. */
export function nativeMentorSnapshot(value) {
    if (!value) return null
    const { problemId, problemVersionId, languageKey, editorSessionId, codeRevision, code } = value
    return { problemId, problemVersionId, languageKey, editorSessionId, codeRevision, code }
}

export async function requestNativeMentor(snapshot, { signal } = {}) {
    const { data } = await api.post('/api/mentor/native-feedback', nativeMentorSnapshot(snapshot), { signal, timeout: 20000 })
    return data
}

export async function updateInteractionResult(interactionId, payload, { signal } = {}) {
    const { data } = await api.post(`/api/interactions/${encodeURIComponent(interactionId)}/result`, payload, { signal })
    return data
}
