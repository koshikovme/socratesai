<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h2>Register</h2>

      <form @submit.prevent="handleRegister" class="auth-form">
        <input
            v-model="fullName"
            type="text" aria-label="Full name" maxlength="120" autocomplete="name"
            placeholder="Full name"
            required
        />

        <input
            v-model="email"
            type="email" aria-label="Email" maxlength="120" autocomplete="username"
            placeholder="Email"
            required
        />

        <input
            v-model="password"
            type="password" aria-label="Password" autocomplete="new-password"
            placeholder="Password"
            minlength="6" maxlength="100"
            required
        />

        <button class="primary-btn" :disabled="loading">
          {{ loading ? 'Creating account...' : 'Register' }}
        </button>
      </form>

      <p v-if="error" class="error-text">{{ error }}</p>

      <p class="auth-switch">
        Already have an account?
        <RouterLink to="/login">Login</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { apiErrorMessage } from '../services/errors'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { register } from '../services/authService'

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
