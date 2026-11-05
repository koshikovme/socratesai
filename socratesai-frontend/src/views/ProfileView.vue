<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useI18n } from '../i18n'
import AppHeader from '../components/AppHeader.vue'
import { useAuthStore } from '../stores/auth'
import { fetchMyProfile, fetchMyProgress, updateMyProfile, updateMySettings } from '../services/userService'
import { applyTheme, getStoredTheme, toggleTheme } from '../services/themeService'
import { apiErrorMessage } from '../services/errors'
import { registerSessionCleanup } from '../services/sessionLifecycle'

const { t } = useI18n()
const auth = useAuthStore()
const controller = new AbortController()
const profile = ref(null)
const progress = ref(null)
const loading = ref(false)
const saving = ref(false)
const savingTheme = ref(false)
const editing = ref(false)
const error = ref('')
const saveError = ref('')
const saved = ref(false)
const theme = ref(applyTheme(getStoredTheme()))
const form = reactive({ fullName: '', email: '', university: '', groupName: '', currentPassword: '', profileRevision: '' })
const student = computed(() => (profile.value?.role || auth.role) === 'STUDENT')
const busy = computed(() => loading.value || saving.value || savingTheme.value)
const emailChanged = computed(() => form.email.trim().toLowerCase() !== profile.value?.email)
const tasks = computed(() => progress.value?.tasks || [])
const summary = computed(() => progress.value?.summary || {})

function resetForm() {
  Object.assign(form, { fullName: profile.value?.fullName || '', email: profile.value?.email || '',
    university: profile.value?.university || '', groupName: profile.value?.groupName || '',
    currentPassword: '', profileRevision: profile.value?.profileRevision || '' })
}
function edit() { resetForm(); saveError.value = ''; saved.value = false; editing.value = true }
function cancel() { resetForm(); editing.value = false; saveError.value = '' }

async function load() {
  loading.value = true; error.value = ''; saved.value = false
  try {
    if (student.value) {
      const result = await fetchMyProgress({ signal: controller.signal })
      if (controller.signal.aborted) return
      progress.value = result; profile.value = result.profile
    } else {
      const result = await fetchMyProfile({ signal: controller.signal })
      if (controller.signal.aborted) return
      profile.value = result
    }
    auth.refreshProfile(profile.value)
    if (profile.value.darkMode !== undefined) theme.value = applyTheme(profile.value.darkMode ? 'dark' : 'light')
    resetForm(); saveError.value = ''; editing.value = false
  } catch (failure) { if (!controller.signal.aborted) error.value = apiErrorMessage(failure) }
  finally { loading.value = false }
}

async function save() {
  if (busy.value) return
  saving.value = true; saveError.value = ''; saved.value = false
  const payload = { fullName: form.fullName.trim(), email: form.email.trim().toLowerCase(), university: form.university.trim(),
    groupName: form.groupName.trim(), profileRevision: form.profileRevision,
    ...(emailChanged.value ? { currentPassword: form.currentPassword } : {}) }
  try {
    const updated = await updateMyProfile(payload, { signal: controller.signal })
    if (controller.signal.aborted || !auth.refreshProfile(updated)) return
    profile.value = updated; resetForm(); editing.value = false; saved.value = true
  } catch (failure) { if (!controller.signal.aborted) saveError.value = apiErrorMessage(failure) }
  finally { form.currentPassword = ''; saving.value = false }
}

async function toggleThemeMode() {
  if (busy.value) return
  const previous = theme.value
  theme.value = toggleTheme(); savingTheme.value = true; error.value = ''
  try {
    const updated = await updateMySettings({ darkMode: theme.value === 'dark' }, { signal: controller.signal })
    if (!controller.signal.aborted) profile.value = updated
  } catch (failure) {
    if (!controller.signal.aborted) { theme.value = applyTheme(previous); error.value = apiErrorMessage(failure) }
  } finally { savingTheme.value = false }
}

function clear() {
  controller.abort(); profile.value = null; progress.value = null; editing.value = false
  Object.keys(form).forEach(key => { form[key] = '' }); error.value = ''; saveError.value = ''; saved.value = false
}
const unregister = registerSessionCleanup(clear)
onBeforeUnmount(() => { clear(); unregister() })
onMounted(load)
</script>

