import { beforeEach, describe, expect, test, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
const handlers = vi.hoisted(() => ({ request: null, error: null }))
vi.mock('axios', () => ({ default: { create: () => ({ interceptors: {
  request: { use: callback => { handlers.request = callback } },
  response: { use: (_success, failure) => { handlers.error = failure } },
} }) } }))
import '../api'
import { useAuthStore } from '../../stores/auth'
import { useEditorStore } from '../../stores/editor'
import { apiErrorMessage } from '../errors'

beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()) })
describe('authenticated API lifecycle', () => {
  test('a 401 from the active identity clears transient source and credentials', async () => {
    const auth = useAuthStore()
    auth.setAuth({ token: 'current', userId: 7, role: 'STUDENT' })
    const editor = useEditorStore()
    editor.open({ problemId: 1, problemVersionId: 2, languageKey: 'python3' }, 'PRIVATE_SOURCE')
    const config = handlers.request({ headers: {} })
    expect(config.headers.Authorization).toBe('Bearer current')
    await expect(handlers.error({ response: { status: 401 }, config })).rejects.toBeDefined()
    expect(editor.contexts).toEqual({})
    expect(auth.token).toBe('')
  })
  test('late 401 from a previous account cannot sign out the new identity', async () => {
    const auth = useAuthStore()
    auth.token = 'old'
    const config = handlers.request({ headers: {} })
    auth.token = 'new'
    await expect(handlers.error({ response: { status: 401 }, config })).rejects.toBeDefined()
    expect(auth.token).toBe('new')
  })
  test('only fixed envelope messages are shown, never raw error bodies', () => {
    expect(apiErrorMessage({ response: { status: 500, data: { code: 'INTERNAL_ERROR', message: 'SELECT private_source secret=...' } } })).not.toContain('SELECT')
    expect(apiErrorMessage({ response: { status: 409, data: { message: 'PRIVATE_SOURCE' } } })).toContain('Reload')
  })
})
