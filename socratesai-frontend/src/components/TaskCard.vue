<template>
  <div class="card task-card">
    <div class="task-top">
      <h3>{{ task.title }}</h3>
      <span class="badge" :class="task.difficulty?.toLowerCase()">{{ task.difficulty }}</span>
    </div>
    <p class="task-topic">{{ task.topic }} / {{ task.language }}</p>
    <p class="task-desc">{{ task.description }}</p>
    <router-link class="primary-btn" :to="`/tasks/${task.id}`">Open task</router-link>
  </div>
</template>

<script setup>
defineProps({ task: Object })
</script>
