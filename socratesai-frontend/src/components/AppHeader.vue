<template>
  <header class="app-header">
    <div class="brand-block">
      <RouterLink to="/dashboard" class="brand-link">SocratesAI</RouterLink>
      <p class="subtitle">{{ t('Programming practice, with guidance') }}</p>
    </div>
    <div class="header-actions">
      <nav class="header-nav" :aria-label="t('Main navigation')">
        <RouterLink to="/dashboard" class="header-link">{{ t('Catalog') }}</RouterLink>
        <RouterLink v-if="['TEACHER', 'ADMIN'].includes(auth.role)" to="/teacher" class="header-link">{{ t('Teaching') }}</RouterLink>
        <RouterLink v-if="auth.role === 'ADMIN'" to="/admin/users" class="header-link">{{ t('Roles') }}</RouterLink>
        <RouterLink to="/profile" class="header-link">{{ t('Profile') }}</RouterLink>
      </nav>
      <LanguageSwitcher />
      <button class="ghost-btn icon-button" :aria-label="t(theme === 'dark' ? 'Light mode' : 'Dark mode')" :title="t(theme === 'dark' ? 'Light mode' : 'Dark mode')" @click="toggleThemeMode">
        <svg v-if="theme === 'dark'" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></svg>
        <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M20 14a8 8 0 0 1-10-10A8 8 0 1 0 20 14Z" /></svg>
      </button>
      <button class="ghost-btn" @click="logout">{{ t('Logout') }}</button>
    </div>
  </header>
</template>

<script setup>
import { useI18n } from '../i18n'
import LanguageSwitcher from './LanguageSwitcher.vue'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { applyTheme, getStoredTheme, toggleTheme } from '../services/themeService'
import { updateMySettings } from '../services/userService'

const { t } = useI18n()

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
