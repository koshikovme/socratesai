<script setup>
import { useI18n } from '../i18n'
const { locale, languages, setLocale, t } = useI18n()
</script>

<template>
  <select class="language-switcher" :aria-label="t('Interface language')" :title="t('Interface language')" :value="locale" @change="setLocale($event.target.value)">
    <option v-for="language in languages" :key="language.code" :value="language.code" :lang="language.code">{{ language.label }}</option>
  </select>
</template>

<style scoped>
.language-switcher { width: auto; min-width: 112px; max-width: 150px; min-height: 42px; font: inherit; cursor: pointer; }
.app-header .language-switcher { background: #16202a; color: #e8eef6; border-color: rgba(255, 255, 255, .2); font-size: 13px; }
</style>
