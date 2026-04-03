import api from './api'

export async function fetchPublicTasks() {
    const { data } = await api.get('/api/tasks/public')
    return data
}

export async function fetchTaskById(id) {
    const { data } = await api.get(`/api/tasks/public/${id}`)
    return data
}

export async function createTask(payload) {
    const { data } = await api.post('/api/tasks', payload)
    return data
}