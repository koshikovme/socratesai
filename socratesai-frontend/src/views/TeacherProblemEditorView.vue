<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import SafeMarkdown from '../components/SafeMarkdown.vue'
import { teacherService } from '../services/teacherService'
import { blankProblemDraft, languageDefaults, problemDraft, problemRequest, validateProblemDraft, verificationAllowsPublish } from '../services/authoringForms'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'
import '../components/teacher/authoring.css'

const route = useRoute()
const router = useRouter()
const record = ref(null)
const form = ref(blankProblemDraft())
const saved = ref(JSON.stringify(form.value))
const languages = ref([])
const nextLanguage = ref('')
const reference = ref({})
const verification = ref(null)
const error = ref('')
const notice = ref('')
const busy = ref(false)
const verifying = ref(false)
const now = ref(Date.now())
let controller = new AbortController()
let pollTimer
let pollStarted = 0
let cleared = false
const clock = setInterval(() => { now.value = Date.now() }, 1000)
const dirty = computed(() => JSON.stringify(form.value) !== saved.value)
const editable = computed(() => !record.value || record.value.version.status === 'DRAFT')
const availableLanguages = computed(() => languages.value.filter((item) => !form.value.languages.some((selected) => selected.languageKey === item.languageKey)))
const canVerify = computed(() => !!record.value && editable.value && !dirty.value && form.value.languages.length > 0
  && form.value.tests.some((test) => test.hidden) && form.value.tests.some((test) => !test.hidden)
  && form.value.languages.every((language) => reference.value[language.languageKey]?.trim()))
const canPublish = computed(() => editable.value && verificationAllowsPublish(verification.value, record.value?.version.id, dirty.value, now.value))

function applyRecord(value) {
  record.value = value
  form.value = problemDraft(value)
  saved.value = JSON.stringify(form.value)
}
function stopVerification() { clearTimeout(pollTimer); verifying.value = false }
function clear() {
  cleared = true
  controller.abort()
  stopVerification()
  clearInterval(clock)
  reference.value = {}
  form.value = blankProblemDraft()
  saved.value = JSON.stringify(form.value)
  record.value = null
  verification.value = null
}
const unregister = registerSessionCleanup(clear)
onBeforeUnmount(() => { unregister(); clear() })
onBeforeRouteLeave(() => !dirty.value || window.confirm('Leave without saving this draft?'))
onBeforeRouteUpdate((to, from) => to.params.id === from.params.id || !dirty.value || window.confirm('Leave without saving this draft?'))
watch(() => route.params.id, async () => {
  controller.abort()
  controller = new AbortController()
  stopVerification()
  reference.value = {}; verification.value = null; error.value = ''; notice.value = ''
  record.value = null; form.value = blankProblemDraft(); saved.value = JSON.stringify(form.value)
  const signal = controller.signal
  busy.value = true
  try {
    const [catalog, problem] = await Promise.all([teacherService.languages({ signal }), route.params.id ? teacherService.problem(route.params.id, { signal }) : null])
    if (signal.aborted) return
    languages.value = catalog
    if (problem) applyRecord(problem)
  } catch (failure) { if (!signal.aborted) error.value = apiErrorMessage(failure) }
  finally { if (!signal.aborted) busy.value = false }
}, { immediate: true })
watch(dirty, (changed) => { if (changed) { verification.value = null; stopVerification() } })

