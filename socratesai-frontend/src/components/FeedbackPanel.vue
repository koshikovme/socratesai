<template>
  <div class="feedback-stack">
    <section class="card feedback-card">
      <div class="feedback-head">
        <div>
          <p class="eyebrow">Mentor feedback</p>
          <h3>{{ actionLabel }}</h3>
        </div>
        <span class="signal-pill" :class="feedback?.compileSuccess === true ? 'signal-good' : 'signal-neutral'">
          {{ feedback?.compileSuccess == null ? 'Execution unknown' : (feedback.compileSuccess ? 'Compiled' : 'Compile error') }}
        </span>
      </div>

      <p v-if="feedback" class="policy-mode">{{ feedback.policyMode === 'ML' ? 'ML' : 'RULE' }}<span v-if="feedback.fallbackReason"> · {{ fallbackLabel }}</span></p>
      <p v-if="feedback" class="feedback-text">{{ feedback.feedbackText }}</p>
      <p v-else-if="loading" class="muted">Generating mentor feedback...</p>
      <p v-else class="muted">Ask the mentor or keep typing with realtime assist enabled.</p>

      <div v-if="feedback" class="feedback-meta-grid">
        <div class="meta-card">
          <span class="meta-label">Evidence</span>
          <strong>{{ feedback.evidenceOrigin || 'Unknown' }}</strong>
        </div>
        <div class="meta-card">
          <span class="meta-label">Mentor state</span>
          <strong>{{ feedback.mentorState || 'Unavailable' }}</strong>
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
          <p class="eyebrow">Feedback loop</p>
          <h3>Improve the next label</h3>
        </div>
        <span class="signal-pill signal-neutral">{{ connectionLabel }}</span>
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>
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
      <p v-if="feedback?.sessionId" class="session-note">Session {{ feedback.sessionId }}</p>
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
  const transport = props.feedback?.transport === 'WS' ? 'Realtime' : 'Manual'
  if (!props.feedback?.feedbackSource) return transport
  return `${transport} / ${props.feedback.feedbackSource}`
})

const fallbackLabel = computed(() => ({ UNSUPPORTED_LANGUAGE: 'Rules for this language', ML_UNAVAILABLE: 'Model unavailable', LOW_CONFIDENCE: 'Low model confidence', ML_DISABLED: 'Model disabled', CONFIGURED_RULE: 'Rule mode configured', EMPTY_SOURCE: 'Empty buffer' }[props.feedback?.fallbackReason] || 'Rule fallback'))

const canReview = computed(() => Boolean(props.feedback?.interactionId))
</script>
