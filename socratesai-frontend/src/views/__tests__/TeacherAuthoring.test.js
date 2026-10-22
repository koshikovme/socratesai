import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import TeacherProblemEditorView from '../TeacherProblemEditorView.vue'
import TeacherCollectionEditorView from '../TeacherCollectionEditorView.vue'
import AdminUsersView from '../AdminUsersView.vue'
import { teacherService } from '../../services/teacherService'
import { clearSessionResources } from '../../services/sessionLifecycle'
import { collectionRequest, problemDraft, validateProblemDraft, verificationAllowsPublish } from '../../services/authoringForms'

const navigation = vi.hoisted(() => ({ route: null, push: vi.fn(), replace: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => navigation.route,
  useRouter: () => ({ push: navigation.push, replace: navigation.replace }),
  onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn(),
}))
vi.mock('../../components/AppHeader.vue', () => ({ default: { template: '<header />' } }))
vi.mock('../../services/teacherService', () => ({ teacherService: Object.fromEntries([
  'languages', 'problem', 'createProblem', 'saveProblem', 'verify', 'verification', 'publishProblem', 'cloneDraft', 'archiveProblem', 'deleteProblem',
  'collection', 'publishedProblems', 'createCollection', 'saveCollection', 'publishCollection', 'archiveCollection', 'deleteCollection', 'changeRole',
].map((name) => [name, vi.fn()])) }))

const problem = () => ({
  id: 7, lockVersion: 3, visibility: 'AUTHENTICATED', status: 'DRAFT', publishedVersionId: null,
  version: {
    id: 17, versionNumber: 1, lockVersion: 9, status: 'DRAFT', title: 'Sum', topic: 'Arithmetic', difficulty: 'EASY',
    statement: 'Print the sum.', inputFormat: 'Two integers', outputFormat: 'One integer', constraintsText: '', comparisonMode: 'EXACT',
    languages: [{ languageKey: 'python3', starterCode: '', timeLimitMs: 1000, wallTimeLimitMs: 2000, memoryLimitKb: 65536, outputLimitBytes: 1024, runtimeKey: 'private-runtime' }],
    tests: [{ id: 1, position: 3, inputData: '1 2', expectedOutput: '3', hidden: false }, { id: 2, position: 7, inputData: 'hidden input', expectedOutput: 'hidden output', hidden: true }],
  },
})
const proof = (changes = {}) => ({
  id: '00000000-0000-4000-8000-000000000001', problemVersionId: 17, status: 'FINISHED', verdict: 'ACCEPTED',
  testsTotal: 2, testsPassed: 2, validUntil: new Date(Date.now() + 60000).toISOString(), ...changes,
})
const collection = () => ({
  id: 3, lockVersion: 6, status: 'DRAFT', title: 'Basics', description: '', visibility: 'AUTHENTICATED',
  problems: [{ problemId: 7, problemVersionId: 17, title: 'Sum', position: 0 }, { problemId: 8, problemVersionId: 18, title: 'Multiply', position: 1 }],
})

