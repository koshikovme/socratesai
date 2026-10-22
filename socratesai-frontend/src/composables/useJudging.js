import { computed, onBeforeUnmount, ref } from 'vue'
import { createJudging, judgingSummary, isJudgingTerminal, pollJudging } from '../services/judgingService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'

export function useJudging({ onCreated = () => {}, onFinished = () => {} } = {}) {
  const operation = ref(null)
  const result = ref(null)
  const phase = ref('idle')
  const error = ref('')
  let controller = null
  let epoch = 0
  let pendingRequest = null
  const busy = computed(() => ['creating', 'polling'].includes(phase.value))

  function stop() {
    epoch += 1
    controller?.abort()
    pendingRequest = null
    operation.value = result.value = null
    phase.value = 'idle'
    error.value = ''
  }
  const unregister = registerSessionCleanup(stop)
  onBeforeUnmount(() => { stop(); unregister() })

  async function poll(current) {
    phase.value = 'polling'
    const terminal = await pollJudging(operation.value.kind, operation.value.id, {
      signal: controller.signal,
      onResult: value => { if (current === epoch) result.value = value },
    })
    if (current !== epoch) return
    phase.value = terminal ? 'complete' : 'paused'
    if (terminal) onFinished(operation.value, result.value)
    else error.value = 'Status monitoring paused. Resume checking or open submission history.'
  }

  async function execute() {
    if (busy.value || !operation.value) return
    const current = ++epoch
    controller?.abort()
    controller = new AbortController()
    error.value = ''
    try {
      if (!operation.value.id) {
        phase.value = 'creating'
        const response = await createJudging(operation.value.kind, pendingRequest.problemId, pendingRequest.body, { signal: controller.signal })
        if (current !== epoch) return
        operation.value.id = judgingSummary(response).id
        result.value = response
        onCreated(operation.value, pendingRequest.snapshot)
        pendingRequest = null
      }
      if (isJudgingTerminal(result.value)) {
        phase.value = 'complete'
        onFinished(operation.value, result.value)
      } else await poll(current)
    } catch (failure) {
      if (current !== epoch || controller.signal.aborted) return
      error.value = apiErrorMessage(failure)
      const status = failure?.response?.status
      if (operation.value.id && [403, 404].includes(status)) {
        phase.value = 'unavailable'
        result.value = null
        return
      }
      // A lost response may already have created a submission. Keep the exact body/key.
      const definitive = status >= 400 && status < 500 && ![408, 429].includes(status)
      phase.value = !operation.value.id && definitive ? 'rejected' : 'paused'
      if (definitive && !operation.value.id) pendingRequest = null
    }
  }

  async function start(kind, snapshot, collectionId) {
    if (busy.value || phase.value === 'paused') return
    result.value = null
    operation.value = {
      kind, problemId: snapshot.problemId, problemVersionId: snapshot.problemVersionId,
      languageKey: snapshot.languageKey, codeRevision: snapshot.codeRevision, id: null,
    }
    pendingRequest = {
      problemId: snapshot.problemId, snapshot: { ...snapshot },
      body: {
        problemVersionId: snapshot.problemVersionId, languageKey: snapshot.languageKey,
        sourceCode: snapshot.code, idempotencyKey: crypto.randomUUID(),
        ...(kind === 'submission' && collectionId ? { collectionId } : {}),
      },
    }
    await execute()
  }

  async function resume(kind, summary) {
    if (busy.value) return
    stop()
    operation.value = { kind, id: summary.id, languageKey: summary.languageKey, problemId: summary.problemId, problemVersionId: summary.problemVersionId }
    await execute()
  }

  return { operation, result, phase, error, busy, start, retry: execute, resume, stop }
}
