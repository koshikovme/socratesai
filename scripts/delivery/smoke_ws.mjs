// Native browser-compatible WebSocket framing through the actual TLS edge and SockJS server.
const origin = process.env.SMOKE_WS_ORIGIN
const token = process.env.SMOKE_WS_TOKEN
const ws = new WebSocket(`${origin.replace('https:', 'wss:')}/ws/000/delivery/websocket`)
const timeout = setTimeout(() => { console.error('STOMP smoke timed out'); process.exit(1) }, 12000)
let connected = false
ws.onmessage = event => {
  const body = String(event.data)
  if (body === 'o') {
    ws.send(JSON.stringify([`CONNECT\naccept-version:1.2\nAuthorization:Bearer ${token}\nheart-beat:0,0\n\n\0`]))
  } else if (body.startsWith('a')) {
    for (const frame of JSON.parse(body.slice(1))) {
      if (frame.startsWith('CONNECTED')) {
        connected = true
        ws.send(JSON.stringify(['SUBSCRIBE\nid:own-feedback\ndestination:/user/queue/feedback\n\n\0']))
        ws.send(JSON.stringify(['DISCONNECT\nreceipt:done\n\n\0']))
      } else if (frame.startsWith('RECEIPT')) {
        clearTimeout(timeout); ws.close(); process.exit(0)
      } else if (frame.startsWith('ERROR')) { console.error('STOMP smoke rejected'); process.exit(1) }
    }
  }
}
ws.onerror = () => { console.error('TLS WebSocket smoke failed'); process.exit(1) }
ws.onclose = () => { if (!connected) { console.error('STOMP smoke closed before authentication'); process.exit(1) } }
