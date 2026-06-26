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
          <button class="primary-btn" :disabled="loading || submitting" @click="askMentor">
            {{ loading ? 'Analyzing...' : 'Ask Mentor' }}
          </button>
          <button class="primary-btn submit-code-btn" :disabled="submitting || loading" @click="submitCode">
            {{ submitting ? 'Submitting...' : 'Submit code' }}
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
              Ask Mentor is formative help. Submit code records a summative attempt and appears in your profile progress.
              Realtime nudges do not increment session attempts.
            </p>
          </div>
        </section>

        <section class="right-panel">
          <section v-if="submissionResult" class="card submit-result-card">
            <div class="feedback-head compact">
              <div>
                <p class="eyebrow">Submission result</p>
                <h3>{{ submissionResult.title }}</h3>
              </div>
              <span class="signal-pill" :class="submissionResult.accepted ? 'signal-good' : 'signal-warn'">
                {{ submissionResult.status }}
              </span>
            </div>
            <p class="muted">{{ submissionResult.message }}</p>
            <div class="feedback-meta-grid">
              <div class="meta-card">
                <span class="meta-label">Compile</span>
                <strong>{{ submissionResult.compileSuccess ? 'Success' : 'Failed' }}</strong>
              </div>
              <div class="meta-card">
                <span class="meta-label">Tests</span>
                <strong>{{ submissionResult.testsPassed }} passed / {{ submissionResult.testsFailed }} failed</strong>
              </div>
            </div>
            <router-link class="ghost-btn submit-profile-link" to="/profile">
              View profile progress
            </router-link>
          </section>

          <FeedbackPanel
              :feedback="feedback"
              :loading="loading || submitting"
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
const submitting = ref(false)
const error = ref('')
const submissionResult = ref(null)
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

async function submitCode() {
  if (!task.value) return

  const currentAttempt = attemptNo.value
  submitting.value = true
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
    submissionResult.value = buildSubmissionResult(response)

    if (submissionResult.value.accepted && response.interactionId) {
      await updateInteractionResult(response.interactionId, {
        resolvedAfterFeedback: true,
        fixedAfterMs: null,
        feedbackHelpful: true,
        feedbackRating: 5,
        studentComment: 'Accepted submission',
        repeatedSameErrorAfterFeedback: false
      })
      reviewState.value = 'resolved'
    }
  } catch (e) {
    error.value = e?.response?.data?.message || 'Failed to submit code'
  } finally {
    submitting.value = false
  }
}

function buildSubmissionResult(response) {
  const testsPassed = Number(response?.testsPassed ?? 0)
  const testsFailed = Number(response?.testsFailed ?? 0)
  const compileSuccess = Boolean(response?.compileSuccess)
  const accepted = compileSuccess && testsPassed > 0 && testsFailed === 0

  if (accepted) {
    return {
      accepted,
      compileSuccess,
      testsPassed,
      testsFailed,
      status: 'Accepted',
      title: 'Task solved',
      message: 'This attempt passed the available analyzer checks and is counted as solved in your profile.'
    }
  }

  if (!compileSuccess) {
    return {
      accepted,
      compileSuccess,
      testsPassed,
      testsFailed,
      status: 'Compile error',
      title: 'Code needs syntax fixes',
      message: 'The submission was recorded, but the code does not compile yet.'
    }
  }

  return {
    accepted,
    compileSuccess,
    testsPassed,
    testsFailed,
    status: 'Tests failed',
    title: 'Not accepted yet',
    message: 'The submission was recorded. Use the mentor feedback, fix the issue, and submit again.'
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
