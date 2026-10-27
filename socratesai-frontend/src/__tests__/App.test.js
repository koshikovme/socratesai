import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent, onMounted } from 'vue'
import { expect, it, vi } from 'vitest'
import App from '../App.vue'
import { useAuthStore } from '../stores/auth'

it('unmounts a protected page on logout without mounting it again as anonymous', async () => {
  const loadPrivateData = vi.fn()
  const ProtectedPage = defineComponent({
    setup() { onMounted(loadPrivateData) },
    template: '<p>Private profile</p>',
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/profile', component: ProtectedPage, meta: { requiresAuth: true } },
      { path: '/login', component: { template: '<p>Login page</p>' } },
    ],
  })
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.token = 'test-session'
  await router.push('/profile')
  const wrapper = mount(App, { global: { plugins: [pinia, router] } })
  await flushPromises()
  expect(loadPrivateData).toHaveBeenCalledTimes(1)
  auth.logout()
  await flushPromises()
  expect(wrapper.text()).toContain('Login page')
  expect(loadPrivateData).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
