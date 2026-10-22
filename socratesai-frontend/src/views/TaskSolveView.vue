<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { contextKey, SOURCE_LIMIT, useEditorStore } from '../stores/editor'
import { fetchProblem } from '../services/catalogService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'
import { updateInteractionResult } from '../services/mentorService'
import { useNativeMentor } from '../composables/useNativeMentor'
import { useJudging } from '../composables/useJudging'
import AppHeader from '../components/AppHeader.vue'
import ProblemStatement from '../components/ProblemStatement.vue'
import CodeEditorPanel from '../components/CodeEditorPanel.vue'
import FeedbackPanel from '../components/FeedbackPanel.vue'
import ExecutionResult from '../components/ExecutionResult.vue'
import SubmissionHistory from '../components/SubmissionHistory.vue'

const auth = useAuthStore()
const route = useRoute()
const editor = useEditorStore()
editor.resetSessions()
const problem = ref(null)
const languageKey = ref('')
const loading = ref(false)
const error = ref('')
const history = ref(null)
const savingOutcome = ref(false)
const reviewState = ref('')
const outcomeError = ref('')
let controller
let loadGeneration = 0
let outcomeController
const language = computed(() => problem.value?.languages.find(item => item.languageKey === languageKey.value))
const key = computed(() => problem.value && contextKey({ problemId: problem.value.id, problemVersionId: problem.value.problemVersionId, languageKey: languageKey.value }))
const buffer = computed(() => editor.contexts[key.value])
const code = computed({ get: () => buffer.value?.code ?? '', set: value => editor.update(key.value, value) })
const tooLong = computed(() => code.value.length > SOURCE_LIMIT)
const snapshot = computed(() => !buffer.value || tooLong.value ? null : {
  problemId: buffer.value.problemId, problemVersionId: buffer.value.problemVersionId,
  languageKey: buffer.value.languageKey, editorSessionId: buffer.value.editorSessionId,
  codeRevision: buffer.value.codeRevision, code: buffer.value.code,
})
const mentor = useNativeMentor({ snapshot, token: () => auth.token, advanceRevision: () => editor.advanceRevision(key.value) })
const judging = useJudging({
  onCreated: (operation, submitted) => {
    if (operation.kind === 'submission') { editor.markSubmitted(submitted); history.value?.refresh() }
  },
  onFinished: operation => {
    history.value?.refresh()
    if (operation.problemId === problem.value?.id && operation.problemVersionId === problem.value?.problemVersionId && operation.languageKey === languageKey.value) mentor.refresh()
  },
})
const judgingLocked = computed(() => judging.busy.value || judging.phase.value === 'paused')
const submitDisabled = computed(() => !snapshot.value || !code.value.trim() || judgingLocked.value)
const unsaved = computed(() => Object.values(editor.contexts).some(item => item.dirty))

watch(languageKey, () => {
  if (!problem.value || !language.value) return
  try {
    editor.open({ problemId: problem.value.id, problemVersionId: problem.value.problemVersionId, languageKey: languageKey.value }, language.value.starterCode)
  } catch {
    error.value = 'The in-memory editor limit was reached. Discard other buffers to open this language.'
  }
}, { flush: 'sync' })
watch(() => mentor.feedback.value?.interactionId, () => { reviewState.value = ''; outcomeError.value = '' })

async function load() {
  controller?.abort()
  controller = new AbortController()
  const generation = ++loadGeneration
  loading.value = true
  problem.value = null
  languageKey.value = ''
  error.value = ''
  judging.stop()
  try {
    const id = Number(route.params.id)
    const version = route.query.version ? Number(route.query.version) : null
    if (!Number.isSafeInteger(id) || id < 1 || (version !== null && (!Number.isSafeInteger(version) || version < 1))) throw { code: 'INVALID_REQUEST' }
    const response = await fetchProblem(id, version, { signal: controller.signal })
    if (generation !== loadGeneration) return
    problem.value = response
    languageKey.value = response.languages[0]?.languageKey ?? ''
  } catch (e) { if (generation === loadGeneration) error.value = apiErrorMessage(e) }
  finally { if (generation === loadGeneration) loading.value = false }
}

function discardOtherBuffers() {
  if (!window.confirm('Discard all other in-memory code buffers? This cannot be undone.')) return
  editor.discardOtherContexts(key.value)
  if (problem.value && language.value) editor.open({ problemId: problem.value.id, problemVersionId: problem.value.problemVersionId, languageKey: languageKey.value }, language.value.starterCode)
  error.value = ''
}