let wrappers = []
function render(component) {
  const wrapper = mount(component, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  wrappers.push(wrapper)
  return wrapper
}
function button(wrapper, label) { return wrapper.findAll('button').find((entry) => entry.text() === label) }
beforeEach(() => {
  vi.clearAllMocks()
  navigation.route = reactive({ params: { id: '7' } })
  teacherService.problem.mockResolvedValue(problem())
  teacherService.languages.mockResolvedValue([{ languageKey: 'python3', runtimeVersion: '3.11' }])
  teacherService.collection.mockResolvedValue(collection())
  teacherService.publishedProblems.mockResolvedValue({ page: 0, totalElements: 2, items: [
    { id: 7, problemVersionId: 27, title: 'Sum v2' }, { id: 9, problemVersionId: 19, title: 'Divide' },
  ] })
})
afterEach(() => { wrappers.forEach((wrapper) => wrapper.unmount()); wrappers = []; vi.useRealTimers(); vi.restoreAllMocks() })

describe('teacher problem authoring', () => {
  it('publishes only the saved exact version after complete reference verification', async () => {
    const wrapper = render(TeacherProblemEditorView)
    await flushPromises()
    expect(button(wrapper, 'Publish verified version').element.disabled).toBe(true)
    expect(button(wrapper, 'Verify reference solution').element.disabled).toBe(true)
    await wrapper.findAll('textarea').at(-1).setValue('print(sum(map(int, input().split())))')
    teacherService.verify.mockResolvedValue(proof({ status: 'QUEUED', verdict: null, testsPassed: 0 }))
    teacherService.verification.mockResolvedValue(proof())
    await button(wrapper, 'Verify reference solution').trigger('click')
    await flushPromises()
    expect(teacherService.verify).toHaveBeenCalledWith(7, 3, [{ languageKey: 'python3', sourceCode: 'print(sum(map(int, input().split())))' }], expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(button(wrapper, 'Publish verified version').element.disabled).toBe(false)
    teacherService.publishProblem.mockResolvedValue({ ...problem(), status: 'PUBLISHED', version: { ...problem().version, status: 'PUBLISHED' } })
    await button(wrapper, 'Publish verified version').trigger('click')
    await flushPromises()
    expect(teacherService.publishProblem).toHaveBeenCalledWith(7, 3, proof().id, expect.any(Object))
    expect(wrapper.text()).toContain('Verified version published.')
    expect(wrapper.text()).not.toContain('Reference source ·')
  })

  it('invalidates verification when the draft changes, and blocks partial or expired evidence', async () => {
    const wrapper = render(TeacherProblemEditorView)
    await flushPromises()
    await wrapper.findAll('textarea').at(-1).setValue('print(3)')
    teacherService.verify.mockResolvedValue(proof())
    teacherService.verification.mockResolvedValue(proof())
    await button(wrapper, 'Verify reference solution').trigger('click')
    await flushPromises()
    await wrapper.find('textarea[name="statement"]').setValue('Changed task')
    expect(button(wrapper, 'Publish verified version').element.disabled).toBe(true)
    expect(button(wrapper, 'Verify reference solution').element.disabled).toBe(true)
    expect(verificationAllowsPublish(proof({ testsPassed: 1 }), 17, false)).toBe(false)
    expect(verificationAllowsPublish(proof({ validUntil: new Date(Date.now() - 1).toISOString() }), 17, false)).toBe(false)
    expect(verificationAllowsPublish(proof(), 18, false)).toBe(false)
  })

  it('preserves local edits on optimistic conflict and renders only safe errors', async () => {
    const wrapper = render(TeacherProblemEditorView)
    await flushPromises()
    await wrapper.find('input[name="title"]').setValue('My edited title')
    teacherService.saveProblem.mockRejectedValue({ response: { status: 409, data: { code: 'CONFLICT', message: 'SQL private secret' } } })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(teacherService.saveProblem.mock.calls[0][1]).toMatchObject({ title: 'My edited title', lockVersion: 3 })
    expect(wrapper.find('input[name="title"]').element.value).toBe('My edited title')
    expect(wrapper.text()).toContain('Reload the latest version')
    expect(wrapper.text()).not.toContain('SQL private secret')
    expect(teacherService.saveProblem.mock.calls[0][1].tests.map((test) => test.position)).toEqual([0, 1])
  })

  it('clears source, hidden author data and polling on logout', async () => {
    vi.useFakeTimers()
    const wrapper = render(TeacherProblemEditorView)
    await flushPromises()
    await wrapper.findAll('textarea').at(-1).setValue('private reference source')
    teacherService.verify.mockResolvedValue(proof({ status: 'QUEUED', verdict: null, testsPassed: 0 }))
    teacherService.verification.mockResolvedValue(proof({ status: 'RUNNING', verdict: null, testsPassed: 0 }))
    await button(wrapper, 'Verify reference solution').trigger('click')
    await flushPromises()
    expect(teacherService.verification).toHaveBeenCalledTimes(1)
    clearSessionResources()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(60000)
    expect(teacherService.verification).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('textarea').map((field) => field.element.value).join(' ')).not.toContain('private reference source')
    expect(wrapper.findAll('textarea').map((field) => field.element.value).join(' ')).not.toContain('hidden input')
    expect(teacherService.verify.mock.calls[0][3].signal.aborted).toBe(true)
  })

  it('keeps failed sandbox verification unpublished and permits a safe retry', async () => {
    const wrapper = render(TeacherProblemEditorView)
    await flushPromises()
    await wrapper.findAll('textarea').at(-1).setValue('print(3)')
    teacherService.verify.mockResolvedValue(proof({ status: 'QUEUED', verdict: null }))
    teacherService.verification.mockResolvedValue(proof({ status: 'SYSTEM_ERROR', verdict: null, failureCode: 'SECRET BODY' }))
    await button(wrapper, 'Verify reference solution').trigger('click')
    await flushPromises()
    expect(button(wrapper, 'Publish verified version').element.disabled).toBe(true)
    expect(button(wrapper, 'Verify reference solution').element.disabled).toBe(false)
    expect(wrapper.text()).toContain('Check sandbox availability')
    expect(wrapper.text()).not.toContain('SECRET BODY')
  })

  it('expires a successful proof in the UI and refuses reference checks without hidden tests', async () => {
    vi.useFakeTimers()
    const wrapper = render(TeacherProblemEditorView)
    await flushPromises()
    await wrapper.findAll('textarea').at(-1).setValue('print(3)')
    const expiresSoon = proof({ validUntil: new Date(Date.now() + 1500).toISOString() })
    teacherService.verify.mockResolvedValue(expiresSoon)
    teacherService.verification.mockResolvedValue(expiresSoon)
    await button(wrapper, 'Verify reference solution').trigger('click')
    await flushPromises()
    expect(button(wrapper, 'Publish verified version').element.disabled).toBe(false)
    await vi.advanceTimersByTimeAsync(2000)
    expect(button(wrapper, 'Publish verified version').element.disabled).toBe(true)
    await button(wrapper, 'Remove test 2').trigger('click')
    expect(button(wrapper, 'Verify reference solution').element.disabled).toBe(true)
  })

  it('discards late author responses after logout and never creates a replacement for an inaccessible problem', async () => {
    let complete
    teacherService.problem.mockImplementationOnce(() => new Promise((resolve) => { complete = resolve }))
    const wrapper = render(TeacherProblemEditorView)
    clearSessionResources()
    complete(problem())
    await flushPromises()
    expect(wrapper.find('input[name="title"]').element.value).toBe('')
    expect(wrapper.get('form button[type="submit"]').element.disabled).toBe(true)
    await wrapper.get('form').trigger('submit')
    expect(teacherService.createProblem).not.toHaveBeenCalled()
  })
})

describe('teacher collection authoring', () => {
  it('atomically saves the reordered explicit version pins with the expected revision', async () => {
    const wrapper = render(TeacherCollectionEditorView)
    await flushPromises()
    await wrapper.get('button[aria-label="Move Multiply up"]').trigger('click')
    await button(wrapper, 'Update pinned version').trigger('click')
    teacherService.saveCollection.mockResolvedValue({ ...collection(), lockVersion: 7 })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(teacherService.saveCollection).toHaveBeenCalledWith(3, expect.objectContaining({
      lockVersion: 6, problems: [{ problemId: 8, problemVersionId: 18 }, { problemId: 7, problemVersionId: 27 }],
    }), expect.any(Object))
  })

  it('does not silently replace old pins and retains order after a stale-edit conflict', async () => {
    const wrapper = render(TeacherCollectionEditorView)
    await flushPromises()
    expect(wrapper.find('ol').text()).toContain('pinned version ID 17')
    expect(wrapper.find('ol').text()).not.toContain('pinned version ID 27')
    await wrapper.get('button[aria-label="Move Multiply up"]').trigger('click')
    teacherService.saveCollection.mockRejectedValue({ response: { status: 409, data: { code: 'CONFLICT' } } })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.findAll('ol li')[0].text()).toContain('Multiply')
    expect(button(wrapper, 'Publish collection').element.disabled).toBe(true)
    expect(wrapper.text()).toContain('Reload the latest version')
  })
})

describe('authoring contracts', () => {
  it('omits internal runtime/test identifiers from draft writes and checks byte limits', () => {
    const draft = problemDraft(problem())
    expect(draft.languages[0]).not.toHaveProperty('runtimeKey')
    expect(draft.tests[0]).not.toHaveProperty('id')
    draft.tests[0].inputData = '😀'.repeat(20000)
    expect(validateProblemDraft(draft)).toContain('UTF-8 bytes')
    expect(collectionRequest(collection()).problems[0]).toEqual({ problemId: 7, problemVersionId: 17 })
  })

  it('admin role changes send expected role and a mandatory audit reason', async () => {
    const wrapper = render(AdminUsersView)
    await wrapper.get('input').setValue('42')
    await wrapper.get('textarea').setValue('Teaching appointment')
    teacherService.changeRole.mockResolvedValue({ id: 42, role: 'TEACHER' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(teacherService.changeRole).toHaveBeenCalledWith(42, { expectedRole: 'STUDENT', role: 'TEACHER', reason: 'Teaching appointment' }, expect.any(Object))
    expect(wrapper.text()).toContain('recorded in the audit log')
  })
})
