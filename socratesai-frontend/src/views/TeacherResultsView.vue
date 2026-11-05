<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from '../i18n'
import AppHeader from '../components/AppHeader.vue'
import CatalogPagination from '../components/CatalogPagination.vue'
import ExecutionResult from '../components/ExecutionResult.vue'
import { teacherService } from '../services/teacherService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'
import '../components/teacher/authoring.css'

const route = useRoute()
const { t, dateLocale } = useI18n()
const kind = computed(() => route.meta.resultsKind)
const resource = ref(null)
const data = ref(null)
const page = ref(0)
const size = ref(20)
const search = ref('')
const query = ref('')
const verdict = ref('')
const status = ref('')
const problemId = ref('')
const selectedStudent = ref(null)
const loading = ref(false)
const error = ref('')
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const automaticRefresh = ref(true)
let request, detailRequest, generation = 0
const verdicts = ['ACCEPTED', 'WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED']
const statuses = ['QUEUED', 'DISPATCHING', 'RUNNING', 'FINISHED', 'SYSTEM_ERROR']
const title = computed(() => resource.value?.title || resource.value?.version?.title || '')
const total = computed(() => data.value?.submissions.totalElements || 0)
const operation = computed(() => detail.value ? { kind: 'submission', problemVersionId: detail.value.submission.problemVersionId, languageKey: detail.value.submission.languageKey } : null)

function closeDetail() { detailRequest?.abort(); detail.value = null; detailError.value = ''; detailLoading.value = false }
async function load() {
  request?.abort(); closeDetail()
  const current = new AbortController(); request = current
  const epoch = ++generation
  loading.value = true; error.value = ''; data.value = null
  try {
    const params = { page: page.value, size: size.value,
      ...(query.value ? { query: query.value } : {}), ...(verdict.value ? { verdict: verdict.value } : {}),
      ...(status.value ? { status: status.value } : {}), ...(problemId.value ? { problemId: Number(problemId.value) } : {}),
      ...(selectedStudent.value ? { studentId: selectedStudent.value.id } : {}),
    }
    const result = await teacherService.results(kind.value, route.params.id, params, { signal: current.signal })
    if (epoch === generation && !current.signal.aborted) data.value = result
  } catch (failure) { if (!current.signal.aborted && epoch === generation) error.value = apiErrorMessage(failure) }
  finally { if (epoch === generation) loading.value = false }
}

