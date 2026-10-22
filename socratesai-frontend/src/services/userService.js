import api from './api'

export async function fetchMyProfile() {
    const { data } = await api.get('/api/users/me')
    return data
}

export async function fetchMyProgress(config = {}) {
    const { data } = await api.get('/api/users/me/progress', config)
    return data
}

export async function updateMySettings(payload, config = {}) {
    const { data } = await api.put('/api/users/me/settings', payload, config)
    return data
}
