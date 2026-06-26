import api from './api'

export async function fetchMyProfile() {
    const { data } = await api.get('/api/users/me')
    return data
}

export async function fetchMyProgress() {
    const { data } = await api.get('/api/users/me/progress')
    return data
}

export async function updateMySettings(payload) {
    const { data } = await api.put('/api/users/me/settings', payload)
    return data
}
