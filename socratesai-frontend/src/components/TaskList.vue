<script setup>
import TaskCard from './TaskCard.vue'

defineProps({
  tasks: { type: Array, default: () => [] },
  solvedIds: { type: Set, default: () => new Set() }
})
</script>

<template>
  <div class="task-grid">
    <TaskCard v-for="task in tasks" :key="task.id" :task="task" :solved="solvedIds.has(String(task.id))" />
  </div>
</template>
