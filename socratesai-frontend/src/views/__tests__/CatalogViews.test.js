import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const calls = vi.hoisted(() => ({
  fetchProblems: vi.fn(), fetchCollections: vi.fn(), fetchJudgedProgress: vi.fn(),
  fetchCollection: vi.fn(), fetchCollectionProgress: vi.fn()
}))
vi.mock('../../services/catalogService', async importOriginal => ({ ...await importOriginal(), ...calls }))
vi.mock('../../stores/auth', async () => {
  const { reactive } = await import('vue')
  const auth = reactive({ token: 'token' })
  return { useAuthStore: () => auth }
})
vi.mock('vue-router', async importOriginal => ({ ...await importOriginal(), useRoute: () => ({ params: { id: '7' } }) }))
import DashboardView from '../DashboardView.vue'
import CollectionView from '../CollectionView.vue'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const problems = [
  { id: 1, problemVersionId: 11, title: 'Arrays', topic: 'Lists', difficulty: 'EASY' },
  { id: 2, problemVersionId: 22, title: 'Trees', topic: 'Recursion', difficulty: 'HARD' }
]
const global = { stubs: { AppHeader: true, RouterLink: RouterLinkStub } }
const button = (wrapper, title) => wrapper.findAll('button').find(item => item.text() === title)
function deferred() {
  let resolve
  const promise = new Promise(complete => { resolve = complete })
  return { promise, resolve }
}

beforeEach(() => {
  Object.values(calls).forEach(mock => mock.mockReset())
  auth.token = 'token'
  calls.fetchProblems.mockResolvedValue({ items: problems, page: 0, size: 20, totalElements: 21 })
  calls.fetchCollections.mockResolvedValue({ items: [{ id: 7, title: 'First steps' }], page: 0, size: 20, totalElements: 1 })
  calls.fetchJudgedProgress.mockResolvedValue({ progressSource: 'JUDGED_SUBMISSION', tasks: [
    { taskId: 1, status: 'SOLVED' }, { taskId: 2, status: 'IN_PROGRESS', resolvedAfterFeedback: true }
  ] })
  calls.fetchCollection.mockResolvedValue({ id: 7, title: 'First steps', description: '<img src=x onerror=alert(1)>', problems: [
    { problemId: 1, problemVersionId: 11, title: 'Arrays', position: 1 },
    { problemId: 2, problemVersionId: 22, title: 'Trees', position: 0 }
  ] })
  calls.fetchCollectionProgress.mockResolvedValue({ collectionId: 7, total: 2, solved: 1, problems: [
    { problemId: 1, problemVersionId: 11, solved: true },
    { problemId: 2, problemVersionId: 21, solved: true }
  ] })
})

describe('student catalog', () => {
  it('renders accepted progress, pins native version links, and filters only the current page', async () => {
    const wrapper = mount(DashboardView, { global })
    await flushPromises()
    expect(wrapper.findAll('[data-testid="solved"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('Filters apply to the current page')
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toEqual({ path: '/problems/1', query: { version: '11' } })
    await wrapper.find('input[type="search"]').setValue('recursion')
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.find('article').text()).toContain('Trees')
    expect(calls.fetchProblems).toHaveBeenCalledTimes(1)
    await wrapper.find('input[type="search"]').setValue('')
    await wrapper.findAll('select')[1].setValue('SOLVED')
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.find('article').text()).toContain('Arrays')
    wrapper.unmount()
  })

  it('uses bounded server pages and changes catalog without external tasks', async () => {
    const wrapper = mount(DashboardView, { global })
    await flushPromises()
    await button(wrapper, 'Next').trigger('click')
    await flushPromises()
    expect(calls.fetchProblems).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    await wrapper.find('.catalog-pagination select').setValue('50')
    await flushPromises()
    expect(calls.fetchProblems).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50 }))
    await button(wrapper, 'Collections').trigger('click')
    await flushPromises()
    expect(calls.fetchCollections).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50 }))
    expect(wrapper.text()).toContain('First steps')
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/collections/7')
    wrapper.unmount()
  })

  it('rejects stale page responses and clears in-flight data on logout', async () => {
    const old = deferred()
    calls.fetchProblems.mockReturnValueOnce(old.promise)
    const wrapper = mount(DashboardView, { global })
    const oldSignal = calls.fetchProblems.mock.calls[0][0].signal
    await button(wrapper, 'Collections').trigger('click')
    await flushPromises()
    old.resolve({ items: [{ ...problems[0], title: 'Stale secret title' }], totalElements: 1 })
    await flushPromises()
    expect(oldSignal.aborted).toBe(true)
    expect(wrapper.text()).not.toContain('Stale secret title')
    const pending = deferred()
    calls.fetchCollections.mockReturnValueOnce(pending.promise)
    await button(wrapper, 'Refresh').trigger('click')
    await flushPromises()
    auth.token = ''
    await flushPromises()
    pending.resolve({ items: [{ id: 7, title: 'Previous account collection' }], totalElements: 1 })
    await flushPromises()
    expect(wrapper.text()).not.toContain('Previous account collection')
    expect(wrapper.findAll('article')).toHaveLength(0)
    wrapper.unmount()
  })

  it('keeps server diagnostics out of rendering and disables progress filters when unavailable', async () => {
    calls.fetchJudgedProgress.mockRejectedValue({ response: { status: 500, data: { message: 'private SQL source dump' } } })
    const wrapper = mount(DashboardView, { global })
    await flushPromises()
    expect(wrapper.text()).toContain('Progress unavailable')
    expect(wrapper.text()).not.toContain('private SQL')
    expect(wrapper.findAll('select')[1].attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})

describe('native collection progress', () => {
  it('preserves pinned order/version links and renders only exact-version acceptance', async () => {
    const wrapper = mount(CollectionView, { global })
    await flushPromises()
    expect(wrapper.findAll('li')[0].text()).toContain('Trees')
    expect(wrapper.findAll('[data-testid="solved"]')).toHaveLength(1)
    expect(wrapper.find('[data-testid="collection-progress"]').text()).toBe('1 of 2 solved')
    expect(wrapper.findAllComponents(RouterLinkStub)[1].props('to')).toEqual({ path: '/problems/2', query: { version: '22', collection: '7' } })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    wrapper.unmount()
  })

  it('discards late collection details after logout', async () => {
    const pending = deferred()
    calls.fetchCollection.mockReturnValueOnce(pending.promise)
    const wrapper = mount(CollectionView, { global })
    const signal = calls.fetchCollection.mock.calls[0][1].signal
    auth.token = ''
    await flushPromises()
    pending.resolve({ id: 7, title: 'Previous collection', problems: [] })
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.text()).not.toContain('Previous collection')
    wrapper.unmount()
  })

  it('can still show published content when progress fails', async () => {
    calls.fetchCollectionProgress.mockRejectedValue({ response: { status: 503 } })
    const wrapper = mount(CollectionView, { global })
    await flushPromises()
    expect(wrapper.text()).toContain('First steps')
    expect(wrapper.text()).toContain('Progress unavailable')
    expect(wrapper.find('[data-testid="solved"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
