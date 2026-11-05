<script setup>
import { useI18n } from '../i18n'
import SafeMarkdown from './SafeMarkdown.vue'

const { t } = useI18n()
defineProps({ problem: { type: Object, required: true } })
</script>
<template>
  <section class="card statement-card">
    <p class="eyebrow">{{ problem.topic }} · {{ t(problem.difficulty) }} · {{ t('Version {version}', { version: problem.versionNumber }) }}</p>
    <h1>{{ problem.title }}</h1>
    <SafeMarkdown :content="problem.statement" />
    <template v-for="[field, title] in [['inputFormat','Input'],['outputFormat','Output'],['constraintsText','Constraints']]" :key="field">
      <h3 v-if="problem[field]">{{ t(title) }}</h3>
      <SafeMarkdown v-if="problem[field]" :content="problem[field]" />
    </template>
    <h3>{{ t('Public samples') }}</h3>
    <div v-for="sample in problem.samples" :key="sample.position" class="sample-pair">
      <div><strong>{{ t('Input {number}', { number: sample.position + 1 }) }}</strong><pre>{{ sample.inputData }}</pre></div>
      <div><strong>{{ t('Expected output') }}</strong><pre>{{ sample.expectedOutput }}</pre></div>
    </div>
  </section>
</template>
