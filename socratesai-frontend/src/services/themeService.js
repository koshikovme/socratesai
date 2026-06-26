const THEME_KEY = 'socrates-theme'

export function getStoredTheme() {
    return localStorage.getItem(THEME_KEY) || 'light'
}

export function applyTheme(theme) {
    const normalized = theme === 'dark' ? 'dark' : 'light'
    document.documentElement.dataset.theme = normalized
    localStorage.setItem(THEME_KEY, normalized)
    window.dispatchEvent(new CustomEvent('socrates-theme-change', { detail: normalized }))
    return normalized
}

export function toggleTheme() {
    return applyTheme(getStoredTheme() === 'dark' ? 'light' : 'dark')
}
