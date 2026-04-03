import api from './api'

export async function requestMentorAnalysis(payload) {
    const { data } = await api.post('/api/mentor/analyze-feedback', payload)
    return data
}

export async function updateInteractionResult(interactionId, payload) {
    const { data } = await api.post(`/api/interactions/${interactionId}/result`, payload)
    return data
}