function addLanguage() {
  if (!availableLanguages.value.some((language) => language.languageKey === nextLanguage.value)) return
  form.value.languages.push(languageDefaults(nextLanguage.value)); nextLanguage.value = ''
}
function removeLanguage(key) {
  form.value.languages = form.value.languages.filter((language) => language.languageKey !== key)
  delete reference.value[key]
}
async function action(operation) {
  if (busy.value || cleared) return
  const signal = controller.signal
  busy.value = true; error.value = ''; notice.value = ''
  try { await operation({ signal }) }
  catch (failure) { if (!signal.aborted) error.value = apiErrorMessage(failure) }
  finally { if (!signal.aborted) busy.value = false }
}
async function save() {
  if (route.params.id && !record.value) return
  const invalid = validateProblemDraft(form.value)
  if (invalid) { error.value = invalid; return }
  await action(async (config) => {
    const created = !record.value
    const result = await (created ? teacherService.createProblem(problemRequest(form.value), config) : teacherService.saveProblem(record.value.id, problemRequest(form.value), config))
    if (config.signal.aborted) return
    applyRecord(result); verification.value = null; notice.value = 'Draft saved. Verify all reference solutions before publication.'
    if (created) await router.replace(`/teacher/problems/${result.id}`)
  })
}
async function pollVerification(id, delay = 500) {
  if (Date.now() - pollStarted > 300000) { stopVerification(); notice.value = 'Verification is still pending. Check its status again.'; return }
  const signal = controller.signal
  try {
    const result = await teacherService.verification(record.value.id, id, { signal })
    if (signal.aborted || dirty.value || verification.value?.id !== id) return
    verification.value = result
    if (['FINISHED', 'SYSTEM_ERROR'].includes(result.status)) { stopVerification(); return }
    pollTimer = setTimeout(() => pollVerification(id, Math.min(delay * 2, 5000)), delay)
  } catch (failure) {
    if (!signal.aborted) { error.value = apiErrorMessage(failure); stopVerification() }
  }
}
async function verify() {
  if (!canVerify.value) return
  await action(async (config) => {
    const result = await teacherService.verify(record.value.id, record.value.lockVersion,
      form.value.languages.map(({ languageKey }) => ({ languageKey, sourceCode: reference.value[languageKey] })), config)
    if (config.signal.aborted) return
    verification.value = result; verifying.value = true; pollStarted = Date.now()
    await pollVerification(result.id)
  })
}
function resumeVerification() {
  verifying.value = true; pollStarted = Date.now(); pollVerification(verification.value.id)
}
async function publish() {
  if (!canPublish.value) return
  await action(async (config) => {
    const result = await teacherService.publishProblem(record.value.id, record.value.lockVersion, verification.value.id, config)
    if (config.signal.aborted) return
    applyRecord(result); reference.value = {}; verification.value = null; notice.value = 'Verified version published.'
  })
}
async function cloneDraft() {
  await action(async (config) => {
    const result = await teacherService.cloneDraft(record.value.id, record.value.lockVersion, config)
    if (!config.signal.aborted) { applyRecord(result); verification.value = null }
  })
}
async function archive() {
  if (!window.confirm('Archive this problem and remove it from the student catalog?')) return
  await action(async (config) => { const result = await teacherService.archiveProblem(record.value.id, record.value.lockVersion, config); if (!config.signal.aborted) applyRecord(result) })
}
async function removeDraft() {
  if (!window.confirm('Permanently delete this draft?')) return
  await action(async (config) => {
    await teacherService.deleteProblem(record.value.id, record.value.lockVersion, !!record.value.publishedVersionId, config)
    if (config.signal.aborted) return
    saved.value = JSON.stringify(form.value)
    await router.push('/teacher')
  })
}
async function reload() {
  if (dirty.value && !window.confirm('Discard your local changes and reload the latest draft?')) return
  await action(async (config) => { const result = await teacherService.problem(record.value.id, config); if (!config.signal.aborted) { applyRecord(result); verification.value = null } })
}
</script>

