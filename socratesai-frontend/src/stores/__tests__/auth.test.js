import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../auth'
import { useEditorStore } from '../editor'
import { registerSessionCleanup } from '../../services/sessionLifecycle'

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
        localStorage.setItem('socrates-theme', 'dark')
        store.token = 'jwt-token'
        store.email = 'student@example.com'

        store.logout()

        expect(store.token).toBe('')
        expect(store.email).toBe('')
        expect(localStorage.getItem('token')).toBeNull()
        expect(localStorage.getItem('socrates-theme')).toBe('dark')
    })

    test('profile refresh rotates same-account credentials without clearing private buffers', () => {
        const auth = useAuthStore()
        auth.setAuth({ token: 'old-token', userId: 42, email: 'old@example.test', fullName: 'Old', role: 'STUDENT' })
        const editor = useEditorStore()
        const context = editor.open({ problemId: 1, problemVersionId: 2, languageKey: 'java' }, 'private-unsaved-buffer')
        const cleared = vi.fn(), unregister = registerSessionCleanup(cleared)
        expect(auth.refreshProfile({ id: 42, email: 'new@example.test', fullName: 'New', role: 'STUDENT',
            authentication: { userId: 42, token: 'rotated-token' } })).toBe(true)
        expect(auth.token).toBe('rotated-token')
        expect(localStorage.getItem('email')).toBe('new@example.test')
        expect(editor.open({ problemId: 1, problemVersionId: 2, languageKey: 'java' }, '').code).toBe(context.code)
        expect(cleared).not.toHaveBeenCalled(); unregister()
    })

    test('late or foreign profile responses cannot restore or switch identity', () => {
        const auth = useAuthStore()
        const profile = { id: 42, email: 'a@example.test', fullName: 'Name', role: 'STUDENT' }
        expect(auth.refreshProfile(profile)).toBe(false)
        auth.setAuth({ token: 'session', userId: 43, email: 'other@example.test', fullName: 'Other', role: 'STUDENT' })
        expect(auth.refreshProfile(profile)).toBe(false)
        expect(auth.refreshProfile({ ...profile, id: 43, authentication: { userId: 42, token: 'foreign' } })).toBe(false)
        expect(auth.token).toBe('session')
        expect(auth.fullName).toBe('Other')
    })
})