function start(kind) {
  if (submitDisabled.value) return
  const collectionId = route.query.collection ? Number(route.query.collection) : undefined
  if (collectionId !== undefined && (!Number.isSafeInteger(collectionId) || collectionId < 1)) { error.value = 'Invalid collection link. Open the problem from the catalog.'; return }
  judging.start(kind, { ...snapshot.value }, collectionId)
}

async function markFeedbackResolved(resolvedAfterFeedback) {
  const id = mentor.feedback.value?.interactionId
  if (!id || savingOutcome.value) return
  outcomeController?.abort()
  outcomeController = new AbortController()
  const current = outcomeController
  savingOutcome.value = true
  try {
    await updateInteractionResult(id, { resolvedAfterFeedback, fixedAfterMs: null }, { signal: current.signal })
    if (!current.signal.aborted && mentor.feedback.value?.interactionId === id) reviewState.value = resolvedAfterFeedback ? 'resolved' : 'stuck'
  } catch (e) { if (!current.signal.aborted) outcomeError.value = apiErrorMessage(e) }
  finally { if (!current.signal.aborted) savingOutcome.value = false }
}
function canLeave() { return !auth.token || !unsaved.value || window.confirm('Code is held in memory only. Leave this workspace? Reloading or signing out will discard it.') }
function beforeUnload(event) { if (unsaved.value) { event.preventDefault(); event.returnValue = '' } }
function clear() { loading.value = false; loadGeneration++; controller?.abort(); outcomeController?.abort(); problem.value = null; languageKey.value = ''; error.value = ''; outcomeError.value = ''; reviewState.value = ''; savingOutcome.value = false }
const unregister = registerSessionCleanup(clear)
window.addEventListener('beforeunload', beforeUnload)
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
onBeforeUnmount(() => { clear(); unregister(); window.removeEventListener('beforeunload', beforeUnload) })
watch(() => [route.params.id, route.query.version, route.query.collection], load, { immediate: true })
</script>

<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content native-workspace">
      <RouterLink v-if="route.query.collection" :to="`/collections/${route.query.collection}`">Back to collection</RouterLink>
      <p v-if="loading" role="status">Loading problem…</p>
      <p v-if="error" role="alert" class="error-text">{{ error }}</p>
      <div v-if="problem" class="workspace-grid">
        <ProblemStatement :problem="problem" />
        <section class="workspace-code" aria-label="Solution workspace">
          <div class="card workspace-controls">
            <label>Language
              <select aria-label="Language" v-model="languageKey" :disabled="judging.busy.value">
                <option v-for="item in problem.languages" :key="item.languageKey" :value="item.languageKey">{{ item.languageKey }}</option>
              </select>
            </label>
            <p v-if="language" class="muted">{{ language.timeLimitMs }} ms · {{ language.memoryLimitKb }} KiB · Revision {{ buffer?.codeRevision ?? 0 }}</p>
            <p v-if="!problem.languages.length" role="alert">No verified language is available for this version.</p>
            <p v-if="tooLong" role="alert" class="error-text">Source exceeds {{ SOURCE_LIMIT }} characters. Shorten it before sending.</p>
            <div class="action-row">
              <button class="ghost-btn" :disabled="submitDisabled" @click="start('sample')">Run samples</button>
              <button class="primary-btn" :disabled="submitDisabled" @click="start('submission')">{{ judging.busy.value ? 'Judging…' : 'Submit' }}</button>
              <button class="ghost-btn" :disabled="!snapshot || mentor.pending.value" @click="mentor.refresh()">Ask mentor</button>
            </div>
          </div>
          <CodeEditorPanel v-if="buffer" v-model="code" :language="languageKey" :context-id="buffer.editorSessionId" />
          <button class="ghost-btn" @click="discardOtherBuffers">Discard other buffers</button>
          <p class="muted">Buffers stay in memory for each language and version. Feedback and sample runs do not count as graded attempts.</p>
          <ExecutionResult :result="judging.result.value" :operation="judging.operation.value" :phase="judging.phase.value" :error="judging.error.value" @retry="judging.retry()" />
          <FeedbackPanel :feedback="mentor.feedback.value" :loading="mentor.pending.value" :error="outcomeError || mentor.error.value" :connection-label="mentor.status.value" :saving-outcome="savingOutcome" :review-state="reviewState" @mark-resolved="markFeedbackResolved(true)" @mark-stuck="markFeedbackResolved(false)" />
        </section>
      </div>
      <SubmissionHistory ref="history" :disabled="judgingLocked" @select="item => judging.resume('submission', item)" />
    </main>
  </div>
</template>
