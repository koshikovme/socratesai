<template>
  <header class="app-header">
    <div class="brand-block">
      <RouterLink to="/dashboard" class="brand-link">SocratesAI</RouterLink>
      <p class="subtitle">Policy-aware coding mentor for CS1 practice</p>
    </div>
    <div class="header-actions">
      <RouterLink to="/dashboard" class="header-link">Workspace</RouterLink>
      <span class="role-chip">{{ auth.role || 'STUDENT' }}</span>
      <span class="user-chip">{{ auth.fullName || auth.email }}</span>
      <button class="ghost-btn" @click="logout">Logout</button>
    </div>
  </header>
</template>

<script setup>
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
