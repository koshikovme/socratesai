import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api', () => ({ default: { get } }))
import { fetchProblems, fetchCollections, fetchProblem, fetchCollection, fetchCollectionProgress,
  fetchLanguages, fetchJudgedProgress, solvedProblemIds } from '../catalogService'

beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: {} }) })

describe('native catalog transport', () => {
  it('bounds pages and sizes and never forwards unsupported filters', async () => {
    const signal = new AbortController().signal
    await fetchProblems({ page: -1, size: 90, difficulty: 'EASY', title: 'ignored', signal })
    expect(get).toHaveBeenLastCalledWith('/api/problems', { params: { page: 0, size: 50 }, signal })
    await fetchCollections({ page: 10001, size: 0 })
    expect(get).toHaveBeenLastCalledWith('/api/collections', { params: { page: 10000, size: 1 }, signal: undefined })
    await fetchProblems({ page: NaN, size: '20' })
    expect(get).toHaveBeenLastCalledWith('/api/problems', { params: { page: 0, size: 20 }, signal: undefined })
  })

  it('loads native version-pinned details, collections, runtime catalog and judged progress', async () => {
    const signal = new AbortController().signal
    await fetchProblem(10, 22, { signal })
    expect(get).toHaveBeenLastCalledWith('/api/problems/10/versions/22', { signal })
    await fetchProblem(10)
    expect(get).toHaveBeenLastCalledWith('/api/problems/10', { signal: undefined })
    await fetchCollection(7, { signal })
    expect(get).toHaveBeenLastCalledWith('/api/collections/7', { signal })
    await fetchCollectionProgress(7, { signal })
    expect(get).toHaveBeenLastCalledWith('/api/collections/7/progress', { signal })
    await fetchLanguages()
    expect(get).toHaveBeenLastCalledWith('/api/languages', { signal: undefined })
    await fetchJudgedProgress()
    expect(get).toHaveBeenLastCalledWith('/api/users/me/progress', { signal: undefined })
  })

  it('accepts only judged SOLVED records and ignores heuristic resolution', () => {
    const tasks = [{ taskId: 1, status: 'SOLVED' }, { taskId: 2, status: 'IN_PROGRESS', resolvedAfterFeedback: true }]
    expect([...solvedProblemIds({ progressSource: 'JUDGED_SUBMISSION', tasks })]).toEqual(['1'])
    expect(solvedProblemIds({ progressSource: 'STATIC_HEURISTIC', tasks }).size).toBe(0)
    expect(solvedProblemIds({ tasks }).size).toBe(0)
    expect(solvedProblemIds(null).size).toBe(0)
  })
})
