// Authorized UI-only check. It never sends code to a judge or changes staff resources.
const fs = require('node:fs')
const path = require('node:path')
const { chromium, expect } = require('../../socratesai-frontend/node_modules/@playwright/test')

async function main() {
  const flag = process.argv.indexOf('--access-file')
  if (flag < 0 || !process.argv[flag + 1]) throw Error('Private access file required')
  const file = path.resolve(process.argv[flag + 1])
  const access = JSON.parse(fs.readFileSync(file, 'utf8'))
  if (access.base_url !== 'https://socratesai.duckdns.org') throw Error('Unauthorized target')
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' })
  let stage = 'login'
  try {
    const context = await browser.newContext({ locale: 'en-GB', viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    let executionPosts = 0, staffWrites = 0, unexpectedPageErrors = 0
    page.on('request', request => {
      if (request.method() === 'POST' && /\/api\/problems\/\d+\/(runs|submissions)$/.test(new URL(request.url()).pathname)) executionPosts++
      if (['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method()) && /\/api\/(teacher|admin)\//.test(new URL(request.url()).pathname)) staffWrites++
    })
    page.on('pageerror', error => { if (error.name !== 'Canceled' || error.message !== 'Canceled') unexpectedPageErrors++ })
    const select = locale => page.locator('.language-switcher').selectOption(locale)
    const login = async role => {
      await page.goto(access.base_url + '/login')
      await select('en')
      await page.getByLabel('Email', { exact: true }).fill(access.accounts[role].email)
      await page.getByLabel('Password', { exact: true }).fill(access.accounts[role].password)
      await page.getByRole('button', { name: 'Login', exact: true }).click()
      await expect(page).toHaveURL(/\/dashboard$/)
    }
    const fits = async () => {
      if (!await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)) throw Error('Viewport overflow')
    }
    await login('admin')
    stage = 'admin-russian-kazakh'
    await select('ru')
    await page.getByRole('link', { name: 'Роли', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Управление ролями пользователей', exact: true })).toBeVisible()
    await fits()
    await select('kk')
    await expect(page.getByRole('heading', { name: 'Пайдаланушы рөлдерін басқару', exact: true })).toBeVisible()
    await fits()
    await page.getByRole('button', { name: 'Шығу', exact: true }).click()
    await expect(page.locator('.language-switcher')).toHaveValue('kk')
    await page.reload()
    await expect(page.locator('html')).toHaveAttribute('lang', 'kk')

    stage = 'teacher-form'
    await login('teacher')
    await page.getByRole('link', { name: 'Teaching', exact: true }).click()
    await page.getByRole('link', { name: 'New problem', exact: true }).click()
    await page.getByLabel('Title', { exact: true }).fill('Unsaved localization fixture')
    await select('ru')
    await expect(page.getByLabel('Название', { exact: true })).toHaveValue('Unsaved localization fixture')
    await expect(page.getByRole('button', { name: 'Сохранить черновик', exact: true })).toBeVisible()
    await fits()
    await select('kk')
    await expect(page.getByLabel('Атауы', { exact: true })).toHaveValue('Unsaved localization fixture')
    await expect(page.getByRole('button', { name: 'Жобаны сақтау', exact: true })).toBeVisible()
    await fits()
    await page.getByLabel('Атауы', { exact: true }).fill('')
    await page.getByRole('button', { name: 'Шығу', exact: true }).click()

    stage = 'student-workspace'
    await login('student')
    await page.goto(access.base_url + '/problems/' + access.resources.problem_id)
    await page.locator('.inline-check input').uncheck()
    const editor = page.locator('.monaco-editor .view-lines')
    await expect(editor).toBeVisible({ timeout: 30000 })
    await editor.click({ position: { x: 30, y: 10 } })
    await page.keyboard.press('ControlOrMeta+A')
    await page.keyboard.insertText('# Unsaved localization fixture')
    await select('ru')
    await expect(page.getByRole('button', { name: 'Спросить ментора', exact: true })).toBeVisible()
    await expect(editor).toContainText('Unsaved localization fixture')
    await fits()
    await select('kk')
    await expect(page.getByRole('button', { name: 'Ментордан сұрау', exact: true })).toBeVisible()
    await expect(editor).toContainText('Unsaved localization fixture')
    await fits()
    await page.screenshot({ path: path.join(path.dirname(file), 'production-localization.png'), fullPage: true })
    await select('en')
    await expect(page.getByRole('button', { name: 'Ask mentor', exact: true })).toBeVisible()
    await expect(editor).toContainText('Unsaved localization fixture')
    await page.getByRole('button', { name: 'Logout', exact: true }).click()
    await expect(page).toHaveURL(/\/login$/)
    if (executionPosts || staffWrites || unexpectedPageErrors) throw Error('Unexpected side effect')
    const evidence = { languages: ['en', 'ru', 'kk'], admin: true, teacher: true, student: true, persistedAfterReloadAndLogout: true,
      unsavedFormPreserved: true, monacoDraftPreserved: true, mobileWidth: 390, executionPosts, staffWrites, unexpectedPageErrors }
    fs.writeFileSync(path.join(path.dirname(file), 'production-localization.json'), JSON.stringify(evidence, null, 2), { mode: 0o600 })
    console.log(JSON.stringify(evidence))
  } catch {
    console.error('Live localization stage: ' + stage)
    throw Error('Live localization check failed')
  } finally {
    await browser.close()
  }
}
main().catch(() => { console.error('Authorized localization check failed; private diagnostics suppressed'); process.exitCode = 1 })
