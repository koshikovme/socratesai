<template><main class="page-content"><section class="card"><LanguageSwitcher /><h1>{{ t('Could not verify your session') }}</h1><p>{{ t('The service is temporarily unreachable. Check your connection and try again.') }}</p><RouterLink class="primary-btn" to="/dashboard">{{ t('Try again') }}</RouterLink> <button class="ghost-btn" @click="auth.logout()">{{ t('Sign out') }}</button></section></main></template>
<script setup>
import { useI18n } from '../i18n'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'
import { useAuthStore } from '../stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
</script>
