import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ProfileView from '../ProfileView.vue'
import { useAuthStore } from '../../stores/auth'
import { fetchMyProfile, fetchMyProgress, updateMyProfile, updateMySettings } from '../../services/userService'
import { clearSessionResources } from '../../services/sessionLifecycle'
import { setLocale, t } from '../../i18n'

vi.mock('../../components/AppHeader.vue', () => ({ default: { template: '<header />' } }))
vi.mock('../../services/userService', () => ({ fetchMyProfile: vi.fn(), fetchMyProgress: vi.fn(), updateMyProfile: vi.fn(), updateMySettings: vi.fn() }))
const account = (role = 'STUDENT') => ({ id: 42, fullName: 'Learner Name', email: 'learner@example.test', role,
  university: 'Example university', groupName: 'CS1', darkMode: false, profileRevision: 'a'.repeat(64) })
let wrapper
const button = label => wrapper.findAll('button').find(item => item.text() === t(label))
async function render(role = 'STUDENT') {
  useAuthStore().setAuth({ userId: 42, token: 'existing-session', email: account().email, fullName: account().fullName, role })
  fetchMyProfile.mockResolvedValue(account(role))
  fetchMyProgress.mockResolvedValue({ profile: account(role), summary: { solvedTasks: 1, attemptedTasks: 2, totalAttempts: 3, meanLatencyMs: 99 },
    tasks: [{ taskId: 7, title: 'Add numbers', difficulty: 'EASY', topic: 'Input', attempts: 2, status: 'SOLVED', openable: true }],
    recentActivity: [{ taskTitle: 'Legacy activity #21', errorType: 'Execution Unknown' }] })
  wrapper = mount(ProfileView, { global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } } })
  await flushPromises()
}
beforeEach(() => { vi.resetAllMocks(); localStorage.clear(); setActivePinia(createPinia()); setLocale('en') })
afterEach(() => { wrapper?.unmount(); wrapper = null; clearSessionResources(); setLocale('en') })

describe('account profile and role-specific progress', () => {
  test.each(['en', 'ru', 'kk'])('edits account fields with a revision while localized (%s)', async locale => {
    setLocale(locale); await render(); await button('Edit profile').trigger('click')
    await wrapper.get('#profile-name').setValue(' Алимжан Қошықов ')
    await wrapper.get('#profile-university').setValue('  My university  ')
    await wrapper.get('#profile-group').setValue('  CS2  ')
    updateMyProfile.mockResolvedValue({ ...account(), fullName: 'Алимжан Қошықов', university: 'My university', groupName: 'CS2', profileRevision: 'b'.repeat(64) })
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(updateMyProfile).toHaveBeenCalledWith({ fullName: 'Алимжан Қошықов', email: account().email, university: 'My university', groupName: 'CS2', profileRevision: 'a'.repeat(64) }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(useAuthStore().fullName).toBe('Алимжан Қошықов')
    expect(wrapper.text()).toContain(t('Profile updated.'))
    expect(wrapper.find('form').exists()).toBe(false)
  })

  test('email changes require password and apply rotated credentials without logging out', async () => {
    await render(); await button('Edit profile').trigger('click')
    expect(wrapper.find('#profile-password').exists()).toBe(false)
    await wrapper.get('#profile-email').setValue('New@example.test')
    expect(wrapper.get('#profile-password').attributes('required')).toBeDefined()
    await wrapper.get('#profile-password').setValue('private-current-password')
    updateMyProfile.mockResolvedValue({ ...account(), email: 'new@example.test', authentication: { userId: 42, token: 'rotated-session' } })
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(updateMyProfile.mock.calls[0][0]).toMatchObject({ email: 'new@example.test', currentPassword: 'private-current-password' })
    expect(useAuthStore().token).toBe('rotated-session')
    expect(wrapper.text()).toContain('new@example.test')
    await button('Edit profile').trigger('click')
    expect(wrapper.find('#profile-password').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('private-current-password')
  })

  test.each(['TEACHER', 'ADMIN'])('shows a teaching guide instead of personal research counters for %s', async role => {
    await render(role)
    expect(fetchMyProgress).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Your teaching workspace')
    expect(wrapper.find('a[href="/teacher"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/admin/users"]').exists()).toBe(role === 'ADMIN')
    expect(wrapper.text()).not.toMatch(/Legacy activity|Execution Unknown|Feedback actions|Mean latency|Progress by task/)
  })

  test('students see formal attempts and explained solved progress without research/debug events', async () => {
    await render()
    expect(wrapper.text()).toContain('Add numbers')
    expect(wrapper.text()).toContain('2 formal submissions')
    expect(wrapper.text()).toContain('A task is solved only after Submit receives ACCEPTED.')
    expect(wrapper.find('a[href="/problems/7"]').exists()).toBe(true)
    expect(wrapper.text()).not.toMatch(/Legacy activity|Execution Unknown|Feedback actions|Mean latency|Your teaching workspace/)
  })

  test('profile names and study information render as text rather than markup', async () => {
    fetchMyProgress.mockResolvedValueOnce({ profile: { ...account(), fullName: '<img src=x onerror=alert(1)>', university: '<script>private</script>' }, summary: {}, tasks: [] })
    await render()
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })

  test('safe conflicts preserve edits and password is discarded', async () => {
    await render(); await button('Edit profile').trigger('click')
    await wrapper.get('#profile-name').setValue('Unsaved change')
    await wrapper.get('#profile-email').setValue('new@example.test')
    await wrapper.get('#profile-password').setValue('private-current-password')
    updateMyProfile.mockRejectedValue({ response: { status: 409, data: { code: 'CONFLICT', message: 'SQL PRIVATE DETAILS' } } })
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.get('#profile-name').element.value).toBe('Unsaved change')
    expect(wrapper.get('#profile-password').element.value).toBe('')
    expect(wrapper.text()).toContain('Reload the latest version')
    expect(wrapper.text()).not.toContain('SQL PRIVATE DETAILS')
    expect(button('Reload profile').exists()).toBe(true)
    await button('Cancel').trigger('click')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(useAuthStore().fullName).toBe('Learner Name')
  })

  test('logout cancels the request and ignores a late credential-bearing response', async () => {
    await render(); await button('Edit profile').trigger('click')
    let resolve
    updateMyProfile.mockImplementation(() => new Promise(done => { resolve = done }))
    await wrapper.get('form').trigger('submit')
    const signal = updateMyProfile.mock.calls[0][1].signal
    useAuthStore().logout()
    resolve({ ...account(), fullName: 'Late private response', authentication: { userId: 42, token: 'late' } }); await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(useAuthStore().token).toBe('')
    expect(wrapper.text()).not.toContain('Late private response')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  test('theme preference is saved separately without discarding an unsaved form', async () => {
    await render(); await button('Edit profile').trigger('click')
    await wrapper.get('#profile-name').setValue('Unsaved name')
    updateMySettings.mockResolvedValue({ ...account(), darkMode: true })
    await button('Use dark mode').trigger('click'); await flushPromises()
    expect(updateMySettings).toHaveBeenCalledWith({ darkMode: true }, expect.any(Object))
    expect(wrapper.get('#profile-name').element.value).toBe('Unsaved name')
    expect(button('Use light mode').exists()).toBe(true)
  })
})
