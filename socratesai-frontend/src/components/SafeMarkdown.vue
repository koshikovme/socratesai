<script setup>
defineProps({ content: { type: String, default: '' } })
</script>

<template>
  <!-- Restricted Markdown is displayed as text. Vue escapes tags and links; never use v-html here. -->
  <div class="safe-markdown">{{ content }}</div>
</template>

<style scoped>
.safe-markdown { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
