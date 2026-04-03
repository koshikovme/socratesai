# Vue 3 + Vite

This template should help get you started developing with Vue 3 in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about IDE Support for Vue in the [Vue Docs Scaling up Guide](https://vuejs.org/guide/scaling-up/tooling.html#ide-support).


# SocratesAI Vue Frontend Starter

Ниже — минимальный, но уже нормальный фронт на Vue 3 + Vite + Pinia + Vue Router + STOMP, который работает с твоим backend API и WebSocket.

Важно: прямой вызов LeetCode GraphQL из браузера часто упирается в CORS и нестабильность публичного endpoint. Поэтому правильнее тянуть LeetCode задачи **через backend proxy endpoint**, а не напрямую из Vue. Во фронте я сразу закладываю именно такой вариант.

---

## 1. Стек

* Vue 3
* Vite
* Vue Router
* Pinia
* Axios
* @stomp/stompjs
* Простой кастомный UI без тяжёлой библиотеки компонентов

Установка:

```bash
npm create vite@latest socratesai-frontend -- --template vue
cd socratesai-frontend
npm install
npm install axios pinia vue-router @stomp/stompjs
```

---

## 2. Структура проекта

```text
src/
  main.js
  App.vue
  router/
    index.js
  stores/
    auth.js
  services/
    api.js
    authService.js
    taskService.js
    mentorService.js
    websocketService.js
    leetcodeService.js
  views/
    LoginView.vue
    DashboardView.vue
    TaskSolveView.vue
  components/
    AppHeader.vue
    TaskCard.vue
    CodeEditorPane.vue
    FeedbackPanel.vue
    TaskList.vue
  assets/
    base.css
```

---

## 3. main.js

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/base.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

---

## 4. router/index.js

```js
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import TaskSolveView from '../views/TaskSolveView.vue.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: LoginView },
    { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true } },
    { path: '/tasks/:id', component: TaskSolveView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) {
    return '/login'
  }
  if (to.path === '/login' && auth.token) {
    return '/dashboard'
  }
})

export default router
```

---

## 5. stores/auth.js

```js
import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: localStorage.getItem('userId') || '',
    email: localStorage.getItem('email') || '',
    fullName: localStorage.getItem('fullName') || '',
    role: localStorage.getItem('role') || ''
  }),
  actions: {
    setAuth(data) {
      this.token = data.token
      this.userId = String(data.userId)
      this.email = data.email
      this.fullName = data.fullName
      this.role = data.role

      localStorage.setItem('token', this.token)
      localStorage.setItem('userId', this.userId)
      localStorage.setItem('email', this.email)
      localStorage.setItem('fullName', this.fullName)
      localStorage.setItem('role', this.role)
    },
    logout() {
      this.token = ''
      this.userId = ''
      this.email = ''
      this.fullName = ''
      this.role = ''
      localStorage.clear()
    }
  }
})
```

---

## 6. services/api.js

```js
import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const api = axios.create({
  baseURL: 'http://localhost:8080'
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

export default api
```

---

## 7. services/authService.js

```js
import api from './api'

export async function login(payload) {
  const { data } = await api.post('/api/auth/login', payload)
  return data
}

export async function register(payload) {
  const { data } = await api.post('/api/auth/register', payload)
  return data
}
```

---

## 8. services/taskService.js

```js
import api from './api'

export async function fetchPublicTasks() {
  const { data } = await api.get('/api/tasks/public')
  return data
}

export async function fetchTaskById(id) {
  const { data } = await api.get(`/api/tasks/public/${id}`)
  return data
}

export async function createTask(payload) {
  const { data } = await api.post('/api/tasks', payload)
  return data
}
```

---

## 9. services/mentorService.js

```js
import api from './api'

export async function analyzeCode(payload) {
  const { data } = await api.post('/api/analyzer/analyze', payload)
  return data
}

export async function requestMentorFeedback(payload) {
  const { data } = await api.post('/api/mentor/feedback', payload)
  return data
}

export async function updateInteractionResult(interactionId, payload) {
  const { data } = await api.post(`/api/interactions/${interactionId}/result`, payload)
  return data
}
```

---

## 10. services/websocketService.js

```js
import { Client } from '@stomp/stompjs'

let client = null

export function createMentorSocket({ onFeedback, onConnect, onError }) {
  client = new Client({
    brokerURL: 'ws://localhost:8080/ws',
    reconnectDelay: 3000,
    debug: () => {}
  })

  client.onConnect = () => {
    client.subscribe('/user/queue/feedback', (message) => {
      const body = JSON.parse(message.body)
      onFeedback?.(body)
    })
    onConnect?.()
  }

  client.onStompError = (frame) => {
    onError?.(frame)
  }

  client.activate()
  return client
}

export function sendCodeUpdate(payload) {
  if (!client || !client.connected) return
  client.publish({
    destination: '/app/code.update',
    body: JSON.stringify(payload)
  })
}

export function disconnectSocket() {
  if (client) {
    client.deactivate()
    client = null
  }
}
```

---

## 11. services/leetcodeService.js

Важно: здесь используется **backend proxy**, которого у тебя пока нет в swagger. Это нужно добавить на backend. Во фронте будет вот такой вызов:

```js
import api from './api'

export async function fetchLeetCodeTasks(params = {}) {
  const { data } = await api.get('/api/leetcode/problems', {
    params: {
      limit: params.limit || 20,
      skip: params.skip || 0,
      difficulty: params.difficulty || ''
    }
  })
  return data
}
```

Потому что прямой fetch из браузера в LeetCode GraphQL почти наверняка упрётся в CORS. Правильное решение — backend proxy.

---

## 12. App.vue

```vue
<template>
  <router-view />
</template>
```

---

## 13. components/AppHeader.vue

```vue
<template>
  <header class="app-header">
    <div>
      <h1 class="brand">SocratesAI</h1>
      <p class="subtitle">Real-time mentor for CS1</p>
    </div>
    <div class="header-actions">
      <span class="user-chip">{{ auth.fullName || auth.email }}</span>
      <button class="ghost-btn" @click="logout">Logout</button>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
```

---

## 14. components/TaskCard.vue

```vue
<template>
  <div class="card task-card">
    <div class="task-top">
      <h3>{{ task.title }}</h3>
      <span class="badge" :class="task.difficulty?.toLowerCase()">{{ task.difficulty }}</span>
    </div>
    <p class="task-topic">{{ task.topic }} · {{ task.language }}</p>
    <p class="task-desc">{{ task.description }}</p>
    <router-link class="primary-btn" :to="`/tasks/${task.id}`">Open task</router-link>
  </div>
</template>

<script setup>
defineProps({ task: Object })
</script>
```

---

## 15. components/TaskList.vue

```vue
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
```

---

## 16. components/CodeEditorPane.vue

```vue
<template>
  <div class="card editor-card">
    <div class="editor-toolbar">
      <span>Language: {{ language }}</span>
      <span>Attempt: {{ attemptNo }}</span>
    </div>
    <textarea
      class="code-editor"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      placeholder="Write your code here..."
    />
  </div>
</template>

<script setup>
defineProps({
  modelValue: String,
  language: String,
  attemptNo: Number
})

defineEmits(['update:modelValue'])
</script>
```

---

## 17. components/FeedbackPanel.vue

```vue
<template>
  <div class="card feedback-card">
    <h3>Mentor Feedback</h3>
    <div v-if="feedback">
      <p><strong>Action:</strong> {{ feedback.action }}</p>
      <p><strong>Error:</strong> {{ feedback.errorType }}</p>
      <p><strong>Region:</strong> {{ feedback.suspiciousRegion }}</p>
      <p class="feedback-text">{{ feedback.feedbackText }}</p>
      <p class="muted">Session: {{ feedback.sessionId }}</p>
    </div>
    <p v-else class="muted">No feedback yet.</p>
  </div>
</template>

<script setup>
defineProps({ feedback: Object })
</script>
```

---

## 18. views/LoginView.vue

```vue
<template>
  <div class="auth-page">
    <div class="card auth-card">
      <h2>Login</h2>
      <form @submit.prevent="handleLogin" class="auth-form">
        <input v-model="email" type="email" placeholder="Email" required />
        <input v-model="password" type="password" placeholder="Password" required />
        <button class="primary-btn" :disabled="loading">
          {{ loading ? 'Signing in...' : 'Login' }}
        </button>
      </form>
      <p v-if="error" class="error-text">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login } from '../services/authService'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    const data = await login({ email: email.value, password: password.value })
    auth.setAuth(data)
    router.push('/dashboard')
  } catch (e) {
    error.value = e?.response?.data?.message || 'Login failed'
  } finally {
    loading.value = false
  }
}
</script>
```

---

## 19. views/DashboardView.vue

```vue
<template>
  <div class="page-shell">
    <AppHeader />
    <main class="page-content">
      <TaskList title="Backend Tasks" :tasks="tasks" />

      <section class="leetcode-section">
        <div class="section-head">
          <h2>LeetCode Tasks</h2>
          <button class="ghost-btn" @click="loadLeetCode">Refresh</button>
        </div>

        <div class="task-grid">
          <div v-for="item in leetCodeTasks" :key="item.questionFrontendId" class="card task-card">
            <div class="task-top">
              <h3>{{ item.questionFrontendId }}. {{ item.title }}</h3>
              <span class="badge" :class="item.difficulty?.toLowerCase()">{{ item.difficulty }}</span>
            </div>
            <p class="task-desc">Paid: {{ item.isPaidOnly ? 'Yes' : 'No' }}</p>
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

async function loadTasks() {
  tasks.value = await fetchPublicTasks()
}

async function loadLeetCode() {
  try {
    leetCodeTasks.value = await fetchLeetCodeTasks({ limit: 10, skip: 0 })
  } catch {
    leetCodeTasks.value = []
  }
}

onMounted(async () => {
  await loadTasks()
  await loadLeetCode()
})
</script>
```

---

## 20. views/TaskSolveView.vue

```vue
<template>
  <div class="page-shell">
    <AppHeader />
    <main class="solve-layout">
      <section class="left-panel">
        <div class="card task-view-card" v-if="task">
          <h2>{{ task.title }}</h2>
          <p class="muted">{{ task.topic }} · {{ task.language }} · {{ task.difficulty }}</p>
          <p>{{ task.description }}</p>
        </div>

        <CodeEditorPane
          v-model="code"
          :language="task?.language || 'java'"
          :attempt-no="attemptNo"
        />

        <div class="action-row">
          <button class="primary-btn" @click="analyzeOnce">Analyze via REST</button>
          <button class="ghost-btn" @click="sendRealtime">Send via WebSocket</button>
        </div>
      </section>

      <section class="right-panel">
        <FeedbackPanel :feedback="feedback" />
      </section>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppHeader from '../components/AppHeader.vue'
import CodeEditorPane from '../components/CodeEditorPane.vue'
import FeedbackPanel from '../components/FeedbackPanel.vue'
import { fetchTaskById } from '../services/taskService'
import { analyzeCode, requestMentorFeedback } from '../services/mentorService'
import { createMentorSocket, disconnectSocket, sendCodeUpdate } from '../services/websocketService'

const route = useRoute()
const auth = useAuthStore()

const task = ref(null)
const code = ref('')
const attemptNo = ref(1)
const feedback = ref(null)
let debounceTimer = null

async function loadTask() {
  task.value = await fetchTaskById(route.params.id)
  code.value = task.value?.starterCode || ''
}

async function analyzeOnce() {
  const analyzerResult = await analyzeCode({
    studentId: Number(auth.userId),
    taskId: Number(route.params.id),
    language: task.value?.language || 'java',
    code: code.value,
    attemptNo: attemptNo.value
  })

  feedback.value = await requestMentorFeedback({
    studentId: Number(auth.userId),
    taskId: Number(route.params.id),
    attemptNo: attemptNo.value,
    code: code.value,
    analyzerResult
  })
}

function sendRealtime() {
  sendCodeUpdate({
    studentId: Number(auth.userId),
    taskId: Number(route.params.id),
    language: task.value?.language || 'java',
    code: code.value,
    attemptNo: attemptNo.value
  })
}

onMounted(async () => {
  await loadTask()

  createMentorSocket({
    onFeedback: (message) => {
      feedback.value = message
    }
  })
})

watch(code, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    sendRealtime()
  }, 1500)
})

onBeforeUnmount(() => {
  disconnectSocket()
  clearTimeout(debounceTimer)
})
</script>
```

---

## 21. styles `src/assets/base.css`

```css
:root {
  --bg: #0f172a;
  --panel: #111827;
  --muted: #94a3b8;
  --card: #ffffff;
  --line: #e5e7eb;
  --primary: #2563eb;
  --primary-dark: #1d4ed8;
  --success: #16a34a;
  --warning: #d97706;
  --danger: #dc2626;
  --text: #0f172a;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: Inter, Arial, sans-serif;
  background: #f8fafc;
  color: var(--text);
}

.page-shell {
  min-height: 100vh;
}

.page-content,
.solve-layout {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px;
}

.solve-layout {
  display: grid;
  grid-template-columns: 1.4fr 0.8fr;
  gap: 20px;
}

.card {
  background: white;
  border-radius: 20px;
  padding: 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
  border: 1px solid #eef2f7;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  background: linear-gradient(135deg, #0f172a, #1e293b);
  color: white;
}

.brand {
  margin: 0;
  font-size: 26px;
}

.subtitle {
  margin: 4px 0 0;
  color: #cbd5e1;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  background: rgba(255,255,255,0.12);
  padding: 8px 12px;
  border-radius: 999px;
}

.primary-btn,
.ghost-btn {
  border: none;
  border-radius: 14px;
  padding: 10px 16px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.primary-btn {
  background: var(--primary);
  color: white;
}

.primary-btn:hover {
  background: var(--primary-dark);
}

.ghost-btn {
  background: #eef2ff;
  color: #1e3a8a;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.task-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.badge {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.badge.easy { background: #dcfce7; color: #166534; }
.badge.medium { background: #fef3c7; color: #92400e; }
.badge.hard { background: #fee2e2; color: #991b1b; }

.task-desc,
.task-topic,
.muted {
  color: var(--muted);
}

.code-editor {
  width: 100%;
  min-height: 420px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  padding: 16px;
  font-family: Consolas, monospace;
  font-size: 14px;
  resize: vertical;
  background: #0f172a;
  color: #e2e8f0;
}

.editor-toolbar,
.action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.feedback-text {
  font-size: 16px;
  line-height: 1.6;
}

.auth-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
}

.auth-card {
  width: 100%;
  max-width: 420px;
}

.auth-form {
  display: grid;
  gap: 12px;
}

input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #d1d5db;
}

.error-text {
  color: var(--danger);
}

@media (max-width: 960px) {
  .solve-layout {
    grid-template-columns: 1fr;
  }
}
```

---

## 22. Что обязательно добавить на backend для LeetCode

Сейчас у тебя этого endpoint нет. Нужно добавить proxy endpoint, например:

`GET /api/leetcode/problems?limit=10&skip=0&difficulty=EASY`

И уже backend будет дёргать LeetCode GraphQL query.

Причина: браузерный прямой вызов к LeetCode чаще всего неудобен из-за CORS.

---

## 23. Что делать дальше

Сначала подними этот фронт и проверь обычный flow:

* login
* dashboard
* public tasks
* task details
* editor
* REST analyze + mentor
* websocket real-time feedback

Потом уже докрути backend proxy для LeetCode и подцепи реальные задачи.

После этого можно заменить textarea на Monaco Editor и сделать более продвинутый UI.
