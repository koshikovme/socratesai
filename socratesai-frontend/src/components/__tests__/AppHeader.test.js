import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppHeader from '../AppHeader.vue'
import { useAuthStore } from '../../stores/auth'

vi.mock('../../services/userService', () => ({ updateMySettings: vi.fn().mockResolvedValue({}) }))
const mounted = []
beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()) })
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()))

async function header(role = 'STUDENT') {
  const pinia = createPinia(); setActivePinia(pinia)
  useAuthStore().$patch({ token: 'fixture-token', role, fullName: 'Private Student Name', email: 'private@example.test' })
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/dashboard', component: { template: '<p>Catalog</p>' } },
    { path: '/profile', component: { template: '<p>Profile</p>' } },
  ] })
  await router.push('/dashboard'); await router.isReady()
  const wrapper = mount(AppHeader, { global: { plugins: [pinia, router] } }); mounted.push(wrapper)
  return wrapper
}

describe('application navigation', () => {
  test('keeps identity and role in Profile while exposing an accessible compact theme control', async () => {
    const wrapper = await header()
    expect(wrapper.text()).not.toContain('Private Student Name')
    expect(wrapper.text()).not.toContain('private@example.test')
    expect(wrapper.text()).not.toContain('STUDENT')
    expect(wrapper.find('nav[aria-label="Main navigation"]').text()).toContain('Profile')
    expect(wrapper.find('.icon-button').attributes('aria-label')).toMatch(/Light mode|Dark mode/)
    expect(wrapper.find('.icon-button svg').attributes('aria-hidden')).toBe('true')
  })

  test('preserves teaching and role-management navigation for authorized staff', async () => {
    const wrapper = await header('ADMIN')
    expect(wrapper.find('a[href="/teacher"]').text()).toBe('Teaching')
    expect(wrapper.find('a[href="/admin/users"]').text()).toBe('Roles')
    expect(wrapper.text()).not.toContain('ADMIN')
  })
})