<template>
  <div class="page-shell"><AppHeader />
    <main class="page-content authoring-page">
      <section class="card">
        <RouterLink to="/teacher">← Teaching workspace</RouterLink>
        <div class="section-head">
          <h1>{{ record ? `Problem #${record.id}` : 'New problem' }}</h1>
          <span v-if="record" class="compact-chip">{{ record.status }} · version {{ record.version.versionNumber }} ({{ record.version.status }})</span>
        </div>
        <p v-if="error" class="error-text" role="alert">{{ error }} <button v-if="record" class="ghost-btn" :disabled="busy" @click="reload">Reload latest</button></p>
        <p v-if="notice" role="status">{{ notice }}</p>
        <div class="authoring-actions">
          <button v-if="record && !editable" class="primary-btn" :disabled="busy" @click="cloneDraft">Create new draft version</button>
          <button v-if="record && record.status !== 'ARCHIVED'" class="ghost-btn" :disabled="busy || verifying || dirty" @click="archive">Archive problem</button>
          <button v-if="record && editable" class="ghost-btn" :disabled="busy || verifying" @click="removeDraft">Delete draft</button>
          <span v-if="dirty" class="muted">Unsaved changes</span>
        </div>
      </section>
      <form class="authoring-form" @submit.prevent="save">
        <fieldset class="card" :disabled="busy || verifying || !editable">
          <legend>Problem statement</legend>
          <div class="authoring-grid">
            <label>Title<input v-model="form.title" required maxlength="180" name="title" /></label>
            <label>Topic<input v-model="form.topic" required maxlength="120" name="topic" /></label>
            <label>Difficulty<select v-model="form.difficulty" aria-label="Difficulty"><option>EASY</option><option>MEDIUM</option><option>HARD</option></select></label>
            <label>Visibility<select v-model="form.visibility" aria-label="Visibility"><option value="AUTHENTICATED">Signed-in students</option><option value="PUBLIC">Public</option></select></label>
          </div>
          <label>Statement<textarea v-model="form.statement" required maxlength="50000" rows="8" name="statement" /></label>
          <div class="authoring-grid">
            <label>Input format<textarea v-model="form.inputFormat" maxlength="50000" rows="3" /></label>
            <label>Output format<textarea v-model="form.outputFormat" maxlength="50000" rows="3" /></label>
          </div>
          <label>Constraints<textarea v-model="form.constraintsText" maxlength="50000" rows="3" /></label>
          <details><summary>Safe statement preview</summary><SafeMarkdown :content="form.statement" /></details>
          <label>Output comparison<select v-model="form.comparisonMode" aria-label="Output comparison"><option value="EXACT">Exact (CRLF normalized)</option><option value="IGNORE_TRAILING_LINE_ENDINGS">Ignore trailing line endings</option></select></label>
        </fieldset>
        <fieldset class="card" :disabled="busy || verifying || !editable">
          <legend>Languages and sandbox limits</legend>
          <p class="muted">Configured runtimes require successful reference verification before publication.</p>
          <div v-for="language in form.languages" :key="language.languageKey" class="authoring-entry" role="group" :aria-label="`${language.languageKey} configuration`">
            <div class="section-head"><h3>{{ language.languageKey }}</h3><button class="ghost-btn" type="button" @click="removeLanguage(language.languageKey)">Remove language</button></div>
            <div class="authoring-grid">
              <label>CPU time (ms)<input v-model.number="language.timeLimitMs" type="number" min="1" max="30000" required /></label>
              <label>Wall time (ms)<input v-model.number="language.wallTimeLimitMs" type="number" :min="language.timeLimitMs" max="60000" required /></label>
              <label>Memory (KiB)<input v-model.number="language.memoryLimitKb" type="number" min="1" max="1048576" required /></label>
              <label>Output (bytes)<input v-model.number="language.outputLimitBytes" type="number" min="1" max="65536" required /></label>
            </div>
            <label>Starter code<textarea v-model="language.starterCode" class="code-input" maxlength="20000" rows="5" spellcheck="false" /></label>
          </div>
          <div class="authoring-actions">
            <label>Add language<select v-model="nextLanguage" aria-label="Add language"><option value="">Choose runtime</option><option v-for="language in availableLanguages" :key="language.languageKey" :value="language.languageKey">{{ language.languageKey }} · {{ language.runtimeVersion }}</option></select></label>
            <button class="ghost-btn" type="button" :disabled="!nextLanguage || form.languages.length >= 10" @click="addLanguage">Add language</button>
          </div>
        </fieldset>
        <fieldset class="card" :disabled="busy || verifying || !editable">
          <legend>Public samples and hidden tests</legend>
          <p class="muted">At least one sample and one hidden test are required for publication. Only sample inputs and expected outputs are visible to students.</p>
          <div v-for="(test, index) in form.tests" :key="index" class="authoring-entry">
            <div class="section-head"><h3>Test {{ index + 1 }}</h3><button class="ghost-btn" type="button" @click="form.tests.splice(index, 1)">Remove test {{ index + 1 }}</button></div>
            <label class="check-label"><input v-model="test.hidden" type="checkbox" /> Hidden test</label>
            <div class="authoring-grid">
              <label>Input<textarea v-model="test.inputData" class="code-input" maxlength="65536" rows="4" spellcheck="false" /></label>
              <label>Expected output<textarea v-model="test.expectedOutput" class="code-input" maxlength="65536" rows="4" spellcheck="false" /></label>
            </div>
          </div>
          <button class="ghost-btn" type="button" :disabled="form.tests.length >= 100" @click="form.tests.push({ inputData: '', expectedOutput: '', hidden: false })">Add test</button>
        </fieldset>
        <button v-if="editable" class="primary-btn" :disabled="busy || verifying || (!!route.params.id && !record)" type="submit">{{ busy ? 'Saving…' : 'Save draft' }}</button>
      </form>
      <section v-if="record && editable" class="card authoring-form">
        <h2>Reference verification</h2>
        <p class="muted">Save the draft, then provide one reference solution for every configured language. Reference source is kept in this page’s memory and cleared when you leave.</p>
        <label v-for="language in form.languages" :key="language.languageKey">Reference source · {{ language.languageKey }}
          <textarea v-model="reference[language.languageKey]" class="code-input" rows="7" maxlength="20000" spellcheck="false" :disabled="busy || verifying" />
        </label>
        <div class="authoring-actions">
          <button class="ghost-btn" :disabled="busy || verifying || !canVerify" @click="verify">{{ verifying ? 'Verifying…' : 'Verify reference solution' }}</button>
          <button class="primary-btn" :disabled="busy || verifying || !canPublish" @click="publish">Publish verified version</button>
          <button v-if="verification && !verifying && !['FINISHED', 'SYSTEM_ERROR'].includes(verification.status)" class="ghost-btn" :disabled="busy || dirty" @click="resumeVerification">Check verification status</button>
        </div>
        <p v-if="verification" class="authoring-status" role="status">{{ verification.status }}{{ verification.verdict ? ` · ${verification.verdict}` : '' }} · {{ verification.testsPassed }} / {{ verification.testsTotal }} checks passed<span v-if="verification.verdict === 'ACCEPTED' && !canPublish"> · Verification expired or draft changed; verify again.</span></p>
        <p v-if="verification?.status === 'SYSTEM_ERROR'" class="error-text">Verification could not finish. Check sandbox availability and try again.</p>
      </section>
    </main>
  </div>
</template>
