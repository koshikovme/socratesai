// Authorized synthetic-account profile checks. Credentials, tokens and names stay out of output.
const fs = require('node:fs')
const path = require('node:path')
const { randomUUID } = require('node:crypto')
const { chromium, expect } = require('../../socratesai-frontend/node_modules/@playwright/test')

async function main() {
  const index = process.argv.indexOf('--access-file')
  if (index < 0 || !process.argv[index + 1]) throw Error('Private input required')
  const accessFile = path.resolve(process.argv[index + 1])
  const access = JSON.parse(fs.readFileSync(accessFile, 'utf8'))
  const base = access.base_url
  if (base !== 'https://socratesai.duckdns.org') throw Error('Unauthorized target')
  async function api(method, route, body, token) {
    const response = await fetch(base + route, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(20000) })
    return { status: response.status, body: await response.json() }
  }
  const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome', headless: true })
  let stage = 'login', pageErrors = 0, restored = 0
  const checks = []
  try {
    for (const role of ['teacherB', 'probe']) {
      const account = access.accounts[role]
      if (!account) throw Error('Synthetic fixture missing')
      const context = await browser.newContext({ locale: 'en-GB', viewport: { width: 390, height: 844 } })
      const page = await context.newPage()
      page.on('pageerror', () => { pageErrors++ })
      let baseline, changedEmail
      try {
        stage = role + '-profile'
        await page.goto(base + '/login')
        await page.locator('.language-switcher').selectOption('en')
        await page.getByLabel('Email', { exact: true }).fill(account.email)
        await page.getByLabel('Password', { exact: true }).fill(account.password)
        await page.getByRole('button', { name: 'Login', exact: true }).click()
        await expect(page).toHaveURL(/\/dashboard$/)
        const initialToken = await page.evaluate(() => localStorage.getItem('token'))
        const initial = await api('GET', '/api/users/me', null, initialToken)
        if (initial.status !== 200) throw Error('Profile unavailable')
        baseline = initial.body
        await page.goto(base + '/profile')
        await expect(page.locator('.profile-hero')).toContainText(baseline.email)
        await expect(page.locator('main')).not.toContainText(/Legacy activity|Execution Unknown|Mean latency|Latest mentor events/)
        if (role === 'teacherB') await expect(page.getByText('Your teaching workspace', { exact: true })).toBeVisible()
        else await expect(page.getByText('Progress by task', { exact: true })).toBeVisible()
        await page.getByRole('button', { name: 'Edit profile', exact: true }).click()
        await page.locator('#profile-name').fill('Profile acceptance fixture')
        await page.locator('#profile-university').fill('Acceptance university')
        await page.locator('#profile-group').fill('CHECK-ONLY')
        await page.locator('.language-switcher').selectOption('ru')
        await expect(page.locator('#profile-name')).toHaveValue('Profile acceptance fixture')
        await page.locator('.language-switcher').selectOption('kk')
        await page.getByRole('button', { name: 'Өзгерістерді сақтау', exact: true }).click()
        await expect(page.getByRole('status')).toHaveText('Профиль жаңартылды.')
        await page.reload()
        await expect(page.locator('.profile-hero')).toContainText('CHECK-ONLY')
        await expect(page.locator('.profile-hero')).toContainText('Acceptance university')
        if (!await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)) throw Error('Mobile overflow')
        await page.locator('.language-switcher').selectOption('en')
        if (role === 'probe') {
          stage = 'email-rotation'
          changedEmail = 'profile-check-' + randomUUID() + '@example.invalid'
          await page.getByRole('button', { name: 'Edit profile', exact: true }).click()
          await page.locator('#profile-email').fill(changedEmail)
          await page.locator('#profile-password').fill(account.password)
          await page.getByRole('button', { name: 'Save changes', exact: true }).click()
          await expect(page.getByRole('status')).toHaveText('Profile updated.')
          await page.reload()
          await expect(page.locator('.profile-hero')).toContainText(changedEmail)
          const old = await api('GET', '/api/users/me', null, initialToken)
          if (old.status !== 401) throw Error('Old email token accepted')
        }
        await page.screenshot({ path: path.join(path.dirname(accessFile), 'production-profile-' + role + '.png'), fullPage: true })
        checks.push({ role: role === 'probe' ? 'STUDENT' : 'TEACHER', savedAndReloaded: true, mobileWidth: 390, threeLanguageForm: true,
          researchPanelRemoved: true, emailRotation: role === 'probe' })
      } finally {
        await context.close()
        if (baseline) {
          stage = role + '-restore'
          let session
          for (const email of [account.email, changedEmail].filter(Boolean)) {
            const response = await api('POST', '/api/auth/login', { email, password: account.password })
            if (response.status === 200) { session = response.body; break }
          }
          if (!session || session.userId !== baseline.id) throw Error('Fixture restore identity failed')
          const current = await api('GET', '/api/users/me', null, session.token)
          if (current.status !== 200) throw Error('Fixture restore read failed')
          const response = await api('PUT', '/api/users/me/profile', { fullName: baseline.fullName, email: baseline.email,
            university: baseline.university, groupName: baseline.groupName, profileRevision: current.body.profileRevision,
            ...(current.body.email !== baseline.email ? { currentPassword: account.password } : {}) }, session.token)
          if (response.status !== 200 || ['fullName', 'email', 'university', 'groupName'].some(key => response.body[key] !== baseline[key])) throw Error('Fixture restore failed')
          restored++
        }
      }
    }
    stage = 'internal-submission-filter'
    const probeLogin = await api('POST', '/api/auth/login', access.accounts.probe)
    if (probeLogin.status !== 200) throw Error('Probe login failed')
    const history = await api('GET', '/api/submissions?size=1', null, probeLogin.body.token)
    if (history.status !== 200 || !history.body.items.length) throw Error('Preserved history missing')
    const submission = history.body.items[0]
    for (const role of ['teacher', 'admin']) {
      const login = await api('POST', '/api/auth/login', access.accounts[role])
      if (login.status !== 200) throw Error('Staff login failed')
      const list = await api('GET', `/api/teacher/problems/${submission.problemId}/submissions?studentId=${probeLogin.body.userId}`, null, login.body.token)
      if (list.status !== 200 || list.body.summary.attempts !== 0 || list.body.submissions.totalElements !== 0) throw Error('Internal result included')
      const review = await api('GET', '/api/teacher/submissions/' + submission.id, null, login.body.token)
      if (review.status !== 404) throw Error('Internal detail accessible')
    }
    if (pageErrors || restored !== 2) throw Error('Live profile checks incomplete')
    const evidence = { checks, originalProfilesRestored: restored, unexpectedPageErrors: pageErrors, realStudentAccountsModified: 0, internalSubmissionRowsExcluded: true, internalSubmissionDetailDenied: true, internalHistoryPreserved: true }
    fs.writeFileSync(path.join(path.dirname(accessFile), 'production-profile-browser.json'), JSON.stringify(evidence, null, 2), { mode: 0o600 })
    console.log(JSON.stringify(evidence))
  } catch {
    console.error('Live profile stage: ' + stage)
    throw Error('Profile acceptance failed')
  } finally { await browser.close() }
}
main().catch(() => { console.error('Authorized profile check failed; private diagnostics suppressed'); process.exitCode = 1 })
