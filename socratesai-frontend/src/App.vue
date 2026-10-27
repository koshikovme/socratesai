<template>
  <router-view v-if="auth.token || !route.meta.requiresAuth" :key="auth.token ? 'signed-in' : 'anonymous'" />
</template>

<script setup>
import { onMounted, watch } from 'vue'
import { applyTheme, getStoredTheme } from './services/themeService'

import { useAuthStore } from './stores/auth'
import { useRoute, useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
watch(() => auth.token, token => { if (!token) router.replace('/login') })
onMounted(() => {
  applyTheme(getStoredTheme())
})
</script>
