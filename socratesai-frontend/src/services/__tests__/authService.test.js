import { describe, expect, test, vi } from 'vitest'

const { post } = vi.hoisted(() => ({
    post: vi.fn()
}))

vi.mock('../api', () => ({
    default: {
        post
    }
}))

import { login, register } from '../authService'

describe('authService', () => {
    test('login posts credentials to auth endpoint', async () => {
        const payload = { email: 'student@example.com', password: 'secret123' }
        post.mockResolvedValueOnce({ data: { token: 'jwt-token' } })

        const result = await login(payload)

        expect(post).toHaveBeenCalledWith('/api/auth/login', payload)
        expect(result).toEqual({ token: 'jwt-token' })
    })

    test('register posts payload to registration endpoint', async () => {
        const payload = { email: 'teacher@example.com', password: 'secret123', fullName: 'Teacher' }
        post.mockResolvedValueOnce({ data: { userId: 10 } })

        const result = await register(payload)

        expect(post).toHaveBeenCalledWith('/api/auth/register', payload)
        expect(result).toEqual({ userId: 10 })
    })
})
