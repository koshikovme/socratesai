import { beforeEach, describe, expect, test } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { contextKey, MAX_EDITOR_CONTEXTS, useEditorStore } from '../editor'
import { useAuthStore } from '../auth'
import { registerSessionCleanup } from '../../services/sessionLifecycle'

describe('memory-only native editor buffers', () => {
  beforeEach(() => { localStorage.clear(); sessionStorage.clear(); setActivePinia(createPinia()) })
  test('keeps edits per problem, pinned version and language; revisions are monotonic', () => {
    const store = useEditorStore()
    const python = { problemId: 1, problemVersionId: 2, languageKey: 'python3' }
    const java = { ...python, languageKey: 'java' }
    const a = store.open(python, 'print(0)')
    const id = a.editorSessionId
    store.update(contextKey(python), 'print(1)')
    store.update(contextKey(python), 'print(1)')
    const b = store.open(java, 'class Main {}')
    store.update(contextKey(java), 'class Main { }')
    expect(store.open(python, 'replacement').code).toBe('print(1)')
    expect(a.codeRevision).toBe(1)
    expect(a.editorSessionId).toBe(id)
    expect(b.editorSessionId).not.toBe(id)
    expect(store.open({ ...python, problemVersionId: 3 }, '').code).toBe('')
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
  test('late acknowledgement cannot clear newer unsaved edits; logout clears all buffers/resources', () => {
    const store = useEditorStore()
    const context = { problemId: 1, problemVersionId: 2, languageKey: 'python3' }
    store.open(context, '')
    store.update(contextKey(context), 'old')
    const sent = { ...store.contexts[contextKey(context)] }
    store.update(contextKey(context), 'private new code')
    store.markSubmitted(sent)
    expect(store.contexts[contextKey(context)].dirty).toBe(true)
    let cleared = false
    const unregister = registerSessionCleanup(() => { cleared = true })
    useAuthStore().logout()
    unregister()
    expect(cleared).toBe(true)
    expect(store.contexts).toEqual({})
  })
  test('bounds private buffers without silently overwriting drafts; explicit discard frees capacity', () => {
    const store = useEditorStore()
    Array.from({ length: MAX_EDITOR_CONTEXTS }, (_, index) => store.open({ problemId: index + 1, problemVersionId: 2, languageKey: 'python3' }, `draft ${index}`))
    expect(() => store.open({ problemId: 999, problemVersionId: 2, languageKey: 'python3' }, '')).toThrow()
    const key = contextKey({ problemId: 1, problemVersionId: 2, languageKey: 'python3' })
    expect(store.contexts[key].code).toBe('draft 0')
    store.discardOtherContexts(key)
    expect(Object.keys(store.contexts)).toHaveLength(1)
    store.open({ problemId: 999, problemVersionId: 2, languageKey: 'python3' }, '')
    expect(Object.keys(store.contexts)).toHaveLength(2)
  })

})
