import { beforeEach, describe, expect, test } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../auth'

describe('auth store', () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    test('setAuth persists token and profile fields', () => {
        const store = useAuthStore()

        store.setAuth({
            token: 'jwt-token',
            userId: 42,
            email: 'student@example.com',
            fullName: 'Student One',
            role: 'STUDENT'
        })

        expect(store.token).toBe('jwt-token')
        expect(localStorage.getItem('token')).toBe('jwt-token')
        expect(localStorage.getItem('userId')).toBe('42')
        expect(localStorage.getItem('role')).toBe('STUDENT')
    })

    test('logout clears reactive state and local storage', () => {
        const store = useAuthStore()
        localStorage.setItem('token', 'jwt-token')
        store.token = 'jwt-token'
        store.email = 'student@example.com'

        store.logout()

        expect(store.token).toBe('')
        expect(store.email).toBe('')
        expect(localStorage.getItem('token')).toBeNull()
    })
})
