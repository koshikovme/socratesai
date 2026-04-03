import axios from 'axios'
import { useAuthStore } from '../stores/auth'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
export const WEBSOCKET_BASE_URL = (import.meta.env.VITE_WS_BASE_URL || '').replace(/\/$/, '')

const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: 15000
})

api.interceptors.request.use((config) => {
    const auth = useAuthStore()
    if (auth.token) {
        config.headers.Authorization = `Bearer ${auth.token}`
    }
    return config
})

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error?.response?.status === 401) {
            const auth = useAuthStore()
            auth.logout()
        }
        return Promise.reject(error)
    }
)

export default api
