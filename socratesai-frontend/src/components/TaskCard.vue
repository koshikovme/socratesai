<script setup>
defineProps({ task: { type: Object, required: true }, solved: Boolean })
</script>

<template>
  <article class="card task-card">
    <div class="task-top">
      <h3>{{ task.title }}</h3>
      <span v-if="task.difficulty" class="badge" :class="task.difficulty.toLowerCase()">{{ task.difficulty }}</span>
    </div>
    <p class="task-topic">{{ task.topic || 'Programming' }}</p>
    <p v-if="solved" class="success-text" data-testid="solved">✓ SOLVED · accepted submission</p>
    <router-link class="primary-btn" :to="{ path: `/problems/${task.id}`, query: { version: String(task.problemVersionId) } }">
      Open problem
    </router-link>
  </article>
</template>

<style scoped>
.task-card { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
.task-card .task-top { width: 100%; }
.task-card p { margin: 4px 0; }
.task-card a { margin-top: auto; }
</style>
