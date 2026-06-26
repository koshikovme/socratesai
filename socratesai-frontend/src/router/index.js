import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import DashboardView from '../views/DashboardView.vue'
import TaskSolveView from '../views/TaskSolveView.vue'
import ProfileView from '../views/ProfileView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        { path: '/', redirect: '/dashboard' },
        { path: '/login', component: LoginView },
        { path: '/register', component: RegisterView },
        { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true } },
        { path: '/profile', component: ProfileView, meta: { requiresAuth: true } },
        { path: '/tasks/:id', component: TaskSolveView, meta: { requiresAuth: true } }
    ]
})

router.beforeEach((to) => {
    const auth = useAuthStore()

    if (to.meta.requiresAuth && !auth.token) {
        return '/login'
    }

    if ((to.path === '/login' || to.path === '/register') && auth.token) {
        return '/dashboard'
    }
})

export default router
