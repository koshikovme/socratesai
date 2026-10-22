<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import SafeMarkdown from '../components/SafeMarkdown.vue'
import { fetchCollection, fetchCollectionProgress } from '../services/catalogService'
import { apiErrorMessage } from '../services/errors'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const collection = ref(null)
const progress = ref(null)
const loading = ref(false)
const error = ref('')
const progressError = ref('')
let request

const members = computed(() => [...(collection.value?.problems ?? [])].sort((a, b) => a.position - b.position))
const solvedVersions = computed(() => new Set((progress.value?.problems ?? []).filter(item => item.solved === true)
  .map(item => `${item.problemId}:${item.problemVersionId}`)))

async function load() {
  request?.abort()
  const current = new AbortController()
  request = current
  collection.value = null
  progress.value = null
  error.value = ''
  progressError.value = ''
  if (!auth.token) { loading.value = false; return }
  loading.value = true
  const [details, judged] = await Promise.allSettled([
    fetchCollection(route.params.id, { signal: current.signal }),
    fetchCollectionProgress(route.params.id, { signal: current.signal })
  ])
  if (current.signal.aborted) return
  if (details.status === 'fulfilled') collection.value = details.value
  else error.value = apiErrorMessage(details.reason)
  if (judged.status === 'fulfilled') progress.value = judged.value
  else progressError.value = apiErrorMessage(judged.reason)
  loading.value = false
}

watch([() => route.params.id, () => auth.token], load, { immediate: true })
onBeforeUnmount(() => request?.abort())
</script>

<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content" :aria-busy="loading">
      <router-link to="/dashboard">← Catalog</router-link>
      <p v-if="loading" role="status">Loading collection…</p>
      <p v-else-if="error" class="error-text" role="alert">{{ error }}</p>
      <template v-else-if="collection">
        <section class="card collection-heading">
          <div class="section-head"><h1>{{ collection.title }}</h1><button class="ghost-btn" @click="load">Refresh progress</button></div>
          <SafeMarkdown :content="collection.description || ''" />
          <p v-if="progress" class="success-text" data-testid="collection-progress">{{ progress.solved }} of {{ progress.total }} solved</p>
          <p class="muted">Progress requires an accepted submission for the exact published version in this collection.</p>
          <p v-if="progressError" class="error-text" role="status">Progress unavailable: {{ progressError }}</p>
        </section>
        <ol class="collection-problems">
          <li v-for="problem in members" :key="`${problem.problemId}:${problem.problemVersionId}`" class="card">
            <div><h2>{{ problem.title }}</h2><p v-if="solvedVersions.has(`${problem.problemId}:${problem.problemVersionId}`)" class="success-text" data-testid="solved">✓ SOLVED</p></div>
            <router-link class="primary-btn" :to="{ path: `/problems/${problem.problemId}`, query: { version: String(problem.problemVersionId), collection: String(collection.id) } }">Open problem</router-link>
          </li>
        </ol>
        <p v-if="!members.length" class="muted">This collection has no problems yet.</p>
      </template>
      <button v-if="error" class="ghost-btn" @click="load">Retry</button>
    </main>
  </div>
</template>

<style scoped>
.collection-heading { margin-top: 20px; }
h1, h2 { margin: 0; }
h2 { font-size: 19px; }
.collection-problems { padding-left: 24px; display: grid; gap: 14px; }
.collection-problems li { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
@media (max-width: 640px) { .collection-problems li { align-items: flex-start; flex-direction: column; } }
</style>
