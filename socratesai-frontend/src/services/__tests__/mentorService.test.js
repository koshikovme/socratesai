import { describe, expect, test, vi } from 'vitest'
const { post } = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('../api', () => ({ default: { post } }))
import { nativeMentorSnapshot, requestNativeMentor, updateInteractionResult } from '../mentorService'

const snapshot = { problemId: 1, problemVersionId: 2, languageKey: 'python3', editorSessionId: 'editor', codeRevision: 4, code: 'print(1)' }
describe('native mentor service', () => {
    test('allows native snapshot fields only, never caller identity or evidence', async () => {
        const payload = { ...snapshot, studentId: 999, attemptNo: 88, analyzerResult: { success: true } }
        post.mockResolvedValueOnce({ data: { action: 'CONCEPTUAL_HINT' } })
        const signal = new AbortController().signal
        expect(await requestNativeMentor(payload, { signal })).toEqual({ action: 'CONCEPTUAL_HINT' })
        expect(post).toHaveBeenCalledWith('/api/mentor/native-feedback', snapshot, { signal, timeout: 20000 })
        expect(nativeMentorSnapshot(null)).toBeNull()
    })
    test('preserves interaction outcome endpoint and supports cancellation', async () => {
        const payload = { resolvedAfterFeedback: true, fixedAfterMs: 800 }
        const signal = new AbortController().signal
        post.mockResolvedValueOnce({ data: { message: 'ok' } })
        expect(await updateInteractionResult('abc-123', payload, { signal })).toEqual({ message: 'ok' })
        expect(post).toHaveBeenCalledWith('/api/interactions/abc-123/result', payload, { signal })
    })
})
