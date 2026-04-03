import api from './api'

export async function login(payload) {
    const { data } = await api.post('/api/auth/login', payload)
    return data
}

export async function register(payload) {
    const { data } = await api.post('/api/auth/register', payload)
    return data
}