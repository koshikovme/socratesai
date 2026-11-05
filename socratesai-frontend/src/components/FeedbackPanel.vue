<template>
  <div class="feedback-stack">
    <section class="card feedback-card">
      <div class="feedback-head">
        <div>
          <p class="eyebrow">Mentor feedback</p>
          <h3>{{ actionLabel }}</h3>
        </div>
        <span class="signal-pill" :class="executionSignal">
          {{ executionLabel }}
        </span>
      </div>

      <p v-if="feedback" class="policy-mode">{{ modeLabel }}</p>
      <p v-if="feedback?.verdict === 'RUNTIME_ERROR' && feedback?.compileSuccess" class="muted">Compilation succeeded, but the program failed when it started or ran. Compilation alone does not mean execution succeeded.</p>
      <p v-if="feedback" class="feedback-text">{{ feedback.feedbackText }}</p>
      <p v-else-if="loading" class="muted">Generating mentor feedback...</p>
      <p v-else-if="error" role="alert" class="error-text">{{ error }}</p>
      <p v-else class="muted">Ask the mentor or keep typing with realtime assist enabled.</p>

      <div v-if="feedback" class="feedback-meta-grid">
        <div class="meta-card">
          <span class="meta-label">Evidence</span>
          <strong>{{ evidenceLabel }}</strong>
        </div>
        <div class="meta-card">
          <span class="meta-label">Suggested feedback focus</span>
          <strong>{{ focusLabel }}</strong>
        </div>
        <div class="meta-card">
          <span class="meta-label">Tests</span>
          <strong>{{ feedback.executionKnown === false || feedback.testsPassed == null ? 'Not executed' : `${feedback.testsPassed} passed / ${feedback.testsFailed} failed` }}</strong>
        </div>
        <div class="meta-card">
          <span class="meta-label">Source</span>
          <strong>{{ sourceLabel }}</strong>
        </div>
      </div>
    </section>

    <section class="card feedback-card feedback-support">
      <div class="feedback-head compact">
        <div>
          <p class="eyebrow">Your feedback</p>
          <h3>Was this helpful?</h3>
        </div>
        <span class="signal-pill signal-neutral">{{ connectionLabel }}</span>
      </div>

      <p v-if="error && feedback" class="error-text">{{ error }}</p>
      <p v-else class="muted">
        You can record whether this hint helped. This does not change your judged progress.
      </p>

      <div class="feedback-actions">
        <button
            class="primary-btn"
            :disabled="!canReview || savingOutcome"
            @click="$emit('mark-resolved')"
        >
          {{ savingOutcome && reviewState === 'resolved' ? 'Saving...' : 'It helped' }}
        </button>
        <button
            class="ghost-btn"
            :disabled="!canReview || savingOutcome"
            @click="$emit('mark-stuck')"
        >
          {{ savingOutcome && reviewState === 'stuck' ? 'Saving...' : 'Still stuck' }}
        </button>
      </div>

      <p v-if="reviewState === 'resolved'" class="success-text">Marked as helpful.</p>
      <p v-if="reviewState === 'stuck'" class="muted">Marked as not resolved yet.</p>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  feedback: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  connectionLabel: {
    type: String,
    default: 'Socket idle'
  },
  savingOutcome: {
    type: Boolean,
    default: false
  },
  reviewState: {
    type: String,
    default: ''
  }
})

defineEmits(['mark-resolved', 'mark-stuck'])

const actionLabel = computed(() => {
  if (!props.feedback?.action) return 'Awaiting guidance'
  return props.feedback.action
      .toLowerCase()
      .split('_')
      .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join(' ')
})

const sourceLabel = computed(() => {
  const transport = props.feedback?.transport === 'WS' ? 'Realtime' : 'Request'
  if (!props.feedback?.feedbackSource) return transport
  const source = { gemini: 'Gemini', execution: 'Judged result', onboarding: 'Getting started' }[props.feedback.feedbackSource] || props.feedback.feedbackSource
  return `${transport} / ${source}`
})

const modeLabel = computed(() => ({ ML: 'Python ML classification · Gemini hint', GEMINI: 'Gemini classification and hint', READY: 'AI mentor ready', JUDGED: 'Verified submission result' }[props.feedback?.policyMode] || 'Mentor feedback'))
const evidenceLabel = computed(() => ({ STATIC_HEURISTIC: 'Code only · no execution', SAMPLE_RUN: 'Public sample run', JUDGED_SUBMISSION: 'Graded submission', SUBMISSION: 'Graded submission' }[props.feedback?.evidenceOrigin] || 'No execution evidence'))
const focusLabel = computed(() => ({ syntax_repair: 'Syntax and program structure', execution_safety: 'Startup and runtime', semantic_debug: 'Logic and output', efficiency_review: 'Time and memory', accepted: 'Review and refinement' }[props.feedback?.mentorState] || 'Ready for your code'))
const executionLabel = computed(() => {
  const value = props.feedback
  if (!value?.executionKnown && value?.compileSuccess == null) return 'Code review only'
  return { RUNTIME_ERROR: 'Runtime error', COMPILATION_ERROR: 'Compile error', TIME_LIMIT_EXCEEDED: 'Time limit exceeded',
    MEMORY_LIMIT_EXCEEDED: 'Memory limit exceeded', OUTPUT_LIMIT_EXCEEDED: 'Output limit exceeded', WRONG_ANSWER: 'Wrong answer',
    ACCEPTED: value?.evidenceOrigin === 'JUDGED_SUBMISSION' ? 'Accepted' : 'Samples passed' }[value?.verdict]
    || (value?.compileSuccess === false ? 'Compile error' : 'Compilation succeeded')
})
const executionSignal = computed(() => props.feedback?.verdict && props.feedback.verdict !== 'ACCEPTED' ? 'signal-error'
  : props.feedback?.verdict === 'ACCEPTED' ? 'signal-good' : 'signal-neutral')

const canReview = computed(() => Boolean(props.feedback?.interactionId))
</script>
