<template>
  <div class="auth-page">
    <div class="card auth-card">
      <div class="auth-language"><LanguageSwitcher /></div>
      <h2>{{ t('Login') }}</h2>
      <form @submit.prevent="handleLogin" class="auth-form">
        <input v-model="email" type="email" :aria-label="t('Email')" autocomplete="username" :placeholder="t('Email')" required />
        <input v-model="password" type="password" :aria-label="t('Password')" autocomplete="current-password" :placeholder="t('Password')" required />
        <button class="primary-btn" :disabled="loading">
          {{ t(loading ? 'Signing in...' : 'Login') }}
        </button>
      </form>

      <p v-if="error" class="error-text">{{ t(error) }}</p>

      <p class="auth-switch"> {{ t('Don\'t have an account?') }} <RouterLink to="/register">{{ t('Register') }}</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from '../i18n'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'
import { ref, onBeforeUnmount } from 'vue'
import { apiErrorMessage } from '../services/errors'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login } from '../services/authService'

const { t } = useI18n()

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
onBeforeUnmount(() => { password.value = '' })

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    const data = await login({ email: email.value, password: password.value })
    auth.setAuth(data)
    await router.push('/dashboard')
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>
