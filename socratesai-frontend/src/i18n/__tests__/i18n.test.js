import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { initializeLocale, languages, LOCALE_STORAGE_KEY, setLocale, t, useI18n } from '../index'
import { translations } from '../messages'
import { apiErrorMessage } from '../../services/errors'
import { useAuthStore } from '../../stores/auth'
import LanguageSwitcher from '../../components/LanguageSwitcher.vue'
import ExecutionResult from '../../components/ExecutionResult.vue'
import FeedbackPanel from '../../components/FeedbackPanel.vue'

const wrappers = []
const render = (component, props) => {
  const wrapper = mount(component, { props }); wrappers.push(wrapper); return wrapper
}
beforeEach(() => { localStorage.clear(); setLocale('en') })
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks(); setLocale('en') })

describe('interface languages', () => {
  test('persists all three native language choices and updates existing UI without a reload', async () => {
    const picker = render(LanguageSwitcher)
    const label = render({ setup: useI18n, template: '<span>{{ t("Ask mentor") }}</span>' })
    expect(picker.findAll('option').map(option => option.text())).toEqual(['English', 'Русский', 'Қазақша'])
    await picker.find('select').setValue('ru')
    expect(label.text()).toBe('Спросить ментора')
    expect(document.documentElement.lang).toBe('ru')
    await picker.find('select').setValue('kk')
    expect(label.text()).toBe('Ментордан сұрау')
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('kk')
    expect(document.documentElement.lang).toBe('kk')
    setLocale('invalid')
    expect(useI18n().locale.value).toBe('kk')
    setActivePinia(createPinia())
    useAuthStore().logout()
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('kk')
  })

  test('restores the saved choice before considering browser languages', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['ru-RU', 'en-US'])
    localStorage.setItem(LOCALE_STORAGE_KEY, 'kk')
    initializeLocale()
    expect(useI18n().locale.value).toBe('kk')
    localStorage.setItem(LOCALE_STORAGE_KEY, 'unsupported')
    initializeLocale()
    expect(useI18n().locale.value).toBe('ru')
  })

  test('uses Kazakh browser preferences and safely falls back for unsupported browsers or blocked storage', () => {
    localStorage.removeItem(LOCALE_STORAGE_KEY)
    const browser = vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['kk-KZ'])
    initializeLocale()
    expect(useI18n().dateLocale.value).toBe('kk-KZ')
    browser.mockReturnValue(['fr-FR'])
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw Error('Storage blocked') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw Error('Storage blocked') })
    expect(() => initializeLocale()).not.toThrow()
    expect(t('Ask mentor')).toBe('Ask mentor')
    setLocale('ru')
    expect(t('Ask mentor')).toBe('Спросить ментора')
  })

  test('preserves named parameters, literal code and escaped user content', () => {
    setLocale('kk')
    expect(t('Page {page} of {pages} · {total} total', { page: 2, pages: 4, total: 35 })).toBe('2 / 4 бет · барлығы 35')
    expect(t('constructor')).toBe('constructor')
    expect(t('Unknown future enum')).toBe('Unknown future enum')
    const wrapper = render({ setup: () => ({ t }), template: '<p>{{ t("Move {title} up", { title: "<img src=x onerror=alert(1)>" }) }}</p>' })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })

  test('every message has three complete translations and the same interpolation contract', () => {
    const keys = translations.map(row => row[0])
    expect(new Set(keys).size).toBe(keys.length)
    const parameters = text => [...text.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort()
    translations.forEach(([en, ru, kk]) => {
      expect(ru?.trim(), en).toBeTruthy(); expect(kk?.trim(), en).toBeTruthy()
      expect(parameters(ru), en).toEqual(parameters(en)); expect(parameters(kk), en).toEqual(parameters(en))
    })
    expect(languages.map(language => language.code)).toEqual(['en', 'ru', 'kk'])
  })

  test('changes safe error messages with the interface without displaying backend details', async () => {
    const error = apiErrorMessage({ response: { status: 409, data: { code: 'CONFLICT', message: 'SQL private secret' } } })
    const wrapper = render({ setup: () => ({ t, error }), template: '<p role="alert">{{ t(error) }}</p>' })
    setLocale('ru'); await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Данные изменились')
    setLocale('kk'); await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Деректер өзгерді')
    expect(wrapper.text()).not.toContain('SQL private secret')
  })

  test.each(['ru', 'kk'])('keeps hidden output private in translated execution results (%s)', locale => {
    setLocale(locale)
    const wrapper = render(ExecutionResult, {
      operation: { kind: 'submission', problemVersionId: 1, languageKey: 'java' }, phase: 'complete',
      result: { submission: { verdict: 'WRONG_ANSWER', testsPassed: 0, testsTotal: 1 }, cases: [{ hidden: true, position: 0, verdict: 'WRONG_ANSWER', stdout: 'HIDDEN_ECHO', stderr: 'SECRET_DIAGNOSTIC' }] },
    })
    expect(wrapper.text()).toContain(t('WRONG_ANSWER'))
    expect(wrapper.text()).toContain(t('Hidden test'))
    expect(wrapper.text()).not.toContain('HIDDEN_ECHO')
    expect(wrapper.text()).not.toContain('SECRET_DIAGNOSTIC')
  })

  test('translates mentor chrome while retaining generated advice and execution uncertainty', () => {
    setLocale('ru')
    const wrapper = render(FeedbackPanel, { connectionLabel: 'connected', feedback: {
      policyMode: 'GEMINI', feedbackSource: 'gemini', feedbackText: 'Авторский текст совета.',
      action: 'CONCEPTUAL_HINT', evidenceOrigin: 'STATIC_HEURISTIC', executionKnown: false, testsPassed: null,
    } })
    expect(wrapper.text()).toContain('Классификация и подсказка Gemini')
    expect(wrapper.text()).toContain('Только анализ кода')
    expect(wrapper.text()).toContain('Не запускались')
    expect(wrapper.text()).toContain('Подключено')
    expect(wrapper.text()).toContain('Авторский текст совета.')
  })
})
