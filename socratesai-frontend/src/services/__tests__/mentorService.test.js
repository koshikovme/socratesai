import { describe, expect, test, vi } from 'vitest'

const { post } = vi.hoisted(() => ({
    post: vi.fn()
}))

vi.mock('../api', () => ({
    default: {
        post
    }
}))

import { requestMentorAnalysis, updateInteractionResult } from '../mentorService'

describe('mentorService', () => {
    test('requestMentorAnalysis posts to analyze endpoint and returns data', async () => {
        const payload = { code: 'print(1)', taskId: 5 }
        post.mockResolvedValueOnce({ data: { action: 'CONCEPTUAL_HINT' } })

        const result = await requestMentorAnalysis(payload)

        expect(post).toHaveBeenCalledWith('/api/mentor/analyze-feedback', payload)
        expect(result).toEqual({ action: 'CONCEPTUAL_HINT' })
    })

    test('updateInteractionResult posts to interaction result endpoint', async () => {
        const payload = { resolvedAfterFeedback: true, fixedAfterMs: 800 }
        post.mockResolvedValueOnce({ data: { message: 'ok' } })

        const result = await updateInteractionResult('abc-123', payload)

        expect(post).toHaveBeenCalledWith('/api/interactions/abc-123/result', payload)
        expect(result).toEqual({ message: 'ok' })
    })
})
