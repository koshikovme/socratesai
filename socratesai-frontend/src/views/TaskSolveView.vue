<script setup>
import { useI18n } from '../i18n'
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

const { t } = useI18n()

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
const automaticHints = ref(true)
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
const mentor = useNativeMentor({ snapshot, token: () => auth.token, advanceRevision: () => editor.advanceRevision(key.value), automatic: automaticHints })
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
  if (!window.confirm(t('Discard all other in-memory code buffers? This cannot be undone.'))) return
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
function canLeave() { return !auth.token || !unsaved.value || window.confirm(t('Code is held in memory only. Leave this workspace? Reloading or signing out will discard it.')) }
function restoreStarter() {
  const starter = language.value?.starterCode
  if (!buffer.value || !starter || code.value === starter) return
  if (code.value.trim() && !window.confirm(t('Replace this language buffer with the starter program? Copy any code you want to keep first.'))) return
  editor.update(key.value, starter)
}
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
      <RouterLink v-if="route.query.collection" :to="`/collections/${route.query.collection}`">{{ t('Back to collection') }}</RouterLink>
      <p v-if="loading" role="status">{{ t('Loading problem…') }}</p>
      <p v-if="error" role="alert" class="error-text">{{ t(error) }}</p>
      <div v-if="problem" class="workspace-grid">
        <div class="workspace-description">
          <ProblemStatement :problem="problem" />
          <section v-if="language" class="card program-contract" :aria-label="t('Program format')">
            <p class="eyebrow">{{ t('How to write your solution') }}</p>
            <p>{{ t('Write a complete program: read standard input and print the answer to standard output.') }}</p>
            <p v-if="languageKey === 'java'">{{ t('Keep class Main and public static void main(String[] args). Implement the function marked TODO using its parameters. Keep the input/output adapter unless the task needs a different format. A standalone Solution method is not called automatically.') }}</p>
            <p v-else-if="languageKey === 'python3'">{{ t('Implement the function marked TODO and keep its signature. The starter explains how inputs are passed and the answer is printed.') }}</p>
            <p v-else-if="languageKey === 'cpp17'">{{ t('Keep main() and implement the function marked TODO. The input/output adapter reads the parameters and prints the answer.') }}</p>
            <details v-if="language.starterCode"><summary>{{ t('Starter program · {entry}', { entry: language.entryPoint || languageKey }) }}</summary><pre>{{ language.starterCode }}</pre></details>
            <button class="ghost-btn" :disabled="judgingLocked || !language.starterCode || code === language.starterCode" @click="restoreStarter">{{ t('Reset to starter') }}</button>
          </section>
        </div>
        <section class="workspace-code" :aria-label="t('Solution workspace')">
          <div class="card workspace-controls">
            <label>{{ t('Language') }} <select :aria-label="t('Language')" v-model="languageKey" :disabled="judging.busy.value">
                <option v-for="item in problem.languages" :key="item.languageKey" :value="item.languageKey">{{ item.languageKey }}</option>
              </select>
            </label>
            <p v-if="language" class="muted">{{ language.timeLimitMs }} {{ t('ms') }} · {{ language.memoryLimitKb }} {{ t('KiB') }} · {{ language.entryPoint || languageKey }} · {{ t('Revision {revision}', { revision: buffer?.codeRevision ?? 0 }) }}</p>
            <p v-if="!problem.languages.length" role="alert">{{ t('No verified language is available for this version.') }}</p>
            <p v-if="tooLong" role="alert" class="error-text">{{ t('Source exceeds {limit} characters. Shorten it before sending.', { limit: SOURCE_LIMIT }) }}</p>
            <div class="action-row">
              <button class="ghost-btn" :disabled="submitDisabled" @click="start('sample')">{{ t('Run samples') }}</button>
              <button class="primary-btn" :disabled="submitDisabled" @click="start('submission')">{{ t(judging.busy.value ? 'Judging…' : 'Submit') }}</button>
              <button class="ghost-btn" :disabled="!snapshot || mentor.pending.value" @click="mentor.refresh()">{{ t('Ask mentor') }}</button>
            </div>
            <label class="inline-check"><input type="checkbox" v-model="automaticHints" /> {{ t('AI hints after a typing pause') }}</label>
            <p class="muted">{{ t('Ask mentor reviews your current code and the public task description. Running samples first is optional. AI hints do not compile your code or mark the task solved.') }}</p>
          </div>
          <CodeEditorPanel v-if="buffer" v-model="code" :language="languageKey" :context-id="buffer.editorSessionId" />
          <button class="ghost-btn" @click="discardOtherBuffers">{{ t('Discard other buffers') }}</button>
          <p class="muted">{{ t('Buffers stay in memory for each language and version. Feedback and sample runs do not count as graded attempts.') }}</p>
          <ExecutionResult :result="judging.result.value" :operation="judging.operation.value" :phase="judging.phase.value" :error="judging.error.value" @retry="judging.retry()" />
          <FeedbackPanel :feedback="mentor.feedback.value" :loading="mentor.pending.value" :error="outcomeError || mentor.error.value" :connection-label="mentor.status.value" :saving-outcome="savingOutcome" :review-state="reviewState" @mark-resolved="markFeedbackResolved(true)" @mark-stuck="markFeedbackResolved(false)" />
        </section>
      </div>
      <SubmissionHistory ref="history" :disabled="judgingLocked" @select="item => judging.resume('submission', item)" />
    </main>
  </div>
</template>
