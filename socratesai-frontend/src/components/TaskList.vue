<template>
  <section>
    <div class="section-head">
      <h2>{{ title }}</h2>
    </div>
    <div class="task-grid">
      <TaskCard v-for="task in tasks" :key="task.id" :task="task" />
    </div>
  </section>
</template>

<script setup>
import TaskCard from './TaskCard.vue'

defineProps({
  title: String,
  tasks: Array
})
</script>