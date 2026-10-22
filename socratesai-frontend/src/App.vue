<template>
  <router-view :key="auth.token ? 'signed-in' : 'anonymous'" />
</template>

<script setup>
import { onMounted, watch } from 'vue'
import { applyTheme, getStoredTheme } from './services/themeService'

import { useAuthStore } from './stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
watch(() => auth.token, token => { if (!token) router.replace('/login') })
onMounted(() => {
  applyTheme(getStoredTheme())
})
</script>
