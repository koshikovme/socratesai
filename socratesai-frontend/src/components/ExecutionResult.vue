<script setup>
import { computed } from 'vue'
import { judgingSummary } from '../services/judgingService'
const props = defineProps({ result: Object, operation: Object, phase: String, error: String })
defineEmits(['retry'])
const summary = computed(() => judgingSummary(props.result))
// Current API returns metric-only cases. Never spread/render arbitrary response fields.
const cases = computed(() => props.result?.cases ?? [])
</script>

<template>
  <section v-if="operation" class="card" aria-live="polite" aria-label="Execution result">
    <p class="eyebrow">{{ operation.kind === 'sample' ? 'Public sample run' : 'Graded submission' }}</p>
    <h3>{{ phase === 'unavailable' ? 'Result unavailable or expired' : (result?.retryScheduled ? 'Recovering — retry scheduled' : (summary?.verdict || summary?.status || (phase === 'creating' ? 'Sending…' : 'Waiting for status'))) }}</h3>
    <p class="muted">{{ operation.languageKey }} · Version {{ operation.problemVersionId }}<span v-if="operation.codeRevision !== undefined"> · Revision {{ operation.codeRevision }}</span></p>
    <p v-if="summary?.attemptNumber">Server attempt {{ summary.attemptNumber }}</p>
    <p v-if="operation.kind === 'sample'">Samples provide feedback. Only an accepted graded submission counts as solved.</p>
    <p v-if="summary?.testsTotal != null">{{ summary.testsPassed }} / {{ summary.testsTotal }} tests passed</p>
    <p v-if="summary?.timeConsumedMs != null">Time: {{ summary.timeConsumedMs }} ms · Memory: {{ summary.memoryConsumedBytes ?? '—' }} bytes</p>
    <ul v-if="cases.length" class="case-results">
      <li v-for="item in cases" :key="item.position">
        {{ item.hidden ? 'Hidden test' : 'Sample' }} {{ item.position + 1 }}: {{ item.verdict || item.status }}
        <span> · {{ item.timeConsumedMs ?? '—' }} ms · {{ item.memoryConsumedBytes ?? '—' }} bytes</span>
      </li>
    </ul>
    <p v-if="cases.length" class="muted">The server exposes verdicts and resource metrics; diagnostic output is unavailable.</p>
    <p v-if="error" role="alert" class="error-text">{{ error }}</p>
    <button v-if="phase === 'paused'" class="primary-btn" @click="$emit('retry')">{{ operation.id ? 'Resume status check' : 'Retry same request safely' }}</button>
  </section>
</template>
