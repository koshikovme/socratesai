import { defineStore } from 'pinia'

export const SOURCE_LIMIT = 20000
export const MAX_EDITOR_CONTEXTS = 24
export const contextKey = ({ problemId, problemVersionId, languageKey }) => `${problemId}:${problemVersionId}:${languageKey}`

// Deliberately memory-only. Reload/logout discards source; languages keep independent buffers.
export const useEditorStore = defineStore('editor', {
  state: () => ({ contexts: {} }),
  actions: {
    open(context, starterCode = '') {
      const key = contextKey(context)
      if (!this.contexts[key]) {
        if (Object.keys(this.contexts).length >= MAX_EDITOR_CONTEXTS) throw { code: 'EDITOR_LIMIT' }
        this.contexts[key] = {
          problemId: context.problemId, problemVersionId: context.problemVersionId,
          languageKey: context.languageKey, editorSessionId: crypto.randomUUID(),
          codeRevision: 0, code: starterCode, dirty: false,
        }
      }
      return this.contexts[key]
    },
    update(key, code) {
      const context = this.contexts[key]
      if (!context || context.code === code) return
      context.code = code
      context.dirty = true
      this.advanceRevision(key)
    },
    advanceRevision(key) {
      const context = this.contexts[key]
      if (!context) return
      if (context.codeRevision >= Number.MAX_SAFE_INTEGER) {
        context.editorSessionId = crypto.randomUUID()
        context.codeRevision = 0
      } else context.codeRevision += 1
    },
    markSubmitted(snapshot) {
      const context = this.contexts[contextKey(snapshot)]
      if (context?.code === snapshot.code) context.dirty = false
    },
    discardOtherContexts(key) {
      this.contexts = this.contexts[key] ? { [key]: this.contexts[key] } : {}
    },
    resetSessions() {
      Object.values(this.contexts).forEach(context => {
        context.editorSessionId = crypto.randomUUID()
        context.codeRevision = 0
      })
    },
  },
})
