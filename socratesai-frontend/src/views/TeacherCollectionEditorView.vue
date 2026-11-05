<script setup>
import { useI18n } from '../i18n'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import SafeMarkdown from '../components/SafeMarkdown.vue'
import { teacherService } from '../services/teacherService'
import { blankCollectionDraft, collectionDraft, collectionRequest } from '../services/authoringForms'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'
import '../components/teacher/authoring.css'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const record = ref(null)
const form = ref(blankCollectionDraft())
const saved = ref(JSON.stringify(form.value))
const busy = ref(false)
const catalogBusy = ref(false)
const error = ref('')
const notice = ref('')
const catalog = ref({ items: [], page: 0, totalElements: 0 })
let controller = new AbortController()
let catalogController
const dirty = computed(() => JSON.stringify(form.value) !== saved.value)
const archived = computed(() => record.value?.status === 'ARCHIVED')
function applyRecord(value) { record.value = value; form.value = collectionDraft(value); saved.value = JSON.stringify(form.value) }
function clear() {
  controller.abort(); catalogController?.abort()
  record.value = null; form.value = blankCollectionDraft(); saved.value = JSON.stringify(form.value)
  catalog.value = { items: [], page: 0, totalElements: 0 }
}
const unregister = registerSessionCleanup(clear)
onBeforeUnmount(() => { unregister(); clear() })
onBeforeRouteLeave(() => !dirty.value || window.confirm(t('Leave without saving this collection?')))
onBeforeRouteUpdate((to, from) => to.params.id === from.params.id || !dirty.value || window.confirm(t('Leave without saving this collection?')))

async function loadCatalog(page = 0) {
  catalogController?.abort()
  const current = new AbortController(); catalogController = current
  catalogBusy.value = true
  try {
    const result = await teacherService.publishedProblems(page, { signal: current.signal })
    if (!current.signal.aborted) catalog.value = result
  } catch (failure) { if (!current.signal.aborted) error.value = apiErrorMessage(failure) }
  finally { if (!current.signal.aborted) catalogBusy.value = false }
}
watch(() => route.params.id, async () => {
  controller.abort(); controller = new AbortController()
  record.value = null; form.value = blankCollectionDraft(); saved.value = JSON.stringify(form.value)
  error.value = ''; notice.value = ''; busy.value = true
  const signal = controller.signal
  try {
    if (route.params.id) {
      const result = await teacherService.collection(route.params.id, { signal })
      if (signal.aborted) return
      applyRecord(result)
    }
    await loadCatalog()
  } catch (failure) { if (!signal.aborted) error.value = apiErrorMessage(failure) }
  finally { if (!signal.aborted) busy.value = false }
}, { immediate: true })

function addProblem(item) {
  const existing = form.value.problems.find((member) => member.problemId === item.id)
  if (existing) { existing.problemVersionId = item.problemVersionId; existing.title = item.title; return }
  if (form.value.problems.length < 200) form.value.problems.push({ problemId: item.id, problemVersionId: item.problemVersionId, title: item.title })
}
function membership(item) { return form.value.problems.find((member) => member.problemId === item.id) }
function move(index, direction) {
  const target = index + direction
  if (target < 0 || target >= form.value.problems.length) return
  const reordered = [...form.value.problems]
  ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
  form.value.problems = reordered
}
async function action(operation) {
  if (busy.value || controller.signal.aborted) return
  const signal = controller.signal
  busy.value = true; error.value = ''; notice.value = ''
  try { await operation({ signal }) }
  catch (failure) { if (!signal.aborted) error.value = apiErrorMessage(failure) }
  finally { if (!signal.aborted) busy.value = false }
}
async function save() {
  if (route.params.id && !record.value) return
  await action(async (config) => {
    const created = !record.value
    const result = await (created ? teacherService.createCollection(collectionRequest(form.value), config) : teacherService.saveCollection(record.value.id, collectionRequest(form.value), config))
    if (config.signal.aborted) return
    applyRecord(result); notice.value = 'Collection and problem order saved.'
    if (created) await router.replace(`/teacher/collections/${result.id}`)
  })
}
async function publish() {
  if (dirty.value || !record.value || !form.value.problems.length) return
  await action(async (config) => {
    const result = await teacherService.publishCollection(record.value.id, record.value.lockVersion, config)
    if (!config.signal.aborted) { applyRecord(result); notice.value = 'Collection published.' }
  })
}
async function archive() {
  if (!window.confirm(t('Archive this collection and remove it from the student catalog?'))) return
  await action(async (config) => {
    const result = await teacherService.archiveCollection(record.value.id, record.value.lockVersion, config)
    if (!config.signal.aborted) applyRecord(result)
  })
}
async function remove() {
  if (!window.confirm(t('Permanently delete this draft collection?'))) return
  await action(async (config) => {
    await teacherService.deleteCollection(record.value.id, record.value.lockVersion, config)
    if (config.signal.aborted) return
    saved.value = JSON.stringify(form.value); await router.push('/teacher')
  })
}
async function reload() {
  if (dirty.value && !window.confirm(t('Discard local changes and load the latest collection?'))) return
  await action(async (config) => { const result = await teacherService.collection(record.value.id, config); if (!config.signal.aborted) applyRecord(result) })
}
</script>

