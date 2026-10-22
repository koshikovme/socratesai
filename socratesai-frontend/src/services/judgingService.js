import api from './api'

export async function createJudging(kind, problemId, payload, { signal } = {}) {
  const path = kind === 'sample' ? 'runs' : 'submissions'
  const { data } = await api.post(`/api/problems/${problemId}/${path}`, payload, { signal })
  return data
}

export async function fetchJudging(kind, id, { signal } = {}) {
  const path = kind === 'sample' ? 'runs' : 'submissions'
  const { data } = await api.get(`/api/${path}/${id}`, { signal })
  return data
}

export async function fetchSubmissions({ page = 0, size = 20, signal } = {}) {
  const { data } = await api.get('/api/submissions', { params: { page, size }, signal })
  return data
}

export const judgingSummary = result => result?.submission ?? result
export const isJudgingTerminal = result => ['FINISHED', 'SYSTEM_ERROR'].includes(judgingSummary(result)?.status) && !result?.retryScheduled

export function delay(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal.aborted) return reject(new DOMException('Cancelled', 'AbortError'))
    const aborted = () => { clearTimeout(timer); reject(new DOMException('Cancelled', 'AbortError')) }
    const timer = setTimeout(() => { signal.removeEventListener('abort', aborted); resolve() }, ms)
    signal.addEventListener('abort', aborted, { once: true })
  })
}

// Poll only GETs. A SYSTEM_ERROR with retryScheduled is still in recovery.
export async function pollJudging(kind, id, { signal, onResult, deadlineMs = 360000 } = {}) {
  const deadline = Date.now() + deadlineMs
  let interval = 500
  while (!signal.aborted && Date.now() < deadline) {
    const result = await fetchJudging(kind, id, { signal })
    if (signal.aborted) return false
    onResult(result)
    if (isJudgingTerminal(result)) return true
    await delay(Math.min(interval, Math.max(0, deadline - Date.now())), signal)
    interval = Math.min(interval * 1.5, 5000)
  }
  return false
}
