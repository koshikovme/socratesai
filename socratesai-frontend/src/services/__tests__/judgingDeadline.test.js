import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { pollJudging } from '../judgingService'
import api from '../api'
vi.mock('../api', () => ({ default: { get: vi.fn() } }))
beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())
test('pending status monitoring ends at its budget and cancellation releases scheduled work', async () => {
  api.get.mockResolvedValue({ data: { status: 'RUNNING' } })
  const controller = new AbortController()
  const onResult = vi.fn()
  const completed = pollJudging('sample', 'run', { signal: controller.signal, onResult, deadlineMs: 1100 })
  await vi.advanceTimersByTimeAsync(1100)
  expect(await completed).toBe(false)
  expect(api.get).toHaveBeenCalledTimes(2)
  expect(vi.getTimerCount()).toBe(0)
})
