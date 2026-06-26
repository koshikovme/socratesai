<template>
  <div class="page-shell">
    <AppHeader />

    <main class="page-content profile-page">
      <section class="card profile-hero">
        <div>
          <p class="eyebrow">Student profile</p>
          <h2>{{ profile.fullName || auth.fullName || 'Student' }}</h2>
          <p class="profile-subtitle">{{ profile.email || auth.email }}</p>
          <div class="profile-meta-row">
            <span>{{ profile.role || auth.role || 'STUDENT' }}</span>
            <span>{{ profile.university || 'University not set' }}</span>
            <span>{{ profile.groupName || 'Group not set' }}</span>
          </div>
        </div>

        <div class="profile-actions">
          <button class="ghost-btn" :disabled="loading" @click="loadProgress">
            {{ loading ? 'Refreshing...' : 'Refresh' }}
          </button>
          <button class="primary-btn" @click="toggleThemeMode">
            {{ theme === 'dark' ? 'Use light mode' : 'Use dark mode' }}
          </button>
        </div>
      </section>

      <section v-if="loading && !progress" class="card">
        <p class="muted">Loading progress...</p>
      </section>

      <section v-else-if="error" class="card">
        <p class="error-text">{{ error }}</p>
      </section>

      <template v-else-if="progress">
        <section class="profile-stats">
          <div v-for="metric in metrics" :key="metric.label" class="stat-tile">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.detail }}</small>
          </div>
        </section>

        <div class="profile-layout">
          <section class="card progress-panel task-progress-panel">
            <div class="section-head">
              <div>
                <p class="eyebrow">Tasks</p>
                <h2>Progress by task</h2>
              </div>
            </div>

            <div v-if="tasks.length" class="task-progress-list">
              <div v-for="task in tasks" :key="task.taskId" class="task-progress-row">
                <div>
                  <h3>{{ task.title }}</h3>
                  <p class="muted">{{ task.topic }} / {{ task.difficulty }}</p>
                  <p class="task-progress-meta">
                    {{ task.attempts }} attempts / {{ task.feedbackCount }} feedback events / best {{ task.bestTestsPassed }} tests
                  </p>
                </div>
                <div class="task-progress-side">
                  <span class="signal-pill" :class="task.status === 'SOLVED' ? 'signal-good' : 'signal-neutral'">
                    {{ task.status === 'SOLVED' ? 'Solved' : 'In progress' }}
                  </span>
                  <router-link v-if="task.openable" class="ghost-btn" :to="`/tasks/${task.taskId}`">Open</router-link>
                  <span v-else class="compact-chip">External</span>
                </div>
              </div>
            </div>

            <p v-else class="muted">No attempts yet. Open a task and ask the mentor or submit code.</p>
          </section>

          <section class="card progress-panel">
            <p class="eyebrow">Policy behavior</p>
            <h2>Feedback actions</h2>
            <div class="bar-list">
              <div v-for="[label, count] in actionEntries" :key="label" class="bar-row">
                <div class="bar-row-head">
                  <span>{{ formatLabel(label) }}</span>
                  <strong>{{ count }}</strong>
                </div>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ width: barWidth(count, actionTotal) }"></div>
                </div>
              </div>
            </div>

            <p class="eyebrow secondary-eyebrow">Analyzer signals</p>
            <div class="compact-chip-list">
              <span v-for="[label, count] in errorEntries" :key="label" class="compact-chip">
                {{ formatLabel(label) }}: {{ count }}
              </span>
            </div>
          </section>

          <section class="card progress-panel recent-panel">
            <p class="eyebrow">Recent activity</p>
            <h2>Latest mentor events</h2>
            <div v-if="recentActivity.length" class="activity-list">
              <div v-for="item in recentActivity" :key="`${item.taskId}-${item.createdAt}-${item.attemptNo}`" class="activity-row">
                <div>
                  <strong>{{ item.taskTitle }}</strong>
                  <p class="muted">
                    {{ formatLabel(item.feedbackAction) }} / {{ formatLabel(item.errorType) }}
                  </p>
                </div>
                <div class="activity-side">
                  <span>{{ item.testsPassed ?? 0 }}/{{ (item.testsPassed ?? 0) + (item.testsFailed ?? 0) }} tests</span>
                  <small>{{ formatDate(item.createdAt) }}</small>
                </div>
              </div>
            </div>
            <p v-else class="muted">No recent activity yet.</p>
          </section>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { useAuthStore } from '../stores/auth'
import { fetchMyProgress, updateMySettings } from '../services/userService'
import { applyTheme, getStoredTheme, toggleTheme } from '../services/themeService'

const auth = useAuthStore()
const progress = ref(null)
const loading = ref(false)
const error = ref('')
const theme = ref(applyTheme(getStoredTheme()))

const profile = computed(() => progress.value?.profile || {})
const summary = computed(() => progress.value?.summary || {})
const tasks = computed(() => progress.value?.tasks || [])
const recentActivity = computed(() => progress.value?.recentActivity || [])
const actionEntries = computed(() => Object.entries(progress.value?.feedbackActions || {}))
const errorEntries = computed(() => Object.entries(progress.value?.errorTypes || {}))
const actionTotal = computed(() => actionEntries.value.reduce((sum, [, count]) => sum + Number(count), 0))

const metrics = computed(() => [
  {
    label: 'Solved tasks',
    value: `${summary.value.solvedTasks ?? 0}/${summary.value.attemptedTasks ?? 0}`,
    detail: `${formatPercent(summary.value.solveRate)} solve rate`
  },
  {
    label: 'Attempts',
    value: summary.value.totalAttempts ?? 0,
    detail: `${summary.value.activeSessions ?? 0} active sessions`
  },
  {
    label: 'Feedback events',
    value: summary.value.totalFeedback ?? 0,
    detail: `${summary.value.resolvedInteractions ?? 0} marked resolved`
  },
  {
    label: 'Mean latency',
    value: `${Math.round(summary.value.meanLatencyMs ?? 0)} ms`,
    detail: summary.value.lastActivityAt ? `Last: ${formatDate(summary.value.lastActivityAt)}` : 'No activity yet'
  }
])

async function loadProgress() {
  loading.value = true
  error.value = ''

  try {
    progress.value = await fetchMyProgress()
    if (progress.value?.profile?.darkMode !== undefined) {
      theme.value = applyTheme(progress.value.profile.darkMode ? 'dark' : 'light')
    }
  } catch (e) {
    error.value = e?.response?.data?.message || 'Failed to load student progress'
  } finally {
    loading.value = false
  }
}

async function toggleThemeMode() {
  theme.value = toggleTheme()

  try {
    const updatedProfile = await updateMySettings({ darkMode: theme.value === 'dark' })
    if (progress.value) {
      progress.value = {
        ...progress.value,
        profile: updatedProfile
      }
    }
  } catch (e) {
    error.value = e?.response?.data?.message || 'Theme changed locally, but could not be saved'
  }
}

function formatPercent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`
}

function formatLabel(value) {
  if (!value) return 'N/A'
  return String(value)
      .toLowerCase()
      .split('_')
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ')
}

function barWidth(count, total) {
  if (!total) return '0%'
  return `${Math.max(6, Math.round((Number(count) / total) * 100))}%`
}

function formatDate(value) {
  if (!value) return 'N/A'
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

onMounted(loadProgress)
</script>
