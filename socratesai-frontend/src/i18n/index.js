import { computed, readonly, ref } from 'vue'
import { messages } from './messages'

export const LOCALE_STORAGE_KEY = 'socrates-ui-locale'
export const languages = Object.freeze([
  { code: 'en', label: 'English', dateLocale: 'en-GB' },
  { code: 'ru', label: 'Русский', dateLocale: 'ru-RU' },
  { code: 'kk', label: 'Қазақша', dateLocale: 'kk-KZ' },
])
const locale = ref('en')
const supported = value => languages.some(language => language.code === value)

export function setLocale(value) {
  if (!supported(value)) return
  locale.value = value
  if (typeof document !== 'undefined') document.documentElement.lang = value
  try { localStorage.setItem(LOCALE_STORAGE_KEY, value) } catch { /* Storage may be disabled. */ }
}

export function initializeLocale() {
  let stored
  try { stored = localStorage.getItem(LOCALE_STORAGE_KEY) } catch { /* Use the browser preference. */ }
  const browser = typeof navigator === 'undefined' ? [] : navigator.languages?.length ? navigator.languages : [navigator.language]
  const preferred = browser.map(value => value?.toLowerCase().split('-')[0]).find(supported)
  setLocale(supported(stored) ? stored : preferred || 'en')
}

// Messages are plain text. Vue escapes values; translations never render HTML.
export function t(message, params = {}) {
  const dictionary = messages[locale.value]
  const translated = Object.hasOwn(dictionary, message) ? dictionary[message] : String(message ?? '')
  return translated.replace(/\{(\w+)\}/g, (match, name) => Object.hasOwn(params, name) ? String(params[name]) : match)
}

const dateLocale = computed(() => languages.find(language => language.code === locale.value).dateLocale)
export function useI18n() {
  return { locale: readonly(locale), languages, setLocale, t, dateLocale }
}
