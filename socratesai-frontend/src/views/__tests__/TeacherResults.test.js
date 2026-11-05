import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import TeacherResultsView from '../TeacherResultsView.vue'
import { teacherService } from '../../services/teacherService'
import { clearSessionResources } from '../../services/sessionLifecycle'
import { setLocale, t } from '../../i18n'

const navigation = vi.hoisted(() => ({ route: null }))
vi.mock('vue-router', () => ({ useRoute: () => navigation.route }))
vi.mock('../../components/AppHeader.vue', () => ({ default: { template: '<header />' } }))
vi.mock('../../services/teacherService', () => ({ teacherService: { collection: vi.fn(), problem: vi.fn(), results: vi.fn(), submission: vi.fn() } }))
const row = { id: 'submission-fixture', studentId: 44, studentName: 'Learner Name', studentEmail: 'learner@example.test', groupName: 'CS1',
  problemId: 7, problemVersionId: 17, versionNumber: 1, problemTitle: 'Sum', languageKey: 'java', attemptNumber: 2,
  status: 'FINISHED', verdict: 'WRONG_ANSWER', testsPassed: 1, testsTotal: 2, createdAt: '2026-11-05T01:00:00Z' }
const response = () => ({ submissions: { items: [row], page: 0, size: 20, totalElements: 1 }, summary: { attempts: 1, students: 1, accepted: 0, pending: 0 } })
const detail = () => ({ submission: row, student: { fullName: row.studentName, email: row.studentEmail }, problemTitle: 'Sum',
  sourceCode: '<img src=x onerror=alert(1)> private-student-source', cases: [{ position: 0, hidden: true, verdict: 'WRONG_ANSWER', stdout: 'HIDDEN_ECHO', stderr: 'SECRET_DIAGNOSTIC' }] })
let wrapper
const button = label => wrapper.findAll('button').find(item => item.text() === label)
beforeEach(() => {
  vi.clearAllMocks(); setLocale('en')
  navigation.route = reactive({ params: { id: '3' }, meta: { resultsKind: 'collections' } })
  teacherService.collection.mockResolvedValue({ id: 3, title: 'Basics', problems: [{ problemId: 7, title: 'Sum' }] })
  teacherService.problem.mockResolvedValue({ id: 7, version: { title: 'Sum' } })
  teacherService.results.mockResolvedValue(response()); teacherService.submission.mockResolvedValue(detail())
})
afterEach(() => { wrapper?.unmount(); wrapper = null; clearSessionResources(); setLocale('en'); vi.useRealTimers(); vi.restoreAllMocks() })
async function render() {
  wrapper = mount(TeacherResultsView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
}

describe('staff submission monitoring', () => {
  test('refreshes visible lists, pauses while source is open and stops on logout', async () => {
    vi.useFakeTimers()
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    await render()
    await vi.advanceTimersByTimeAsync(10000); await flushPromises()
    expect(teacherService.results).toHaveBeenCalledTimes(2)
    await button('View attempt').trigger('click'); await flushPromises()
    await vi.advanceTimersByTimeAsync(10000)
    expect(teacherService.results).toHaveBeenCalledTimes(2)
    clearSessionResources()
    await vi.advanceTimersByTimeAsync(20000)
    expect(teacherService.results).toHaveBeenCalledTimes(2)
  })

  test('lists formal attempts without loading source until explicit review', async () => {
    await render()
    expect(wrapper.text()).toContain('Learner Name')
    expect(wrapper.text()).toContain('learner@example.test')
    expect(wrapper.text()).toContain('WRONG_ANSWER')
    expect(teacherService.submission).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('private-student-source')
    await button('View attempt').trigger('click'); await flushPromises()
    expect(teacherService.submission).toHaveBeenCalledWith(row.id, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(wrapper.text()).toContain('private-student-source')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('HIDDEN_ECHO')
    expect(wrapper.text()).not.toContain('SECRET_DIAGNOSTIC')
    await button('Close').trigger('click')
    expect(wrapper.text()).not.toContain('private-student-source')
  })

  test.each(['ru', 'kk'])('filters by exact API enum and student values while translated (%s)', async locale => {
    setLocale(locale); await render()
    await wrapper.find('input[type="search"]').setValue('Learner')
    const verdict = wrapper.findAll('select').find(select => select.find('option[value="WRONG_ANSWER"]').exists())
    await verdict.setValue('WRONG_ANSWER')
    await wrapper.find('form').trigger('submit'); await flushPromises()
    expect(teacherService.results).toHaveBeenLastCalledWith('collections', '3', expect.objectContaining({ query: 'Learner', verdict: 'WRONG_ANSWER', page: 0 }), expect.any(Object))
    await button('Learner Name').trigger('click'); await flushPromises()
    expect(teacherService.results).toHaveBeenLastCalledWith('collections', '3', expect.objectContaining({ studentId: 44 }), expect.any(Object))
    await button(t('Show all students')).trigger('click'); await flushPromises()
    expect(teacherService.results.mock.calls.at(-1)[2]).not.toHaveProperty('studentId')
  })

  test('clears private detail and ignores late responses after logout', async () => {
    let resolve
    teacherService.submission.mockImplementationOnce(() => new Promise(complete => { resolve = complete }))
    await render(); await button('View attempt').trigger('click')
    const signal = teacherService.submission.mock.calls[0][1].signal
    clearSessionResources(); resolve(detail()); await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.text()).not.toContain('private-student-source')
    expect(wrapper.text()).not.toContain('learner@example.test')
  })

  test('does not reveal a forbidden resource or raw backend details', async () => {
    teacherService.collection.mockRejectedValueOnce({ response: { status: 404, data: { code: 'NOT_FOUND', message: 'SQL PRIVATE DATA' } } })
    await render()
    expect(teacherService.results).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('resource is unavailable')
    expect(wrapper.text()).not.toContain('SQL PRIVATE DATA')
  })
})
