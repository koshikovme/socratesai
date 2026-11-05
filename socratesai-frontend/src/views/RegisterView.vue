<template>
  <div class="auth-page">
    <div class="card auth-card">
      <div class="auth-language"><LanguageSwitcher /></div>
      <h2>{{ t('Register') }}</h2>

      <form @submit.prevent="handleRegister" class="auth-form">
        <input
            v-model="fullName"
            type="text" :aria-label="t('Full name')" maxlength="120" autocomplete="name"
            :placeholder="t('Full name')"
            required
        />

        <input
            v-model="email"
            type="email" :aria-label="t('Email')" maxlength="120" autocomplete="username"
            :placeholder="t('Email')"
            required
        />

        <input
            v-model="password"
            type="password" :aria-label="t('Password')" autocomplete="new-password"
            :placeholder="t('Password')"
            minlength="6" maxlength="100"
            required
        />

        <button class="primary-btn" :disabled="loading">
          {{ t(loading ? 'Creating account...' : 'Register') }}
        </button>
      </form>

      <p v-if="error" class="error-text">{{ t(error) }}</p>

      <p class="auth-switch"> {{ t('Already have an account?') }} <RouterLink to="/login">{{ t('Login') }}</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from '../i18n'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'
import { ref, onBeforeUnmount } from 'vue'
import { apiErrorMessage } from '../services/errors'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { register } from '../services/authService'

const { t } = useI18n()

const router = useRouter()
const auth = useAuthStore()

const fullName = ref('')
const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
onBeforeUnmount(() => { password.value = '' })

async function handleRegister() {
  loading.value = true
  error.value = ''

  try {
    const data = await register({
      fullName: fullName.value,
      email: email.value,
      password: password.value,
      role: 'STUDENT'
    })

    auth.setAuth(data)
    await router.push('/dashboard')
  } catch (e) {
    error.value =
        apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>
