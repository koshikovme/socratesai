<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h2>Login</h2>
      <form @submit.prevent="handleLogin" class="auth-form">
        <input v-model="email" type="email" placeholder="Email" required />
        <input v-model="password" type="password" placeholder="Password" required />
        <button class="primary-btn" :disabled="loading">
          {{ loading ? 'Signing in...' : 'Login' }}
        </button>
      </form>

      <p v-if="error" class="error-text">{{ error }}</p>

      <p class="auth-switch">
        Don't have an account?
        <RouterLink to="/register">Register</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login } from '../services/authService'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    const data = await login({ email: email.value, password: password.value })
    auth.setAuth(data)
    await router.push('/dashboard')
  } catch (e) {
    error.value = e?.response?.data?.message || 'Login failed'
  } finally {
    loading.value = false
  }
}
</script>
