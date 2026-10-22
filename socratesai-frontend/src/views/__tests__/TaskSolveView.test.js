import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'

const transport = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), createMentorSocket: vi.fn(), send: vi.fn(), disconnect: vi.fn() }))
vi.mock('../../services/api', () => ({ default: { get: transport.get, post: transport.post, put: transport.put }, WEBSOCKET_BASE_URL: '' }))
vi.mock('../../monaco-setup', () => ({}))
vi.mock('../../services/websocketService', () => ({ createMentorSocket: transport.createMentorSocket }))
import TaskSolveView from '../TaskSolveView.vue'
import { useAuthStore } from '../../stores/auth'
import { useEditorStore, contextKey } from '../../stores/editor'
import { clearSessionResources } from '../../services/sessionLifecycle'

const publicProblem = (id = 1, version = 11) => ({
  id, problemVersionId: version, versionNumber: 1, title: `Problem ${id}`, topic: 'Basics', difficulty: 'EASY',
  statement: 'Read a number and print it.', inputFormat: 'One number', outputFormat: 'One number', constraintsText: '',
  samples: [{ position: 0, input: '1', expectedOutput: '1' }],
  languages: [
    { languageKey: 'python3', starterCode: 'print(input())', timeLimitMs: 1000, memoryLimitKb: 65536 },
    { languageKey: 'java', starterCode: 'class Main {}', timeLimitMs: 2000, memoryLimitKb: 65536 },
    { languageKey: 'cpp17', starterCode: 'int main() {}', timeLimitMs: 1000, memoryLimitKb: 65536 },
  ],
})
const CodeEditorStub = defineComponent({
  props: ['modelValue', 'language', 'contextId'], emits: ['update:modelValue'],
  template: '<textarea aria-label="Test source editor" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})
const button = (wrapper, label) => wrapper.findAll('button').find(item => item.text() === label)
function deferred() {
  let resolve, reject
  const promise = new Promise((complete, fail) => { resolve = complete; reject = fail })
  return { promise, resolve, reject }
}
const wrappers = []

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  setActivePinia(createPinia())
  useAuthStore().$patch({ token: 'jwt', userId: '7', role: 'STUDENT' })
  Object.values(transport).forEach(mock => mock.mockReset())
  transport.createMentorSocket.mockReturnValue({ send: transport.send, disconnect: transport.disconnect })
  transport.get.mockImplementation(async path => {
    if (path === '/api/submissions') return { data: { items: [], totalElements: 0 } }
    if (path.startsWith('/api/problems/')) return { data: publicProblem() }
    return { data: { id: 'result-id', status: 'FINISHED', verdict: 'ACCEPTED', cases: [] } }
  })
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  clearSessionResources()
  vi.restoreAllMocks()
})

async function mountWorkspace(path = '/problems/1?version=11') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().$patch({ token: 'jwt', userId: '7', role: 'STUDENT' })
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/problems/:id', component: TaskSolveView },
    { path: '/dashboard', component: { template: '<p>Catalog</p>' } },
    { path: '/collections/:id', component: { template: '<p>Collection</p>' } },
  ] })
  await router.push(path)
  await router.isReady()
  const wrapper = mount({ template: '<RouterView />' }, { global: {
    plugins: [pinia, router], stubs: { AppHeader: true, CodeEditorPanel: CodeEditorStub },
  } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router, editor: useEditorStore(), auth: useAuthStore() }
}

