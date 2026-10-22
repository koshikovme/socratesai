import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { fetchMyProfile } from '../services/userService'
import { createAccessGuard } from './access'

const authenticated = { requiresAuth: true }
const staff = { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] }
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/unavailable', component: () => import('../views/ServiceUnavailableView.vue') },
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/register', component: () => import('../views/RegisterView.vue') },
    { path: '/dashboard', component: () => import('../views/DashboardView.vue'), meta: authenticated },
    { path: '/profile', component: () => import('../views/ProfileView.vue'), meta: authenticated },
    { path: '/problems/:id', component: () => import('../views/TaskSolveView.vue'), meta: authenticated },
    { path: '/tasks/:id(\\d+)', redirect: to => ({ path: `/problems/${to.params.id}`, query: to.query }) },
    { path: '/collections/:id', component: () => import('../views/CollectionView.vue'), meta: authenticated },
    { path: '/teacher', component: () => import('../views/TeacherDashboardView.vue'), meta: staff },
    { path: '/teacher/problems/new', component: () => import('../views/TeacherProblemEditorView.vue'), meta: staff },
    { path: '/teacher/problems/:id', component: () => import('../views/TeacherProblemEditorView.vue'), meta: staff },
    { path: '/teacher/collections/new', component: () => import('../views/TeacherCollectionEditorView.vue'), meta: staff },
    { path: '/teacher/collections/:id', component: () => import('../views/TeacherCollectionEditorView.vue'), meta: staff },
    { path: '/admin/users', component: () => import('../views/AdminUsersView.vue'), meta: { requiresAuth: true, roles: ['ADMIN'] } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})
router.beforeEach(to => createAccessGuard(useAuthStore(), fetchMyProfile)(to))
export default router
