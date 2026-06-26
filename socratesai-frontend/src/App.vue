<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { applyTheme, getStoredTheme } from './services/themeService'

onMounted(() => {
  applyTheme(getStoredTheme())
})
</script>
