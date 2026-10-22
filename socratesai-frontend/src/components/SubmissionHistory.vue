<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchSubmissions } from '../services/judgingService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'
defineProps({ disabled: Boolean })
defineEmits(['select'])
const page = ref(0)
const data = ref({ items: [], totalElements: 0 })
const loading = ref(false)
const error = ref('')
let controller
let epoch = 0
function clear() { loading.value = false; epoch++; controller?.abort(); data.value = { items: [], totalElements: 0 }; error.value = '' }
const unregister = registerSessionCleanup(clear)
async function load(nextPage = page.value) {
  controller?.abort()
  controller = new AbortController()
  const current = ++epoch
  loading.value = true
  error.value = ''
  try {
    const response = await fetchSubmissions({ page: nextPage, size: 10, signal: controller.signal })
    if (current !== epoch) return
    data.value = response
    page.value = nextPage
  } catch (e) { if (current === epoch) error.value = apiErrorMessage(e) }
  finally { if (current === epoch) loading.value = false }
}
defineExpose({ refresh: () => load(0) })
onMounted(() => load())
onBeforeUnmount(() => { clear(); unregister() })
</script>
<template>
  <section class="card" aria-label="Submission history">
    <h2>My submission history</h2>
    <p class="muted">All problems · submitted code is not stored in this browser.</p>
    <button class="ghost-btn" :disabled="loading" @click="load()">Refresh history</button>
    <p v-if="error" role="alert" class="error-text">{{ error }}</p>
    <p v-if="!loading && !data.items.length">No graded submissions yet.</p>
    <ul class="history-list">
      <li v-for="item in data.items" :key="item.id">
        <RouterLink :to="`/problems/${item.problemId}?version=${item.problemVersionId}`">Problem {{ item.problemId }}</RouterLink>
        · {{ item.languageKey }} · Attempt {{ item.attemptNumber }} · {{ item.verdict || item.status }}
        <button class="ghost-btn" :disabled="disabled" @click="$emit('select', item)">Check status</button>
      </li>
    </ul>
    <nav class="pagination" aria-label="History pages">
      <button class="ghost-btn" :disabled="page === 0 || loading" @click="load(page - 1)">Previous</button>
      <span>Page {{ page + 1 }}</span>
      <button class="ghost-btn" :disabled="(page + 1) * 10 >= data.totalElements || loading" @click="load(page + 1)">Next</button>
    </nav>
  </section>
</template>
