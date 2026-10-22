<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import CatalogPagination from '../components/CatalogPagination.vue'
import TaskList from '../components/TaskList.vue'
import { fetchCollections, fetchJudgedProgress, fetchProblems, solvedProblemIds } from '../services/catalogService'
import { apiErrorMessage } from '../services/errors'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const kind = ref('problems')
const page = ref(0)
const size = ref(20)
const items = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const progressError = ref('')
const progressReady = ref(false)
const solvedIds = ref(new Set())
const search = ref('')
const difficulty = ref('')
const status = ref('')
let catalogRequest
let progressRequest

const filtered = computed(() => items.value.filter(item => {
  const text = `${item.title} ${item.topic ?? ''}`.toLocaleLowerCase()
  return text.includes(search.value.trim().toLocaleLowerCase())
    && (kind.value !== 'problems' || !difficulty.value || item.difficulty === difficulty.value)
    && (kind.value !== 'problems' || !status.value || (status.value === 'SOLVED') === solvedIds.value.has(String(item.id)))
}))

async function loadCatalog() {
  catalogRequest?.abort()
  const request = new AbortController()
  catalogRequest = request
  error.value = ''
  items.value = []
  total.value = 0
  if (!auth.token) { loading.value = false; return }
  loading.value = true
  try {
    const fetchPage = kind.value === 'problems' ? fetchProblems : fetchCollections
    const result = await fetchPage({ page: page.value, size: size.value, signal: request.signal })
    if (request.signal.aborted) return
    items.value = result.items
    total.value = result.totalElements
  } catch (cause) {
    if (!request.signal.aborted) error.value = apiErrorMessage(cause)
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

async function loadProgress() {
  progressRequest?.abort()
  const request = new AbortController()
  progressRequest = request
  solvedIds.value = new Set()
  progressReady.value = false
  progressError.value = ''
  if (!auth.token) return
  try {
    const progress = await fetchJudgedProgress({ signal: request.signal })
    if (!request.signal.aborted) {
      solvedIds.value = solvedProblemIds(progress)
      progressReady.value = progress.progressSource === 'JUDGED_SUBMISSION'
      if (!progressReady.value) progressError.value = 'Judged progress is not available.'
    }
  } catch (cause) {
    if (!request.signal.aborted) progressError.value = apiErrorMessage(cause)
  }
}

function setKind(value) {
  kind.value = value
  page.value = 0
  search.value = ''
  difficulty.value = ''
  status.value = ''
}

function setSize(value) { size.value = value; page.value = 0 }
function refresh() { loadCatalog(); loadProgress() }
watch([kind, page, size, () => auth.token], loadCatalog, { immediate: true })
watch(() => auth.token, loadProgress, { immediate: true })
onBeforeUnmount(() => { catalogRequest?.abort(); progressRequest?.abort() })
</script>

<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content">
      <div class="section-head">
        <div><p class="eyebrow">Practice programming</p><h1>Problem catalog</h1><p class="muted">Published problems and curated collections.</p></div>
        <button class="ghost-btn" :disabled="loading" @click="refresh">Refresh</button>
      </div>
      <div class="catalog-tabs" aria-label="Catalog type">
        <button :class="kind === 'problems' ? 'primary-btn' : 'ghost-btn'" :aria-pressed="kind === 'problems'" @click="setKind('problems')">Problems</button>
        <button :class="kind === 'collections' ? 'primary-btn' : 'ghost-btn'" :aria-pressed="kind === 'collections'" @click="setKind('collections')">Collections</button>
      </div>
      <section class="card catalog-filters" aria-label="Filter current page">
        <label>Filter this page<input v-model="search" type="search" maxlength="200" placeholder="Title or topic" /></label>
        <template v-if="kind === 'problems'">
          <label>Difficulty<select v-model="difficulty"><option value="">All difficulties</option><option>EASY</option><option>MEDIUM</option><option>HARD</option></select></label>
          <label>Progress<select v-model="status" :disabled="!progressReady"><option value="">All problems</option><option value="SOLVED">Solved</option><option value="UNSOLVED">Not yet solved</option></select></label>
        </template>
        <p class="muted filter-help">Filters apply to the current page. Use the page controls to explore the full catalog.</p>
      </section>
      <p v-if="progressError" role="status" class="error-text">Progress unavailable: {{ progressError }}</p>
      <p v-if="error" role="alert" class="error-text">{{ error }}</p>
      <section :aria-busy="loading" aria-label="Catalog results">
        <p v-if="loading" role="status">Loading catalog…</p>
        <template v-else-if="!error">
          <p v-if="!filtered.length" class="muted">{{ items.length ? 'No matches on this page. Adjust the filters or go to another page.' : 'No published items yet.' }}</p>
          <TaskList v-if="kind === 'problems'" :tasks="filtered" :solved-ids="solvedIds" />
          <div v-else class="task-grid">
            <article v-for="item in filtered" :key="item.id" class="card collection-card">
              <h2>{{ item.title }}</h2>
              <router-link class="primary-btn" :to="`/collections/${item.id}`">Open collection</router-link>
            </article>
          </div>
        </template>
      </section>
      <CatalogPagination :page="page" :size="size" :total="total" :loading="loading" @page="page = $event" @size="setSize" />
    </main>
  </div>
</template>

<style scoped>
h1 { margin: 0; }
.catalog-tabs { display: flex; gap: 10px; margin-bottom: 18px; }
.catalog-filters { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 16px; margin-bottom: 24px; }
label { display: grid; gap: 8px; }
.filter-help { grid-column: 1 / -1; margin: 0; font-size: 14px; }
.collection-card h2 { font-size: 19px; margin-top: 0; }
@media (max-width: 640px) { .catalog-filters { grid-template-columns: 1fr; } }
</style>
