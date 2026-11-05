<script setup>
import { useI18n } from '../i18n'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { teacherService } from '../services/teacherService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'
import '../components/teacher/authoring.css'

const { t } = useI18n()

const tab = ref('problems')
const page = ref(0)
const items = ref([])
const total = ref(0)
const busy = ref(false)
const error = ref('')
let controller
async function load(nextPage = page.value) {
  controller?.abort()
  const current = new AbortController()
  controller = current
  busy.value = true
  error.value = ''
  try {
    const result = await teacherService[tab.value](nextPage, { signal: current.signal })
    if (current.signal.aborted) return
    items.value = result.items
    total.value = result.totalElements
    page.value = result.page
  } catch (failure) {
    if (!current.signal.aborted) error.value = apiErrorMessage(failure)
  } finally {
    if (!current.signal.aborted) busy.value = false
  }
}
function selectTab(value) { tab.value = value; items.value = []; load(0) }
const clear = () => { controller?.abort(); items.value = []; total.value = 0 }
const unregister = registerSessionCleanup(clear)
onBeforeUnmount(() => { unregister(); clear() })
onMounted(() => load())
</script>

<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content authoring-page">
      <section class="card">
        <div class="section-head">
          <div><p class="eyebrow">{{ t('Teaching workspace') }}</p><h1>{{ t('Your content') }}</h1></div>
          <div class="authoring-actions">
            <RouterLink class="primary-btn" to="/teacher/problems/new">{{ t('New problem') }}</RouterLink>
            <RouterLink class="ghost-btn" to="/teacher/collections/new">{{ t('New collection') }}</RouterLink>
          </div>
        </div>
        <p class="muted">{{ t('Prepare drafts, verify reference solutions and publish versioned tasks. Administrators can manage the entire catalog.') }}</p>
        <div class="authoring-actions" :aria-label="t('Content type')">
          <button class="ghost-btn" :aria-pressed="tab === 'problems'" @click="selectTab('problems')">{{ t('Problems') }}</button>
          <button class="ghost-btn" :aria-pressed="tab === 'collections'" @click="selectTab('collections')">{{ t('Collections') }}</button>
        </div>
      </section>
      <section class="card" :aria-busy="busy">
        <p v-if="error" class="error-text" role="alert">{{ t(error) }}</p>
        <p v-if="busy" role="status">{{ t('Loading content…') }}</p>
        <p v-else-if="!items.length">{{ t(tab === 'problems' ? 'No problems yet. Create a draft to get started.' : 'No collections yet. Create a draft to get started.') }}</p>
        <div v-else class="authoring-scroll">
          <table class="authoring-table">
            <thead><tr><th scope="col">{{ t('Title') }}</th><th scope="col">{{ t('Status') }}</th><th scope="col">{{ t('Visibility') }}</th><th scope="col">{{ t('Action') }}</th></tr></thead>
            <tbody><tr v-for="item in items" :key="item.id">
              <td>{{ item.title }}</td><td>{{ t(item.status) }}</td><td>{{ t(item.visibility) }}</td>
              <td><RouterLink :to="`/teacher/${tab}/${item.id}`">{{ t('Open') }}<span class="muted"> #{{ item.id }}</span></RouterLink></td>
            </tr></tbody>
          </table>
        </div>
        <nav class="authoring-actions" :aria-label="t('Content pages')">
          <button class="ghost-btn" :disabled="busy || page === 0" @click="load(page - 1)">{{ t('Previous') }}</button>
          <span>{{ t('Page {page} · {total} total', { page: page + 1, total }) }}</span>
          <button class="ghost-btn" :disabled="busy || (page + 1) * 20 >= total" @click="load(page + 1)">{{ t('Next') }}</button>
        </nav>
      </section>
    </main>
  </div>
</template>
