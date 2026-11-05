// Authorized real HTTPS/WSS/Monaco journey; programs run exclusively on the remote executor.
const fs = require('node:fs')
const path = require('node:path')
const { chromium, expect } = require('../../socratesai-frontend/node_modules/@playwright/test')

async function main() {
  const flag = process.argv.indexOf('--access-file')
  if (flag < 0 || !process.argv[flag + 1]) throw Error('Private access file required')
  const file = path.resolve(process.argv[flag + 1])
  const access = JSON.parse(fs.readFileSync(file, 'utf8'))
  if (access.base_url !== 'https://socratesai.duckdns.org') throw Error('Unauthorized target')
  const programs = JSON.parse(fs.readFileSync(path.join(__dirname, '../../deploy/demo/programs.json'), 'utf8'))
  const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' })
  let stage = 'login'
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } })
    const page = await context.newPage()
    let executionPosts = 0
    page.on('request', request => {
      if (request.method() === 'POST' && /\/api\/problems\/\d+\/(runs|submissions)$/.test(new URL(request.url()).pathname)) executionPosts++
    })
    let connected = 0, messages = 0, stompErrors = 0, unexpectedPageErrors = 0, editorCancellations = 0
    page.on('pageerror', error => {
      if (error.name === 'Canceled' && error.message === 'Canceled') editorCancellations++
      else unexpectedPageErrors++
    })
    page.on('websocket', socket => socket.on('framereceived', event => {
      const payload = String(event.payload)
      let frames = [payload]
      if (payload.startsWith('a[')) {
        try { frames = JSON.parse(payload.slice(1)) } catch { return }
      }
      frames.forEach(frame => {
        const command = String(frame).split(/\r?\n/, 1)[0]
        if (command === 'CONNECTED') connected++
        if (command === 'MESSAGE') messages++
        if (command === 'ERROR') stompErrors++
      })
    }))
    await page.goto(access.base_url + '/login')
    await page.getByLabel('Email', { exact: true }).fill(access.accounts.student.email)
    await page.getByLabel('Password', { exact: true }).fill(access.accounts.student.password)
    await page.getByRole('button', { name: 'Login', exact: true }).click()
    await expect(page).toHaveURL(/\/dashboard$/)
    stage = 'workspace'
    await page.goto(access.base_url + '/problems/' + access.resources.problem_id)
    const editor = page.locator('.monaco-editor .view-lines')
    await expect(editor).toBeVisible({ timeout: 30000 })
    const write = async code => {
      await editor.click({ position: { x: 30, y: 10 } })
      await page.keyboard.press('ControlOrMeta+A')
      await page.keyboard.insertText(code)
    }
    stage = 'code-only-gemini-hint'
    await page.getByLabel('AI hints after a typing pause', { exact: true }).uncheck()
    await page.getByLabel('Language', { exact: true }).selectOption('java')
    await write('class Solution { public int sum(int a, int b) { return a + b; } }')
    await page.getByRole('button', { name: 'Ask mentor', exact: true }).click()
    await expect(page.locator('.policy-mode')).toContainText('Gemini classification and hint', { timeout: 30000 })
    await expect(page.locator('.feedback-text')).toContainText(/Main|main|entry point|точк.{0,12}вход/i)
    await expect(page.locator('.feedback-card .signal-pill').first()).toHaveText('Code review only')
    if (executionPosts !== 0) throw Error('Code-only hint unexpectedly executed a program')
    if (await page.locator('.app-header .user-chip, .app-header .role-chip').count()) throw Error('Header exposes redundant identity chips')
    stage = 'starter-program'
    page.once('dialog', dialog => dialog.accept())
    await page.getByRole('button', { name: 'Reset to starter', exact: true }).click()
    await expect(editor).toContainText('Main')
    await expect(editor).toContainText('solve')
    await page.getByRole('button', { name: 'Run samples', exact: true }).click()
    await expect(page.getByRole('region', { name: 'Execution result' })).toContainText('WRONG_ANSWER', { timeout: 120000 })
    stage = 'language-buffers'
    await page.getByLabel('Language', { exact: true }).selectOption('python3')
    await write(programs.python3)
    await page.getByLabel('Language', { exact: true }).selectOption('java')
    await write(programs.java)
    await page.getByLabel('Language', { exact: true }).selectOption('python3')
    await expect(editor).toContainText('print')
    stage = 'sample-run'
    await page.getByRole('button', { name: 'Run samples', exact: true }).click()
    const result = page.getByRole('region', { name: 'Execution result' })
    await expect(result).toContainText('ACCEPTED', { timeout: 120000 })
    stage = 'graded-submission'
    await page.getByRole('button', { name: 'Submit', exact: true }).click()
    await expect(result).toContainText('Graded submission')
    await expect(result).toContainText('ACCEPTED', { timeout: 120000 })
    await expect(result).toContainText('2 / 2 tests passed')
    stage = 'mentor'
    await expect(page.locator('.feedback-support')).toContainText('connected', { timeout: 30000 })
    await expect(page.locator('.policy-mode')).toBeVisible({ timeout: 30000 })
    await page.screenshot({ path: path.join(path.dirname(file), 'production-workspace.png'), fullPage: true })
    if (!connected || !messages || stompErrors || unexpectedPageErrors) throw Error('Protocol check failed')
    stage = 'logout'
    await page.getByRole('button', { name: 'Logout', exact: true }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.goto(access.base_url + '/problems/' + access.resources.problem_id)
    await expect(page).toHaveURL(/\/login$/)
    const evidence = { https: true, monaco: true, bufferIsolation: true, codeOnlyGeminiHint: true, hintBeforeAnyExecution: true,
      starterCompilesWithoutSolving: true, compactHeader: true, realRun: 'ACCEPTED', realSubmit: 'ACCEPTED',
      wssConnected: connected, mentorMessages: messages, stompErrors, unexpectedPageErrors, editorCancellations, logout: true }
    fs.writeFileSync(path.join(path.dirname(file), 'production-browser.json'), JSON.stringify(evidence, null, 2), { mode: 0o600 })
    console.log(JSON.stringify(evidence))
  } catch {
    console.error('Live browser stage: ' + stage)
    throw Error('Live browser check failed at ' + stage)
  } finally {
    await browser.close()
  }
}

main().catch(() => { console.error('Authorized live browser journey failed; private diagnostics suppressed'); process.exitCode = 1 })
