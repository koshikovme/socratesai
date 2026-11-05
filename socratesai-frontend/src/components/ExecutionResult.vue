<script setup>
import { useI18n } from '../i18n'
import { computed } from 'vue'
import { judgingSummary } from '../services/judgingService'

const { t } = useI18n()
const props = defineProps({ result: Object, operation: Object, phase: String, error: String })
defineEmits(['retry'])
const summary = computed(() => judgingSummary(props.result))
// Current API returns metric-only cases. Never spread/render arbitrary response fields.
const cases = computed(() => props.result?.cases ?? [])
</script>

<template>
  <section v-if="operation" class="card" aria-live="polite" :aria-label="t('Execution result')">
    <p class="eyebrow">{{ t(operation.kind === 'sample' ? 'Public sample run' : 'Graded submission') }}</p>
    <h3>{{ t(phase === 'unavailable' ? 'Result unavailable or expired' : (result?.retryScheduled ? 'Recovering — retry scheduled' : (summary?.verdict || summary?.status || (phase === 'creating' ? 'Sending…' : 'Waiting for status')))) }}</h3>
    <p class="muted">{{ operation.languageKey }} · {{ t('Version {version}', { version: operation.problemVersionId }) }}<span v-if="operation.codeRevision !== undefined"> · {{ t('Revision {revision}', { revision: operation.codeRevision }) }}</span></p>
    <p v-if="summary?.attemptNumber">{{ t('Server attempt {number}', { number: summary.attemptNumber }) }}</p>
    <p v-if="operation.kind === 'sample'">{{ t('Samples provide feedback. Only an accepted graded submission counts as solved.') }}</p>
    <p v-if="summary?.testsTotal != null">{{ t('{passed} / {total} tests passed', { passed: summary.testsPassed, total: summary.testsTotal }) }}</p>
    <p v-if="summary?.timeConsumedMs != null">{{ t('Time: {time} ms · Memory: {memory} bytes', { time: summary.timeConsumedMs, memory: summary.memoryConsumedBytes ?? '—' }) }}</p>
    <ul v-if="cases.length" class="case-results">
      <li v-for="item in cases" :key="item.position">
        {{ t(item.hidden ? 'Hidden test' : 'Sample') }} {{ item.position + 1 }}: {{ t(item.verdict || item.status) }}
        <span> · {{ item.timeConsumedMs ?? '—' }} {{ t('ms') }} · {{ item.memoryConsumedBytes ?? '—' }} {{ t('bytes') }}</span>
      </li>
    </ul>
    <p v-if="cases.length" class="muted">{{ t('The server exposes verdicts and resource metrics; diagnostic output is unavailable.') }}</p>
    <p v-if="error" role="alert" class="error-text">{{ t(error) }}</p>
    <button v-if="phase === 'paused'" class="primary-btn" @click="$emit('retry')">{{ t(operation.id ? 'Resume status check' : 'Retry same request safely') }}</button>
  </section>
</template>
