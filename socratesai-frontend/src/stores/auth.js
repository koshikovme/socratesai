import { defineStore, getActivePinia } from 'pinia'
import { clearSessionResources } from '../services/sessionLifecycle'

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem('token') || '',
        userId: localStorage.getItem('userId') || '',
        email: localStorage.getItem('email') || '',
        fullName: localStorage.getItem('fullName') || '',
        role: localStorage.getItem('role') || ''
    }),
    actions: {
        refreshProfile(profile) {
            // A delayed response must never restore a logged-out user or replace another account.
            if (!this.token || String(profile.id) !== this.userId) return false
            if (profile.authentication && String(profile.authentication.userId) !== this.userId) return false
            this.email = profile.email
            this.fullName = profile.fullName
            this.role = profile.role
            if (profile.authentication) this.token = profile.authentication.token
            for (const key of ['token', 'email', 'fullName', 'role']) localStorage.setItem(key, this[key])
            return true
        },
        setAuth(data) {
            this.logout()
            this.token = data.token
            this.userId = String(data.userId)
            this.email = data.email
            this.fullName = data.fullName
            this.role = data.role

            localStorage.setItem('token', this.token)
            localStorage.setItem('userId', this.userId)
            localStorage.setItem('email', this.email)
            localStorage.setItem('fullName', this.fullName)
            localStorage.setItem('role', this.role)
        },
        logout() {
            clearSessionResources()
            // Reset every non-auth Pinia store, including transient editor buffers.
            getActivePinia()?._s.forEach(store => {
                if (store.$id !== 'auth') store.$reset()
            })
            this.token = ''
            this.userId = ''
            this.email = ''
            this.fullName = ''
            this.role = ''
            localStorage.removeItem('token')
            localStorage.removeItem('userId')
            localStorage.removeItem('email')
            localStorage.removeItem('fullName')
            localStorage.removeItem('role')
            sessionStorage.removeItem('leetcode_tasks_cache')
        }
    }
})