async function loadResource() {
  resource.value = null; page.value = 0; query.value = ''; search.value = ''; problemId.value = ''; selectedStudent.value = null
  status.value = ''; verdict.value = ''
  request?.abort(); closeDetail(); generation++
  const current = new AbortController(); request = current
  loading.value = true; error.value = ''; data.value = null
  try {
    const result = await teacherService[kind.value === 'collections' ? 'collection' : 'problem'](route.params.id, { signal: current.signal })
    if (current.signal.aborted) return
    resource.value = result
    await load()
  } catch (failure) { if (!current.signal.aborted) { error.value = apiErrorMessage(failure); loading.value = false } }
}
async function review(id) {
  closeDetail()
  const current = new AbortController(); detailRequest = current
  detailLoading.value = true
  try {
    const result = await teacherService.submission(id, { signal: current.signal })
    if (!current.signal.aborted) detail.value = result
  } catch (failure) { if (!current.signal.aborted) detailError.value = apiErrorMessage(failure) }
  finally { if (!current.signal.aborted) detailLoading.value = false }
}
function filter() { query.value = search.value.trim(); page.value = 0; load() }
function student(row) { selectedStudent.value = { id: row.studentId, name: row.studentName }; page.value = 0; load() }
function allStudents() { selectedStudent.value = null; page.value = 0; load() }
function changePage(value) { page.value = value; load() }
function changeSize(value) { size.value = value; page.value = 0; load() }
function date(value) { return value ? new Intl.DateTimeFormat(dateLocale.value, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—' }
const refreshTimer = setInterval(() => {
  if (automaticRefresh.value && document.visibilityState === 'visible' && resource.value && !loading.value && !detail.value && !detailLoading.value) load()
}, 10000)
function clear() { clearInterval(refreshTimer); generation++; request?.abort(); closeDetail(); resource.value = null; data.value = null; loading.value = false; error.value = ''; selectedStudent.value = null }
const unregister = registerSessionCleanup(clear)
onBeforeUnmount(() => { unregister(); clear() })
watch(() => [route.params.id, kind.value], loadResource, { immediate: true })
</script>

<template>
  <div class="page-shell"><AppHeader />
    <main class="page-content authoring-page">
      <section class="card">
        <RouterLink :to="`/teacher/${kind}/${route.params.id}`">{{ t('Back to editor') }}</RouterLink>
        <div class="section-head"><div><p class="eyebrow">{{ t('Student results') }}</p><h1>{{ title }}</h1></div><button class="ghost-btn" :disabled="loading || !resource" @click="load">{{ t('Refresh') }}</button></div>
        <p class="muted">{{ t('Only student submissions are shown. Staff and operational test accounts are excluded.') }}</p>
        <p class="muted">{{ t('Results come from formal submissions. Sample runs and editor activity do not count as attempts. No separate manual grade is needed.') }}</p>
        <label class="auto-refresh"><input v-model="automaticRefresh" type="checkbox" /> {{ t('Refresh automatically every 10 seconds') }}</label>
        <p v-if="kind === 'collections'" class="muted">{{ t('Only submissions sent through this collection are shown, including historical versions. This is an activity view, not an enrollment roster.') }}</p>
        <p v-if="selectedStudent" class="success-text">{{ selectedStudent.name }} <button class="ghost-btn" @click="allStudents">{{ t('Show all students') }}</button></p>
      </section>
      <form v-if="resource" class="card authoring-grid" @submit.prevent="filter">
        <label>{{ t('Student name, email or group') }}<input v-model="search" maxlength="120" type="search" /></label>
        <label v-if="kind === 'collections'">{{ t('Problem') }}<select v-model="problemId"><option value="">{{ t('All problems') }}</option><option v-for="member in resource.problems" :key="member.problemId" :value="member.problemId">{{ member.title }}</option></select></label>
        <label>{{ t('Verdict') }}<select v-model="verdict"><option value="">{{ t('All verdicts') }}</option><option v-for="value in verdicts" :key="value" :value="value">{{ t(value) }}</option></select></label>
        <label>{{ t('Status') }}<select v-model="status"><option value="">{{ t('All statuses') }}</option><option v-for="value in statuses" :key="value" :value="value">{{ t(value) }}</option></select></label>
        <button class="primary-btn" :disabled="loading">{{ t('Apply filters') }}</button>
      </form>
      <p v-if="error" role="alert" class="error-text">{{ t(error) }}</p>
      <section v-if="data" class="results-summary" :aria-label="t('Results summary')">
        <div v-for="[key, label] in [['attempts', 'Formal attempts'], ['students', 'Students with submissions'], ['accepted', 'Accepted submissions'], ['pending', 'Pending submissions']]" :key="key" class="card"><span>{{ t(label) }}</span><strong>{{ data.summary[key] }}</strong></div>
      </section>
      <section class="card" :aria-busy="loading">
        <p v-if="loading" role="status">{{ t('Loading results…') }}</p>
        <template v-else-if="data">
          <p v-if="!data.submissions.items.length">{{ t('No formal submissions match these filters.') }}</p>
          <div v-else class="authoring-scroll">
            <table class="authoring-table">
              <thead><tr><th>{{ t('Student') }}</th><th>{{ t('Problem') }}</th><th>{{ t('Attempt') }}</th><th>{{ t('Verdict') }}</th><th>{{ t('Tests') }}</th><th>{{ t('Submitted at') }}</th><th>{{ t('Action') }}</th></tr></thead>
              <tbody><tr v-for="row in data.submissions.items" :key="row.id">
                <td><button class="student-filter" @click="student(row)">{{ row.studentName }}</button><small>{{ row.studentEmail }}</small><small v-if="row.groupName">{{ row.groupName }}</small></td>
                <td>{{ row.problemTitle }}<small>{{ t('Version {version}', { version: row.versionNumber }) }} · {{ row.languageKey }}</small></td>
                <td>{{ row.attemptNumber }}</td><td>{{ t(row.verdict || row.status) }}</td><td>{{ row.testsPassed }} / {{ row.testsTotal }}</td><td>{{ date(row.createdAt) }}</td>
                <td><button class="ghost-btn" @click="review(row.id)">{{ t('View attempt') }}</button></td>
              </tr></tbody>
            </table>
          </div>
          <CatalogPagination :page="page" :size="size" :total="total" :loading="loading" @page="changePage" @size="changeSize" />
        </template>
      </section>
      <section v-if="detail || detailLoading || detailError" class="card review-detail" :aria-label="t('Submission review')">
        <div class="section-head"><h2>{{ t('Submission review') }}</h2><button class="ghost-btn" @click="closeDetail">{{ t('Close') }}</button></div>
        <p v-if="detailLoading" role="status">{{ t('Loading attempt…') }}</p><p v-if="detailError" role="alert" class="error-text">{{ t(detailError) }}</p>
        <template v-if="detail">
          <p>{{ detail.student.fullName }} · {{ detail.student.email }}</p><h3>{{ detail.problemTitle }}</h3>
          <ExecutionResult :result="detail" :operation="operation" phase="complete" />
          <h3>{{ t('Submitted source code') }}</h3><pre><code>{{ detail.sourceCode }}</code></pre>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
.authoring-page > * { min-width: 0; }
.authoring-scroll { max-width: 100%; }
form label { display: grid; gap: 8px; }
form button { align-self: end; justify-self: start; }
.auto-refresh { display: flex; align-items: center; gap: 8px; }
.auto-refresh input { width: auto; }
.results-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
.results-summary strong { display: block; font-size: 26px; margin-top: 8px; }
td small { display: block; margin-top: 5px; opacity: .75; }
.student-filter { background: transparent; border: 0; padding: 0; color: var(--accent); cursor: pointer; text-align: left; font: inherit; text-decoration: underline; }
.review-detail { min-width: 0; }
</style>
