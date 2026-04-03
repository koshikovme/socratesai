<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h2>Register</h2>

      <form @submit.prevent="handleRegister" class="auth-form">
        <input
            v-model="fullName"
            type="text"
            placeholder="Full name"
            required
        />

        <input
            v-model="email"
            type="email"
            placeholder="Email"
            required
        />

        <input
            v-model="password"
            type="password"
            placeholder="Password"
            minlength="6"
            required
        />

        <select v-model="role" required>
          <option value="STUDENT">Student</option>
          <option value="MENTOR">Mentor</option>
        </select>

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
import { ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { register } from '../services/authService'

const router = useRouter()
const auth = useAuthStore()

const fullName = ref('')
const email = ref('')
const password = ref('')
const role = ref('STUDENT')
const loading = ref(false)
const error = ref('')

async function handleRegister() {
  loading.value = true
  error.value = ''

  try {
    const data = await register({
      fullName: fullName.value,
      email: email.value,
      password: password.value,
      role: role.value
    })

    auth.setAuth(data)
    await router.push('/dashboard')
  } catch (e) {
    error.value =
        e?.response?.data?.message ||
        e?.response?.data?.error ||
        'Registration failed'
  } finally {
    loading.value = false
  }
}
</script>