import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from '../api'
import { teacherService } from '../teacherService'

vi.mock('../api', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
beforeEach(() => Object.values(api).forEach((method) => method.mockReset().mockResolvedValue({ data: { id: 5 } })))

describe('teacher HTTP contracts', () => {
  it('keeps draft content and reference source in request bodies and propagates cancellation', async () => {
    const config = { signal: new AbortController().signal }
    const draft = { lockVersion: 2, title: 'A problem' }
    const solutions = [{ languageKey: 'python3', sourceCode: 'private reference' }]
    await teacherService.saveProblem(7, draft, config)
    await teacherService.verify(7, 2, solutions, config)
    expect(api.put).toHaveBeenCalledWith('/api/teacher/problems/7/draft', draft, config)
    expect(api.post).toHaveBeenCalledWith('/api/teacher/problems/7/verify', { lockVersion: 2, solutions }, config)
  })

  it('pins the verification ID and revision and uses only owner-scoped verification URLs', async () => {
    await teacherService.verification(7, 'proof-id')
    await teacherService.publishProblem(7, 4, 'proof-id')
    expect(api.get).toHaveBeenCalledWith('/api/teacher/problems/7/verifications/proof-id', undefined)
    expect(api.post).toHaveBeenCalledWith('/api/teacher/problems/7/publish', { lockVersion: 4, verificationId: 'proof-id' }, undefined)
  })

  it('sends optimistic revisions on deletion and bounds catalog pages', async () => {
    await teacherService.deleteProblem(7, 4, true)
    await teacherService.deleteCollection(3, 6)
    await teacherService.problems(2)
    expect(api.delete).toHaveBeenCalledWith('/api/teacher/problems/7/draft', { params: { lockVersion: 4 } })
    expect(api.delete).toHaveBeenCalledWith('/api/teacher/collections/3', { params: { lockVersion: 6 } })
    expect(api.get).toHaveBeenCalledWith('/api/teacher/problems', { params: { page: 2, size: 20 } })
  })
})