<template>
  <div class="page-shell"><AppHeader />
    <main class="page-content authoring-page">
      <section class="card">
        <RouterLink to="/teacher">{{ t('← Teaching workspace') }}</RouterLink>
        <div class="section-head"><h1>{{ record ? t('Collection #{id}', { id: record.id }) : t('New collection') }}</h1><span v-if="record" class="compact-chip">{{ t(record.status) }}</span></div>
        <p v-if="error" class="error-text" role="alert">{{ t(error) }} <button v-if="record" class="ghost-btn" :disabled="busy" @click="reload">{{ t('Reload latest') }}</button></p>
        <p v-if="notice" role="status">{{ t(notice) }}</p>
        <p v-if="dirty" class="muted">{{ t('Unsaved changes. Saving commits the entire order atomically.') }}</p>
        <div class="authoring-actions">
          <RouterLink v-if="record" class="ghost-btn" :to="`/teacher/collections/${record.id}/results`">{{ t('Student results') }}</RouterLink>
          <button v-if="record && !archived" class="primary-btn" :disabled="busy || dirty || !form.problems.length || record.status === 'PUBLISHED'" @click="publish">{{ t('Publish collection') }}</button>
          <button v-if="record && !archived" class="ghost-btn" :disabled="busy || dirty" @click="archive">{{ t('Archive collection') }}</button>
          <button v-if="record?.status === 'DRAFT'" class="ghost-btn" :disabled="busy" @click="remove">{{ t('Delete draft') }}</button>
        </div>
      </section>
      <form class="authoring-form" @submit.prevent="save">
        <fieldset class="card" :disabled="busy || archived">
          <legend>{{ t('Collection details') }}</legend>
          <label>{{ t('Title') }}<input v-model="form.title" required maxlength="180" name="title" /></label>
          <label>{{ t('Description') }}<textarea v-model="form.description" maxlength="50000" rows="5" /></label>
          <label>{{ t('Visibility') }}<select v-model="form.visibility" :aria-label="t('Visibility')"><option value="AUTHENTICATED">{{ t('Signed-in students') }}</option><option value="PUBLIC">{{ t('Public') }}</option></select></label>
          <p class="muted">{{ t('Public collections require every pinned problem version to be public.') }}</p>
          <details><summary>{{ t('Safe description preview') }}</summary><SafeMarkdown :content="form.description" /></details>
        </fieldset>
        <fieldset class="card" :disabled="busy || archived">
          <legend>{{ t('Ordered problems · {count} / 200', { count: form.problems.length }) }}</legend>
          <p v-if="!form.problems.length" class="muted">{{ t('Add published versions from the catalog below.') }}</p>
          <ol class="authoring-list">
            <li v-for="(member, index) in form.problems" :key="member.problemId">
              <strong>{{ member.title }}</strong> · #{{ member.problemId }} · {{ t('pinned version ID {id}', { id: member.problemVersionId }) }}
              <div class="authoring-actions">
                <button type="button" class="ghost-btn" :disabled="index === 0" :aria-label="t('Move {title} up', { title: member.title })" @click="move(index, -1)">{{ t('↑ Up') }}</button>
                <button type="button" class="ghost-btn" :disabled="index === form.problems.length - 1" :aria-label="t('Move {title} down', { title: member.title })" @click="move(index, 1)">{{ t('↓ Down') }}</button>
                <button type="button" class="ghost-btn" :aria-label="t('Remove {title}', { title: member.title })" @click="form.problems.splice(index, 1)">{{ t('Remove') }}</button>
              </div>
            </li>
          </ol>
        </fieldset>
        <button v-if="!archived" type="submit" class="primary-btn" :disabled="busy || (!!route.params.id && !record)">{{ t(busy ? 'Saving…' : 'Save collection and order') }}</button>
      </form>
      <section v-if="!archived" class="card">
        <h2>{{ t('Published problem catalog') }}</h2>
        <p class="muted">{{ t('Existing members keep their pinned versions. Use “Update pinned version” to adopt the latest publication.') }}</p>
        <p v-if="catalogBusy" role="status">{{ t('Loading published problems…') }}</p>
        <p v-else-if="!catalog.items.length">{{ t('No published problems are available.') }}</p>
        <div v-else class="authoring-scroll">
          <table class="authoring-table">
            <thead><tr><th scope="col">{{ t('Problem') }}</th><th scope="col">{{ t('Version ID') }}</th><th scope="col">{{ t('Action') }}</th></tr></thead>
            <tbody><tr v-for="item in catalog.items" :key="item.id">
              <td>{{ item.title }}</td><td>{{ item.problemVersionId }}</td>
              <td><button class="ghost-btn" :disabled="busy || membership(item)?.problemVersionId === item.problemVersionId || (!membership(item) && form.problems.length >= 200)" @click="addProblem(item)">{{ t(membership(item) ? (membership(item).problemVersionId === item.problemVersionId ? 'Added' : 'Update pinned version') : 'Add problem') }}</button></td>
            </tr></tbody>
          </table>
        </div>
        <nav class="authoring-actions" :aria-label="t('Published problem pages')">
          <button class="ghost-btn" :disabled="catalogBusy || catalog.page === 0" @click="loadCatalog(catalog.page - 1)">{{ t('Previous') }}</button>
          <span>{{ t('Page {page} · {total} problems', { page: catalog.page + 1, total: catalog.totalElements }) }}</span>
          <button class="ghost-btn" :disabled="catalogBusy || (catalog.page + 1) * 20 >= catalog.totalElements" @click="loadCatalog(catalog.page + 1)">{{ t('Next') }}</button>
        </nav>
      </section>
    </main>
  </div>
</template>
