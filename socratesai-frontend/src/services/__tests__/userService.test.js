import { describe, expect, test, vi } from 'vitest'

const { get, put } = vi.hoisted(() => ({
    get: vi.fn(),
    put: vi.fn()
}))

vi.mock('../api', () => ({
    default: {
        get,
        put
    }
}))

import { fetchMyProfile, fetchMyProgress, updateMySettings } from '../userService'

describe('userService', () => {
    test('fetchMyProgress calls the progress endpoint', async () => {
        get.mockResolvedValueOnce({ data: { summary: { solvedTasks: 1 } } })

        const result = await fetchMyProgress()

        expect(get).toHaveBeenCalledWith('/api/users/me/progress')
        expect(result.summary.solvedTasks).toBe(1)
    })

    test('fetchMyProfile calls the profile endpoint', async () => {
        get.mockResolvedValueOnce({ data: { fullName: 'Student One' } })

        const result = await fetchMyProfile()

        expect(get).toHaveBeenCalledWith('/api/users/me')
        expect(result.fullName).toBe('Student One')
    })

    test('updateMySettings persists settings', async () => {
        const payload = { darkMode: true }
        put.mockResolvedValueOnce({ data: { darkMode: true } })

        const result = await updateMySettings(payload)

        expect(put).toHaveBeenCalledWith('/api/users/me/settings', payload)
        expect(result.darkMode).toBe(true)
    })
})
