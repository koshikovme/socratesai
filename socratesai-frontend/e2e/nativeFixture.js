import { createHash } from 'node:crypto'

// Synthetic protocol fixtures, never a connection to a shared DB or live judge.
export async function installNativeFixture(page) {
  const state = { problem: null, collection: null, accepted: false, submissions: [], snapshots: [], posts: [], polls: {} }
  const languageKeys = ['python3', 'java', 'cpp17']
  const profile = role => ({ id: role === 'TEACHER' ? 1 : 2, role, email: `${role.toLowerCase()}@example.test`, fullName: role === 'TEACHER' ? 'Teacher Test' : 'Student Test' })
  const roleFor = request => request.headers().authorization?.includes('teacher') ? 'TEACHER' : 'STUDENT'
  const publicProblem = () => ({ ...state.problem?.version, id: 11, problemVersionId: 21, samples: [{ position: 0, inputData: '2 3', expectedOutput: '5' }], tests: undefined })
  const summary = () => ({ id: 11, problemVersionId: 21, title: state.problem?.version.title, topic: 'Basics', difficulty: 'EASY' })
  const paged = items => ({ items, page: 0, size: 20, totalElements: items.length })
  const respond = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    const body = method === 'GET' ? null : request.postDataJSON()
    const role = roleFor(request)
    if (path === '/api/auth/login') { const type = body.email.startsWith('teacher') ? 'TEACHER' : 'STUDENT'; return respond(route, { ...profile(type), userId: profile(type).id, token: `${type.toLowerCase()}-fixture-token` }) }
    if (path === '/api/users/me') return respond(route, profile(role))
    if (path === '/api/users/me/settings') return respond(route, { ...profile(role), ...body })
    if (path === '/api/languages') return respond(route, languageKeys.map(languageKey => ({ languageKey, runtimeVersion: 'fixture' })))
    if (path.startsWith('/api/teacher/') && role !== 'TEACHER') return respond(route, { code: 'FORBIDDEN' }, 403)
    if (path === '/api/teacher/problems' && method === 'POST') {
      state.problem = { id: 11, ownerId: 1, status: 'DRAFT', visibility: body.visibility, publishedVersionId: null, lockVersion: 0, version: { ...body, id: 21, versionNumber: 1, status: 'DRAFT' } }
      return respond(route, state.problem, 201)
    }
    if (path === '/api/teacher/problems') return respond(route, paged(state.problem ? [state.problem] : []))
    if (path === '/api/teacher/problems/11') return respond(route, state.problem)
    if (path === '/api/teacher/problems/11/verify') { state.reference = body; return respond(route, { id: 'verification', problemVersionId: 21, status: 'QUEUED', testsTotal: 6, testsPassed: 0 }, 202) }
    if (path.includes('/verifications/')) return respond(route, { id: 'verification', problemVersionId: 21, status: 'FINISHED', verdict: 'ACCEPTED', testsTotal: 6, testsPassed: 6, validUntil: new Date(Date.now() + 300000).toISOString() })
    if (path === '/api/teacher/problems/11/publish') { state.problem.status = 'PUBLISHED'; state.problem.publishedVersionId = 21; state.problem.version.status = 'PUBLISHED'; return respond(route, state.problem) }
    if (path === '/api/teacher/collections' && method === 'POST') { state.collection = { ...body, id: 31, ownerId: 1, status: 'DRAFT', lockVersion: 0, problems: body.problems.map((item, position) => ({ ...item, title: state.problem.version.title, position })) }; return respond(route, state.collection, 201) }
    if (path === '/api/teacher/collections') return respond(route, paged(state.collection ? [state.collection] : []))
    if (path === '/api/teacher/collections/31') return respond(route, state.collection)
    if (path === '/api/teacher/collections/31/publish') { state.collection.status = 'PUBLISHED'; return respond(route, state.collection) }
    if (path === '/api/problems') return respond(route, paged(state.problem?.status === 'PUBLISHED' ? [summary()] : []))
    if (path === '/api/problems/11/versions/21' || path === '/api/problems/11') return respond(route, publicProblem())
    if (path === '/api/collections') return respond(route, paged(state.collection?.status === 'PUBLISHED' ? [state.collection] : []))
    if (path === '/api/collections/31') return respond(route, state.collection)
    if (path === '/api/collections/31/progress') return respond(route, { total: 1, solved: state.accepted ? 1 : 0, problems: [{ problemId: 11, problemVersionId: 21, solved: state.accepted }] })
    if (path === '/api/users/me/progress') return respond(route, { progressSource: 'JUDGED_SUBMISSION', profile: profile(role), summary: { solvedTasks: state.accepted ? 1 : 0, attemptedTasks: state.submissions.length ? 1 : 0, totalAttempts: state.submissions.length }, tasks: state.accepted ? [{ taskId: 11, title: 'Sum two integers', status: 'SOLVED', openable: true }] : [] })
    if (/\/api\/problems\/11\/(runs|submissions)$/.test(path)) {
      state.posts.push({ path, body })
      const sample = path.endsWith('/runs')
      const id = sample ? 'run-fixture' : 'submission-fixture'
      const value = { id, problemId: 11, problemVersionId: 21, languageKey: body.languageKey, status: 'QUEUED', attemptNumber: sample ? undefined : 1 }
      if (!sample) state.submissions = [value]
      return respond(route, value, 202)
    }
    if (path === '/api/submissions') return respond(route, paged(state.submissions))
    if (/\/api\/(runs|submissions)\//.test(path)) {
      state.polls[path] = (state.polls[path] || 0) + 1
      const sample = path.includes('/runs/')
      const done = state.polls[path] > 1
      if (done && !sample) state.accepted = true
      const value = { id: sample ? 'run-fixture' : 'submission-fixture', problemId: 11, problemVersionId: 21, languageKey: state.posts.at(-1)?.body.languageKey, status: done ? 'FINISHED' : 'RUNNING', verdict: done ? 'ACCEPTED' : null, attemptNumber: sample ? undefined : 1, testsTotal: sample ? 1 : 2, testsPassed: done ? (sample ? 1 : 2) : 0 }
      if (!sample) state.submissions = [value]
      const cases = done ? [{ position: 0, hidden: false, verdict: 'ACCEPTED', timeConsumedMs: 5, memoryConsumedBytes: 2048 }, ...(!sample ? [{ position: 1, hidden: true, verdict: 'ACCEPTED', stdout: 'HIDDEN_ECHO_MUST_NOT_RENDER', stderr: 'HIDDEN_DIAGNOSTICS', timeConsumedMs: 7, memoryConsumedBytes: 4096 }] : [])] : []
      return respond(route, sample ? { ...value, cases } : { submission: value, retryScheduled: false, cases })
    }
    if (path.startsWith('/api/interactions/')) return respond(route, {})
    if (path === '/api/mentor/native-feedback') return respond(route, mentorFeedback(body, 'HTTP'))
    return respond(route, { code: 'NOT_FOUND' }, 404)
  })
  function mentorFeedback(snapshot, source = 'WS') {
    return { ...Object.fromEntries(['problemId', 'problemVersionId', 'languageKey', 'editorSessionId', 'codeRevision'].map(key => [key, snapshot[key]])),
      codeHash: createHash('sha256').update(snapshot.code).digest('hex'), policyMode: snapshot.languageKey === 'python3' ? 'ML' : 'RULE',
      fallbackReason: snapshot.languageKey === 'python3' ? null : 'UNSUPPORTED_LANGUAGE', executionKnown: false, compileSuccess: null, testsPassed: null, testsFailed: null,
      interactionId: 'interaction-fixture', feedbackText: `Review your input parsing (${source}).`, action: 'CONCEPTUAL_HINT' }
  }
  await page.route('**/ws/info*', route => respond(route, { websocket: true, cookie_needed: false, origins: ['*:*'], entropy: 123 }))
  await page.routeWebSocket(/\/ws\/.*\/websocket/, ws => {
    let subscription = 'sub-0'
    ws.send('o')
    const sendFrame = frame => ws.send(`a${JSON.stringify([frame])}`)
    ws.onMessage(raw => {
      const messages = JSON.parse(String(raw))
      messages.forEach(frame => {
        if (frame.startsWith('CONNECT')) sendFrame('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0')
        if (frame.startsWith('SUBSCRIBE')) subscription = /\nid:([^\n]+)/.exec(frame)?.[1] || subscription
        if (frame.startsWith('SEND')) {
          const snapshot = JSON.parse(frame.slice(frame.indexOf('\n\n') + 2).replace(/\0$/, ''))
          state.snapshots.push(snapshot)
          const reply = JSON.stringify(mentorFeedback(snapshot))
          sendFrame(`MESSAGE\nsubscription:${subscription}\nmessage-id:fixture-${state.snapshots.length}\ndestination:/user/queue/feedback\ncontent-type:application/json\n\n${reply}\0`)
        }
      })
    })
  })
  return state
}