describe('native problem workspace', () => {
  it('preserves independent language buffers and monotonically revises each editor context', async () => {
    const { wrapper, editor } = await mountWorkspace()
    const pythonKey = contextKey({ problemId: 1, problemVersionId: 11, languageKey: 'python3' })
    const pythonSession = editor.contexts[pythonKey].editorSessionId
    expect(pythonSession).toMatch(/^[0-9a-f-]{36}$/)
    await wrapper.find('textarea').setValue('print("python edit")')
    expect(editor.contexts[pythonKey].codeRevision).toBe(1)
    await wrapper.find('select').setValue('java')
    expect(wrapper.find('textarea').element.value).toBe('class Main {}')
    await wrapper.find('textarea').setValue('class Main { /* java edit */ }')
    const javaKey = contextKey({ problemId: 1, problemVersionId: 11, languageKey: 'java' })
    expect(editor.contexts[javaKey].editorSessionId).not.toBe(pythonSession)
    expect(editor.contexts[javaKey].codeRevision).toBe(1)
    await wrapper.find('select').setValue('python3')
    expect(wrapper.find('textarea').element.value).toBe('print("python edit")')
    expect(editor.contexts[pythonKey].editorSessionId).toBe(pythonSession)
    await wrapper.find('textarea').setValue('print("python edit 2")')
    expect(editor.contexts[pythonKey].codeRevision).toBe(2)
    expect(editor.contexts[javaKey].code).toContain('java edit')
    expect(localStorage.getItem('sourceCode')).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('aborts old route loads and ignores their late responses', async () => {
    const pending = deferred()
    transport.get.mockImplementation(path => {
      if (path === '/api/submissions') return Promise.resolve({ data: { items: [], totalElements: 0 } })
      if (path === '/api/problems/1/versions/11') return pending.promise
      return Promise.resolve({ data: publicProblem(2, 22) })
    })
    const { wrapper, router } = await mountWorkspace()
    const oldSignal = transport.get.mock.calls.find(call => call[0] === '/api/problems/1/versions/11')[1].signal
    await router.push('/problems/2?version=22')
    await flushPromises()
    expect(oldSignal.aborted).toBe(true)
    pending.resolve({ data: { ...publicProblem(), title: 'Obsolete task response' } })
    await flushPromises()
    expect(wrapper.text()).toContain('Problem 2')
    expect(wrapper.text()).not.toContain('Obsolete task response')
    expect(wrapper.find('textarea').element.value).toBe('print(input())')
  })

  it('submits a frozen native snapshot, disables actions during judging and never renders hidden diagnostics', async () => {
    const pending = deferred()
    transport.post.mockReturnValueOnce(pending.promise)
    const terminal = { submission: { id: 'submission-id', status: 'FINISHED', verdict: 'WRONG_ANSWER',
      attemptNumber: 4, testsPassed: 1, testsTotal: 2, timeConsumedMs: 21, memoryConsumedBytes: 1024 },
      cases: [{ position: 1, hidden: true, verdict: 'WRONG_ANSWER', timeConsumedMs: 11, memoryConsumedBytes: 512,
        stdout: 'HIDDEN OUTPUT MUST NOT RENDER', stderr: 'HIDDEN DIAGNOSTIC', expectedOutput: 'HIDDEN ANSWER', token: 'secret-token' }] }
    transport.get.mockImplementation(async path => ({ data: path === '/api/submissions'
      ? { items: [], totalElements: 0 } : path === '/api/submissions/submission-id' ? terminal : publicProblem() }))
    const { wrapper } = await mountWorkspace('/problems/1?version=11&collection=8')
    await wrapper.find('textarea').setValue('print("captured")')
    await button(wrapper, 'Submit').trigger('click')
    expect(transport.post).toHaveBeenCalledTimes(1)
    const [url, body] = transport.post.mock.calls[0]
    expect(url).toBe('/api/problems/1/submissions')
    expect(body).toMatchObject({ problemVersionId: 11, languageKey: 'python3', sourceCode: 'print("captured")', collectionId: 8 })
    expect(body.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/)
    expect(body).not.toHaveProperty('studentId')
    expect(body).not.toHaveProperty('attemptNumber')
    expect(button(wrapper, 'Run samples').attributes('disabled')).toBeDefined()
    expect(wrapper.find('select').attributes('disabled')).toBeDefined()
    await wrapper.find('textarea').setValue('print("later edit")')
    expect(body.sourceCode).toBe('print("captured")')
    pending.resolve({ data: { id: 'submission-id', status: 'QUEUED' } })
    await flushPromises()
    expect(transport.get).toHaveBeenCalledWith('/api/submissions/submission-id', expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(wrapper.text()).toContain('WRONG_ANSWER')
    expect(wrapper.text()).toContain('Server attempt 4')
    expect(wrapper.text()).toContain('21 ms')
    expect(wrapper.text()).not.toContain('HIDDEN OUTPUT')
    expect(wrapper.text()).not.toContain('HIDDEN DIAGNOSTIC')
    expect(wrapper.text()).not.toContain('HIDDEN ANSWER')
    expect(wrapper.text()).not.toContain('secret-token')
    expect(button(wrapper, 'Submit').attributes('disabled')).toBeUndefined()
  })

  it('runs only sample endpoint and preserves the same idempotency key after an ambiguous POST failure', async () => {
    transport.post.mockRejectedValueOnce({ code: 'ERR_NETWORK' })
      .mockResolvedValueOnce({ data: { id: 'run-id', status: 'FINISHED', verdict: 'ACCEPTED', testsTotal: 1, testsPassed: 1 } })
    const { wrapper } = await mountWorkspace('/problems/1?version=11&collection=8')
    await button(wrapper, 'Run samples').trigger('click')
    await flushPromises()
    expect(button(wrapper, 'Submit').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Could not reach the service')
    await button(wrapper, 'Retry same request safely').trigger('click')
    await flushPromises()
    expect(transport.post).toHaveBeenCalledTimes(2)
    expect(transport.post.mock.calls[0][0]).toBe('/api/problems/1/runs')
    expect(transport.post.mock.calls[1][1]).toEqual(transport.post.mock.calls[0][1])
    expect(transport.post.mock.calls[0][1]).not.toHaveProperty('collectionId')
    expect(wrapper.text()).toContain('Only an accepted graded submission counts as solved')
    expect(wrapper.text()).not.toContain('SOLVED')
  })

  it('clears source, closes the mentor socket and aborts in-flight judging on logout', async () => {
    const pending = deferred()
    transport.post.mockReturnValueOnce(pending.promise)
    const { wrapper, auth, editor } = await mountWorkspace()
    await wrapper.find('textarea').setValue('private student buffer')
    await button(wrapper, 'Submit').trigger('click')
    const signal = transport.post.mock.calls[0][2].signal
    auth.logout()
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(transport.disconnect).toHaveBeenCalled()
    expect(editor.contexts).toEqual({})
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('private student buffer')
    pending.resolve({ data: { id: 'late-result', status: 'FINISHED', verdict: 'ACCEPTED' } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('ACCEPTED')
    expect(transport.get.mock.calls.some(([url]) => url === '/api/submissions/late-result')).toBe(false)
  })

  it('blocks oversize code locally and prevents unconfirmed navigation from dropping edits', async () => {
    const { wrapper, router } = await mountWorkspace()
    await wrapper.find('textarea').setValue('x'.repeat(20001))
    expect(wrapper.text()).toContain('Source exceeds 20000 characters')
    expect(button(wrapper, 'Submit').attributes('disabled')).toBeDefined()
    expect(button(wrapper, 'Run samples').attributes('disabled')).toBeDefined()
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false)
    await router.push('/dashboard')
    expect(confirmation).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/problems/1')
    expect(transport.post).not.toHaveBeenCalled()
  })
})
