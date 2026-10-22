<template><main class="page-content"><section class="card"><h1>Could not verify your session</h1><p>The service is temporarily unreachable. Check your connection and try again.</p><RouterLink class="primary-btn" to="/dashboard">Try again</RouterLink> <button class="ghost-btn" @click="auth.logout()">Sign out</button></section></main></template>
<script setup>
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
</script>
