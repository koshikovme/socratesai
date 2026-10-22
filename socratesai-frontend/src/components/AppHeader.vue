<template>
  <header class="app-header">
    <div class="brand-block">
      <RouterLink to="/dashboard" class="brand-link">SocratesAI</RouterLink>
      <p class="subtitle">Policy-aware coding mentor for CS1 practice</p>
    </div>
    <div class="header-actions">
      <RouterLink to="/dashboard" class="header-link">Catalog</RouterLink>
      <RouterLink v-if="['TEACHER', 'ADMIN'].includes(auth.role)" to="/teacher" class="header-link">Teaching</RouterLink>
      <RouterLink v-if="auth.role === 'ADMIN'" to="/admin/users" class="header-link">Roles</RouterLink>
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
    /* Theme remains applied locally. Do not log response bodies. */
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
