<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content solve-page">
      <section v-if="task" class="card solve-hero">
        <div class="solve-hero-copy">
          <p class="eyebrow">Workspace</p>
          <h2>{{ task.title }}</h2>
          <p class="solve-meta">{{ task.topic }} / {{ task.language }} / {{ task.difficulty }}</p>
          <p class="muted">{{ task.description }}</p>
        </div>

        <div class="solve-hero-actions">
          <button class="primary-btn" :disabled="loading" @click="askMentor">
            {{ loading ? 'Analyzing...' : 'Ask Mentor' }}
          </button>
          <button class="ghost-btn" @click="toggleRealtime">
            {{ realtimeEnabled ? 'Realtime On' : 'Realtime Off' }}
          </button>
          <span class="connection-pill" :class="socketConnected ? 'live' : 'idle'">
            {{ socketConnected ? 'Socket live' : 'Socket offline' }}
          </span>
        </div>
      </section>

      <div class="solve-layout">
        <section class="left-panel">
          <CodeEditorPanel
              v-model="code"
              :language="task?.language || 'java'"
              :attempt-no="attemptNo"
          />

          <div class="card editor-note-card">
            <p class="eyebrow">Workflow</p>
            <p class="muted">
              Manual requests count as attempts. Realtime messages are lightweight nudges and do not increment session attempts.
            </p>
          </div>
        </section>

        <section class="right-panel">
          <FeedbackPanel
              :feedback="feedback"
              :loading="loading"
              :error="error"
              :connection-label="connectionLabel"
              :saving-outcome="savingOutcome"
              :review-state="reviewState"
              @mark-resolved="markFeedbackResolved(true)"
              @mark-stuck="markFeedbackResolved(false)"
          />
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppHeader from '../components/AppHeader.vue'
import FeedbackPanel from '../components/FeedbackPanel.vue'
import CodeEditorPanel from '../components/CodeEditorPanel.vue'
import { fetchTaskById } from '../services/taskService'
import { getCachedLeetCodeTaskByRouteId } from '../services/leetcodeService'
import { requestMentorAnalysis, updateInteractionResult } from '../services/mentorService'
import { createMentorSocket, disconnectSocket, sendCodeUpdate } from '../services/websocketService'

const route = useRoute()
const auth = useAuthStore()

const task = ref(null)
const code = ref('')
const attemptNo = ref(1)
const feedback = ref(null)
const loading = ref(false)
const error = ref('')
const socketConnected = ref(false)
const realtimeEnabled = ref(true)
const realtimeAvailable = ref(true)
const savingOutcome = ref(false)
const reviewState = ref('')
const socketFailureCount = ref(0)
let intentionalSocketDisconnect = false
let debounceTimer = null

const resolvedTaskId = computed(() => {
  if (task.value?.externalId) {
    return Number(task.value.externalId)
  }
  return Number(route.params.id)
})

async function loadTask() {
  const routeId = String(route.params.id)
  task.value = getCachedLeetCodeTaskByRouteId(routeId) || await fetchTaskById(routeId)
  code.value = task.value?.starterCode || ''
}

function decorateFeedback(payload, transport) {
  return payload ? { ...payload, transport } : null
}

async function askMentor() {
  if (!task.value) return

  const currentAttempt = attemptNo.value
  loading.value = true
  error.value = ''

  try {
    const response = await requestMentorAnalysis({
      studentId: Number(auth.userId),
      taskId: resolvedTaskId.value,
      language: task.value?.language || 'java',
      code: code.value,
      attemptNo: currentAttempt
    })

    feedback.value = decorateFeedback(response, 'REST')
    reviewState.value = ''
    attemptNo.value = currentAttempt + 1
  } catch (e) {
    error.value = e?.response?.data?.message || 'Failed to get mentor feedback'
  } finally {
    loading.value = false
  }
}

async function markFeedbackResolved(resolvedAfterFeedback) {
  if (!feedback.value?.interactionId || savingOutcome.value) return

  savingOutcome.value = true
  error.value = ''

  try {
    await updateInteractionResult(feedback.value.interactionId, {
      resolvedAfterFeedback,
      fixedAfterMs: null
    })
    reviewState.value = resolvedAfterFeedback ? 'resolved' : 'stuck'
  } catch (e) {
    error.value = e?.response?.data?.message || 'Failed to save feedback outcome'
  } finally {
    savingOutcome.value = false
  }
}

function sendRealtimeUpdate() {
  console.log(realtimeEnabled.value, socketConnected.value, task.value)
  if (!realtimeEnabled.value || !socketConnected.value || !task.value) return

  sendCodeUpdate({
    studentId: Number(auth.userId),
    taskId: resolvedTaskId.value,
    language: task.value?.language || 'java',
    code: code.value,
    attemptNo: attemptNo.value
  })
}

function toggleRealtime() {
  if (!realtimeAvailable.value && !realtimeEnabled.value) {
    socketFailureCount.value = 0
    realtimeAvailable.value = true
    connectRealtime()
    return
  }

  realtimeEnabled.value = !realtimeEnabled.value
  if (!realtimeEnabled.value) {
    clearTimeout(debounceTimer)
    intentionalSocketDisconnect = true
    disconnectSocket()
    socketConnected.value = false
  } else {
    connectRealtime()
  }
}

function handleSocketFailure() {
  socketConnected.value = false
  socketFailureCount.value += 1

  if (socketFailureCount.value >= 3) {
    realtimeEnabled.value = false
    realtimeAvailable.value = false
    disconnectSocket()
  }
}

function connectRealtime() {
  if (!realtimeEnabled.value || !realtimeAvailable.value || !auth.token) return

  intentionalSocketDisconnect = false
  createMentorSocket({
    token: auth.token,
    onFeedback: (message) => {
      feedback.value = decorateFeedback(message, 'WS')
      reviewState.value = ''
    },
    onConnect: () => {
      socketConnected.value = true
      socketFailureCount.value = 0
    },
    onDisconnect: () => {
      if (intentionalSocketDisconnect) {
        intentionalSocketDisconnect = false
        socketConnected.value = false
        return
      }
      handleSocketFailure()
    },
    onError: () => {
      handleSocketFailure()
    }
  })
}

onMounted(async () => {
  await loadTask()
  connectRealtime()
})

watch(code, () => {
  clearTimeout(debounceTimer)
  if (!realtimeEnabled.value) return

  debounceTimer = setTimeout(() => {
    sendRealtimeUpdate()
  }, 1500)
})

onBeforeUnmount(() => {
  intentionalSocketDisconnect = true
  disconnectSocket()
  clearTimeout(debounceTimer)
})

const connectionLabel = computed(() => {
  if (!realtimeAvailable.value) return 'Realtime unavailable'
  if (!realtimeEnabled.value) return 'Realtime paused'
  return socketConnected.value ? 'Realtime connected' : 'Realtime reconnecting'
})
</script>
