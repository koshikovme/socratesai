<template>
  <header class="app-header">
    <div class="brand-block">
      <RouterLink to="/dashboard" class="brand-link">SocratesAI</RouterLink>
      <p class="subtitle">Policy-aware coding mentor for CS1 practice</p>
    </div>
    <div class="header-actions">
      <RouterLink to="/dashboard" class="header-link">Workspace</RouterLink>
      <RouterLink to="/profile" class="header-link">Profile</RouterLink>
      <button class="header-link header-button" @click="toggleThemeMode">
        {{ theme === 'dark' ? 'Light mode' : 'Dark mode' }}
      </button>
      <span class="role-chip">{{ auth.role || 'STUDENT' }}</span>
      <span class="user-chip">{{ auth.fullName || auth.email }}</span>
      <button class="ghost-btn" @click="logout">Logout</button>
    </div>
  </header>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { applyTheme, getStoredTheme, toggleTheme } from '../services/themeService'
import { updateMySettings } from '../services/userService'

const router = useRouter()
const auth = useAuthStore()
const theme = ref(applyTheme(getStoredTheme()))

function syncTheme(event) {
  theme.value = event.detail
}

async function toggleThemeMode() {
  theme.value = toggleTheme()

  if (!auth.token) return
  try {
    await updateMySettings({ darkMode: theme.value === 'dark' })
  } catch (e) {
    console.error('Failed to persist theme setting', e)
  }
}

function logout() {
  auth.logout()
  router.push('/login')
}

onMounted(() => {
  window.addEventListener('socrates-theme-change', syncTheme)
})

onBeforeUnmount(() => {
  window.removeEventListener('socrates-theme-change', syncTheme)
})
</script>
