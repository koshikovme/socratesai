// Thirty authenticated native WSS sessions; no program is executed in this process.
const fs = require('node:fs')
const { randomUUID } = require('node:crypto')
const state = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
if (state.base_url !== 'https://socratesai.duckdns.org' || state.identities.length > 30) process.exit(1)
const clients = [], timers = []
let messages = 0
Promise.all(state.identities.map(identity => new Promise((resolve, reject) => {
  const socket = new WebSocket(state.base_url.replace('https:', 'wss:') + '/ws/websocket')
  clients.push(socket)
  const timeout = setTimeout(() => reject(Error()), 30000)
  socket.onopen = () => socket.send('CONNECT\naccept-version:1.2\nhost:socratesai.duckdns.org\nAuthorization:Bearer ' + identity.token + '\n\n\0')
  socket.onmessage = event => {
    const frame = String(event.data)
    if (frame.startsWith('CONNECTED')) {
      socket.send('SUBSCRIBE\nid:own\ndestination:/user/queue/feedback\n\n\0')
      const payload = JSON.stringify({ problemId: state.problem_id, problemVersionId: state.version_id,
        languageKey: 'java', editorSessionId: randomUUID(), codeRevision: 1, code: 'class Main {}' })
      socket.send('SEND\ndestination:/app/code.update\ncontent-type:application/json\ncontent-length:' + Buffer.byteLength(payload) + '\n\n' + payload + '\0')
      timers.push(setInterval(() => { if (socket.readyState === WebSocket.OPEN) socket.send('\n') }, 10000))
    } else if (frame.startsWith('MESSAGE')) {
      const body = JSON.parse(frame.slice(frame.indexOf('\n\n') + 2).replace(/\0$/, ''))
      if (body.policyMode !== 'RULE' || body.fallbackReason !== 'UNSUPPORTED_LANGUAGE') return reject(Error())
      clearTimeout(timeout); messages++; resolve()
    } else if (frame.startsWith('ERROR')) reject(Error())
  }
  socket.onerror = () => reject(Error())
}))).then(() => {
  fs.writeFileSync(state.ready_file, JSON.stringify({ sessions: clients.length, mentorMessages: messages }))
  process.stdin.once('data', () => {
    timers.forEach(clearInterval); clients.forEach(socket => socket.close())
    setTimeout(() => process.exit(0), 200)
  })
}).catch(() => { console.error('Bounded WSS capacity check failed'); process.exit(1) })
