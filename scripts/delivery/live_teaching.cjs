// Authorized synthetic staff/student UI review. Source and credentials never enter diagnostics.
const fs = require('node:fs')
const path = require('node:path')
const { chromium, expect } = require('../../socratesai-frontend/node_modules/@playwright/test')

async function main() {
  const option = name => { const index = process.argv.indexOf(name); if (index < 0 || !process.argv[index + 1]) throw Error('Missing private input'); return path.resolve(process.argv[index + 1]) }
  const accessFile = option('--access-file'), stateFile = option('--pack-state')
  const access = JSON.parse(fs.readFileSync(accessFile, 'utf8')), pack = JSON.parse(fs.readFileSync(stateFile, 'utf8'))
  if (access.base_url !== 'https://socratesai.duckdns.org') throw Error('Unauthorized target')
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' })
  let stage = 'login'
  try {
    const context = await browser.newContext({ locale: 'en-GB', viewport: { width: 390, height: 844 } })
    const page = await context.newPage()
    let unexpectedPageErrors = 0
    page.on('pageerror', error => { if (error.name !== 'Canceled' || error.message !== 'Canceled') unexpectedPageErrors++ })
    const login = async role => {
      await page.goto(access.base_url + '/login')
      await page.locator('.language-switcher').selectOption('en')
      await page.getByLabel('Email', { exact: true }).fill(access.accounts[role].email)
      await page.getByLabel('Password', { exact: true }).fill(access.accounts[role].password)
      await page.getByRole('button', { name: 'Login', exact: true }).click()
      await expect(page).toHaveURL(/\/dashboard$/)
    }
    await login('teacher')
    stage = 'collection-results'
    await page.goto(`${access.base_url}/teacher/collections/${pack.collections.java.id}/results`)
    await page.getByLabel('Student name, email or group', { exact: true }).fill(access.accounts.probe.email)
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click()
    await expect(page.locator('tbody')).toContainText('ACCEPTED')
    await expect(page.locator('tbody')).toContainText('WRONG_ANSWER')
    if (!await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)) throw Error('Viewport overflow')
    await page.screenshot({ path: path.join(path.dirname(accessFile), 'production-teaching-results.png'), fullPage: true })
    await page.getByRole('button', { name: 'View attempt', exact: true }).first().click()
    await expect(page.locator('.review-detail pre')).toContainText('add(')
    await page.getByRole('button', { name: 'Close', exact: true }).click()
    await page.getByRole('button', { name: 'Logout', exact: true }).click()
    stage = 'admin-results'
    await login('admin')
    await page.goto(`${access.base_url}/teacher/problems/${pack.problems['java:sum-two'].id}/results`)
    await expect(page.locator('tbody')).toContainText('ACCEPTED')
    await page.getByRole('button', { name: 'Logout', exact: true }).click()
    stage = 'foreign-teacher'
    await login('teacherB')
    await page.goto(`${access.base_url}/teacher/collections/${pack.collections.java.id}/results`)
    await expect(page.getByRole('alert')).toContainText('unavailable')
    await expect(page.locator('tbody')).toHaveCount(0)
    await page.getByRole('button', { name: 'Logout', exact: true }).click()
    stage = 'student-starter'
    await login('student')
    await page.goto(`${access.base_url}/problems/${pack.problems['java:sum-two'].id}?version=${pack.problems['java:sum-two'].versionId}&collection=${pack.collections.java.id}`)
    await page.locator('.inline-check input').uncheck()
    await expect(page.locator('.monaco-editor .view-lines')).toContainText('firstNumber', { timeout: 30000 })
    await expect(page.locator('.monaco-editor .view-lines')).toContainText('secondNumber')
    await expect(page.locator('.program-contract')).toContainText('function marked TODO')
    await page.goto(`${access.base_url}/teacher/collections/${pack.collections.java.id}/results`)
    await expect(page).toHaveURL(/\/dashboard$/)
    await page.getByRole('button', { name: 'Logout', exact: true }).click()
    if (unexpectedPageErrors) throw Error('Unexpected page error')
    const evidence = { teacherCollectionResults: true, adminProblemResults: true, foreignTeacherDenied: true, studentStaffRouteDenied: true,
      typedJavaStarter: true, sourceOnExplicitReview: true, mobileWidth: 390, unexpectedPageErrors }
    fs.writeFileSync(path.join(path.dirname(accessFile), 'production-teaching-browser.json'), JSON.stringify(evidence, null, 2), { mode: 0o600 })
    console.log(JSON.stringify(evidence))
  } catch {
    console.error('Live teaching stage: ' + stage)
    throw Error('Live teaching check failed')
  } finally { await browser.close() }
}
main().catch(() => { console.error('Authorized teaching check failed; private diagnostics suppressed'); process.exitCode = 1 })
