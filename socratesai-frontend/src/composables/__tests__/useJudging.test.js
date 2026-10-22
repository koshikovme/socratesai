import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { useJudging } from '../useJudging'
import { clearSessionResources } from '../../services/sessionLifecycle'
import api from '../../services/api'
vi.mock('../../services/api', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
const snapshot = { problemId: 1, problemVersionId: 2, languageKey: 'python3', editorSessionId: 'editor', codeRevision: 3, code: 'print(1)' }
let wrapper, state
function harness(options) { wrapper = mount(defineComponent({ setup() { state = useJudging(options); return () => null } })) }
beforeEach(() => { vi.useFakeTimers(); vi.resetAllMocks() })
afterEach(() => { wrapper?.unmount(); vi.useRealTimers() })
describe('durable asynchronous judging UI', () => {
  test('does not count recovery as terminal and never duplicates POST while polling', async () => {
    api.post.mockResolvedValue({ data: { id: 'submission', status: 'QUEUED' } })
    api.get.mockResolvedValueOnce({ data: { submission: { id: 'submission', status: 'SYSTEM_ERROR' }, retryScheduled: true } })
      .mockResolvedValueOnce({ data: { submission: { id: 'submission', status: 'FINISHED', verdict: 'ACCEPTED', attemptNumber: 1 }, retryScheduled: false } })
    const onFinished = vi.fn()
    harness({ onFinished })
    const promise = state.start('submission', snapshot, 5)
    await flushPromises()
    expect(state.busy.value).toBe(true)
    expect(onFinished).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(500)
    await promise
    expect(api.post).toHaveBeenCalledTimes(1)
    expect(api.post.mock.calls[0][1]).toMatchObject({ problemVersionId: 2, collectionId: 5, sourceCode: 'print(1)' })
    expect(onFinished).toHaveBeenCalledTimes(1)
    expect(state.phase.value).toBe('complete')
  })
  test('reuses exact key and source after ambiguous POST and ignores edits for retry', async () => {
    api.post.mockRejectedValueOnce({ code: 'ERR_NETWORK' }).mockResolvedValueOnce({ data: { id: 'same', status: 'FINISHED', verdict: 'WRONG_ANSWER' } })
    harness()
    await state.start('submission', snapshot)
    const first = api.post.mock.calls[0][1]
    expect(state.phase.value).toBe('paused')
    await state.start('submission', { ...snapshot, code: 'different' })
    expect(api.post).toHaveBeenCalledTimes(1)
    await state.retry()
    expect(api.post.mock.calls[1][1]).toEqual(first)
    expect(first.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/)
    expect(state.phase.value).toBe('complete')
  })
  test('resumes GET only after lost polling response; sample has no collection or client attempt', async () => {
    api.post.mockResolvedValue({ data: { id: 'run', status: 'RUNNING' } })
    api.get.mockRejectedValueOnce({ code: 'ERR_NETWORK' }).mockResolvedValueOnce({ data: { id: 'run', status: 'SYSTEM_ERROR', retryScheduled: false } })
    harness()
    await state.start('sample', snapshot, 9)
    expect(state.phase.value).toBe('paused')
    await state.retry()
    expect(api.post).toHaveBeenCalledTimes(1)
    expect(api.post.mock.calls[0][1]).not.toHaveProperty('collectionId')
    expect(api.post.mock.calls[0][1]).not.toHaveProperty('attemptNumber')
    expect(state.phase.value).toBe('complete')
  })
  test('logout aborts and discards source and late responses', async () => {
    let resolve
    api.post.mockImplementation(() => new Promise(done => { resolve = done }))
    harness()
    const promise = state.start('submission', snapshot)
    const config = api.post.mock.calls[0][2]
    clearSessionResources()
    resolve({ data: { id: 'late', status: 'FINISHED', verdict: 'ACCEPTED' } })
    await promise
    expect(config.signal.aborted).toBe(true)
    expect(state.operation.value).toBeNull()
    expect(state.result.value).toBeNull()
    expect(state.phase.value).toBe('idle')
  })
  test('expired sample result permits a fresh Run without retrying an expired ID forever', async () => {
    api.post.mockResolvedValue({ data: { id: 'expired', status: 'QUEUED' } })
    api.get.mockRejectedValue({ response: { status: 404, data: { code: 'NOT_FOUND' } } })
    harness()
    await state.start('sample', snapshot)
    expect(state.phase.value).toBe('unavailable')
    expect(state.busy.value).toBe(false)
    expect(state.result.value).toBeNull()
    api.post.mockResolvedValue({ data: { id: 'fresh', status: 'FINISHED', verdict: 'ACCEPTED' } })
    await state.start('sample', snapshot)
    expect(api.post).toHaveBeenCalledTimes(2)
    expect(api.post.mock.calls[0][1].idempotencyKey).not.toBe(api.post.mock.calls[1][1].idempotencyKey)
  })

})
