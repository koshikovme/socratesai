<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content">
      <TaskList title="Backend Tasks" :tasks="tasks" />

      <section class="leetcode-section">
        <div class="section-head">
          <div>
            <h2>LeetCode Tasks</h2>
            <p class="muted">Total: {{ leetCodeTotal }}</p>
          </div>
          <button class="ghost-btn" @click="loadLeetCode">Refresh</button>
        </div>

        <div class="task-grid">
          <div
              v-for="item in leetCodeTasks"
              :key="item.id"
              class="card task-card"
          >
            <div class="task-top">
              <h3>{{ item.externalId }}. {{ item.title }}</h3>
              <span class="badge" :class="item.difficulty?.toLowerCase()">
                {{ item.difficulty }}
              </span>
            </div>

            <p class="task-topic">{{ item.topic }} / {{ item.language }}</p>
            <p class="task-desc">{{ item.description }}</p>

            <router-link class="primary-btn" :to="`/tasks/${item.id}`">
              Open task
            </router-link>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import TaskList from '../components/TaskList.vue'
import { fetchPublicTasks } from '../services/taskService'
import { fetchLeetCodeTasks } from '../services/leetcodeService'

const tasks = ref([])
const leetCodeTasks = ref([])
const leetCodeTotal = ref(0)

async function loadTasks() {
  tasks.value = await fetchPublicTasks()
}

async function loadLeetCode() {
  try {
    const response = await fetchLeetCodeTasks({
      categorySlug: 'all-code-essentials',
      limit: 20,
      skip: 0,
      difficulty: 'EASY'
    })

    leetCodeTasks.value = response.tasks || []
    leetCodeTotal.value = response.totalNum || 0
  } catch (e) {
    console.error('Failed to load LeetCode tasks', e)
    leetCodeTasks.value = []
    leetCodeTotal.value = 0
  }
}

onMounted(async () => {
  await loadTasks()
  await loadLeetCode()
})
</script>