<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content profile-page">
      <section class="card profile-hero">
        <div>
          <p class="eyebrow">{{ t('My profile') }}</p>
          <h2>{{ profile?.fullName || auth.fullName }}</h2>
          <p class="profile-subtitle">{{ profile?.email || auth.email }}</p>
          <div class="profile-meta-row">
            <span>{{ t(profile?.role || auth.role) }}</span>
            <span>{{ profile?.university || t('University not set') }}</span>
            <span>{{ profile?.groupName || t('Group not set') }}</span>
          </div>
        </div>
        <div class="profile-actions">
          <button class="primary-btn" :disabled="busy || !profile || editing" @click="edit">{{ t('Edit profile') }}</button>
          <button class="ghost-btn" :disabled="busy || editing" @click="load">{{ t(loading ? 'Refreshing...' : 'Refresh') }}</button>
          <button class="ghost-btn" :disabled="busy || !profile" @click="toggleThemeMode">{{ t(theme === 'dark' ? 'Use light mode' : 'Use dark mode') }}</button>
        </div>
      </section>

      <p v-if="error" role="alert" class="error-text">{{ t(error) }}</p>
      <p v-if="saved" role="status" class="profile-success">{{ t('Profile updated.') }}</p>
      <section v-if="loading && !profile" class="card"><p class="muted">{{ t('Loading profile...') }}</p></section>

      <section v-if="editing" class="card profile-editor">
        <h2>{{ t('Account information') }}</h2>
        <p class="muted">{{ t('Your name and study information help your teacher identify your submissions. Your role is managed by an administrator.') }}</p>
        <form :aria-label="t('Edit profile')" @submit.prevent="save">
          <fieldset :disabled="saving" class="profile-fields">
            <label for="profile-name">{{ t('Full name') }}<input id="profile-name" v-model="form.fullName" name="fullName" autocomplete="name" maxlength="120" required /></label>
            <label for="profile-email">{{ t('Email') }}<input id="profile-email" v-model="form.email" name="email" type="email" autocomplete="email" maxlength="120" required /></label>
            <label for="profile-university">{{ t('University') }}<input id="profile-university" v-model="form.university" name="university" autocomplete="organization" maxlength="120" /></label>
            <label for="profile-group">{{ t('Study group') }}<input id="profile-group" v-model="form.groupName" name="groupName" maxlength="80" /></label>
            <label v-if="emailChanged" for="profile-password">{{ t('Current password') }}<input id="profile-password" v-model="form.currentPassword" name="currentPassword" type="password" autocomplete="current-password" maxlength="100" required /></label>
          </fieldset>
          <p v-if="emailChanged" class="muted">{{ t('Confirm your current password to change your sign-in email. Your password stays the same.') }}</p>
          <p v-if="saveError" role="alert" class="error-text">{{ t(saveError) }}</p>
          <div class="profile-actions">
            <button class="primary-btn" type="submit" :disabled="busy">{{ t(saving ? 'Saving...' : 'Save changes') }}</button>
            <button class="ghost-btn" type="button" :disabled="saving" @click="cancel">{{ t('Cancel') }}</button>
            <button v-if="saveError" class="ghost-btn" type="button" :disabled="busy" @click="load">{{ t('Reload profile') }}</button>
          </div>
        </form>
      </section>

      <template v-if="profile && !student">
        <section class="card profile-guide">
          <p class="eyebrow">{{ t('Teaching') }}</p>
          <h2>{{ t('Your teaching workspace') }}</h2>
          <p>{{ t('Open a problem or collection in Teaching, then select Student results to review attempts, verdicts and submitted code.') }}</p>
          <p class="muted">{{ t('Only student submissions appear in these results. Staff and operational test accounts are excluded. Accepted submissions determine progress; no manual grading is needed.') }}</p>
          <div class="profile-actions">
            <router-link class="primary-btn" to="/teacher">{{ t('Open teaching workspace') }}</router-link>
            <router-link v-if="profile.role === 'ADMIN'" class="ghost-btn" to="/admin/users">{{ t('Manage user roles') }}</router-link>
          </div>
        </section>
      </template>

      <template v-if="profile && student && progress">
        <section class="profile-stats">
          <div class="stat-tile"><span>{{ t('Solved tasks') }}</span><strong>{{ summary.solvedTasks ?? 0 }}</strong><small>{{ t('Accepted graded submissions') }}</small></div>
          <div class="stat-tile"><span>{{ t('Attempted tasks') }}</span><strong>{{ summary.attemptedTasks ?? 0 }}</strong><small>{{ t('Problems with a submitted solution') }}</small></div>
          <div class="stat-tile"><span>{{ t('Submissions') }}</span><strong>{{ summary.totalAttempts ?? 0 }}</strong><small>{{ t('Run samples and mentor requests are not submissions.') }}</small></div>
        </section>
        <section class="card progress-panel task-progress-panel">
          <p class="eyebrow">{{ t('Tasks') }}</p><h2>{{ t('Progress by task') }}</h2>
          <p class="muted">{{ t('A task is solved only after Submit receives ACCEPTED. Mentor hints and sample runs help you practice but do not change this status.') }}</p>
          <div v-if="tasks.length" class="task-progress-list">
            <div v-for="task in tasks" :key="task.taskId" class="task-progress-row">
              <div><h3>{{ task.title }}</h3><p class="muted">{{ task.topic }} · {{ t(task.difficulty) }}</p><p class="task-progress-meta">{{ t('{count} formal submissions', { count: task.attempts }) }}</p></div>
              <div class="task-progress-side">
                <span class="signal-pill" :class="task.status === 'SOLVED' ? 'signal-good' : 'signal-neutral'">{{ t(task.status === 'SOLVED' ? 'Solved' : 'In progress') }}</span>
                <router-link v-if="task.openable" class="ghost-btn" :to="`/problems/${task.taskId}`">{{ t('Open') }}</router-link>
                <span v-else class="compact-chip">{{ t('Unavailable') }}</span>
              </div>
            </div>
          </div>
          <p v-else class="muted">{{ t('No graded attempts yet. Submit a solution to record judged progress.') }}</p>
          <router-link class="ghost-btn" to="/dashboard">{{ t('Open problem catalog') }}</router-link>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.profile-page > * { min-width: 0; }
.profile-hero > div { min-width: 0; }
.profile-hero h2 { overflow-wrap: anywhere; }
.profile-editor h2, .profile-guide h2 { margin-top: 0; }
.profile-fields { border: 0; margin: 1.25rem 0; padding: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.profile-fields label { display: grid; gap: .5rem; min-width: 0; }
.profile-fields input { width: 100%; min-width: 0; }
.profile-guide p { max-width: 75ch; line-height: 1.65; }
.profile-success { color: var(--accent); }
.profile-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.profile-subtitle { overflow-wrap: anywhere; }
.profile-meta-row span { overflow-wrap: anywhere; }
@media (max-width: 900px) { .profile-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .profile-fields, .profile-stats { grid-template-columns: 1fr; } }
</style>
