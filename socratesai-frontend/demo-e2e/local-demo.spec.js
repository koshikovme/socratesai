import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'

const programs = JSON.parse(readFileSync(new URL('../../deploy/demo/programs.json', import.meta.url)))

test('actual local UI -> native API -> private synthetic adapter -> progress and logout', async ({ page }) => {
  const errors = []
  let stage = 'registration'
  page.on('pageerror', () => errors.push('pageerror'))
  page.on('console', message => {
    if (message.type() !== 'error') return
    const location = message.location().url
    const resource = location ? new URL(location) : null
    errors.push({
      kind: /stomp|sockjs|websocket/i.test(message.text()) ? 'socket' : 'resource',
      path: resource ? resource.hostname + resource.pathname : 'unknown',
      status: message.text().match(/\b[45]\d{2}\b/)?.[0] ?? 'network',
      stage,
    })
  })
  await page.goto('/register')
  await expect(page.getByTestId('demo-banner')).toBeVisible()
  await page.getByLabel('Full name', { exact: true }).fill('Local browser probe')
  await page.getByLabel('Email', { exact: true }).fill(`browser-${randomUUID()}@socrates.demo.invalid`)
  await page.getByLabel('Password', { exact: true }).fill(randomUUID())
  await page.getByRole('button', { name: 'Register', exact: true }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  stage = 'workspace'
  await page.getByRole('link', { name: 'Open problem', exact: true }).first().click()
  await expect(page.getByRole('heading', { name: 'ДЕМО · Сумма двух чисел', exact: true })).toBeVisible()
  const editor = page.locator('.monaco-editor .view-lines')
  await page.getByLabel('Language', { exact: true }).selectOption('python3')
  await editor.click({ position: { x: 30, y: 10 } })
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.insertText('print(0)')
  await expect(page.getByText('connected', { exact: true })).toBeVisible()
  await expect(page.locator('.feedback-text')).toBeVisible({ timeout: 20000 })
  await page.getByRole('button', { name: 'Run samples', exact: true }).click()
  const result = page.getByRole('region', { name: 'Execution result' })
  await expect(result).toContainText('WRONG_ANSWER', { timeout: 20000 })
  await editor.click({ position: { x: 30, y: 10 } })
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.insertText(programs.python3)
  await page.getByRole('button', { name: 'Run samples', exact: true }).click()
  await expect(result).toContainText('ACCEPTED', { timeout: 20000 })
  await page.getByRole('button', { name: 'Submit', exact: true }).click()
  await expect(result).toContainText('Server attempt 1')
  await expect(result).toContainText('ACCEPTED', { timeout: 20000 })
  await expect(result).toContainText('Hidden test')
  await page.screenshot({ path: '../.delivery/demo/workspace.png', fullPage: true })
  stage = 'profile'
  await page.getByRole('link', { name: 'Profile', exact: true }).click()
  await expect(page.getByText('Solved', { exact: true })).toBeVisible()
  stage = 'logout'
  await page.getByRole('button', { name: 'Logout', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => localStorage.getItem('token'))).toBeNull()
  expect(errors).toEqual([])
})
