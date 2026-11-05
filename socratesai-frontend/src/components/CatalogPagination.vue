<script setup>
import { useI18n } from '../i18n'
import { computed } from 'vue'

const { t } = useI18n()

const props = defineProps({
  page: { type: Number, required: true },
  size: { type: Number, required: true },
  total: { type: Number, required: true },
  loading: Boolean
})
defineEmits(['page', 'size'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>

<template>
  <nav class="catalog-pagination" :aria-label="t('Catalog pages')">
    <button class="ghost-btn" :disabled="loading || page <= 0" @click="$emit('page', page - 1)">{{ t('Previous') }}</button>
    <span aria-live="polite">{{ t('Page {page} of {pages} · {total} total', { page: page + 1, pages, total }) }}</span>
    <button class="ghost-btn" :disabled="loading || page + 1 >= pages || page >= 10000" @click="$emit('page', page + 1)">{{ t('Next') }}</button>
    <label>{{ t('Per page') }} <select :value="size" :disabled="loading" @change="$emit('size', Number($event.target.value))">
        <option :value="10">10</option><option :value="20">20</option><option :value="50">50</option>
      </select>
    </label>
  </nav>
</template>

<style scoped>
.catalog-pagination { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-top: 24px; }
label { display: flex; gap: 8px; align-items: center; margin-left: auto; }
select { width: auto; }
</style>
