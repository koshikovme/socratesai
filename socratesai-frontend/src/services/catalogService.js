import api from './api'

// Native catalog APIs intentionally accept only bounded pagination parameters.
function pageParams(page = 0, size = 20) {
  return {
    page: Number.isInteger(page) ? Math.min(10000, Math.max(0, page)) : 0,
    size: Number.isInteger(size) ? Math.min(50, Math.max(1, size)) : 20
  }
}

async function read(path, options = {}) {
  const { data } = await api.get(path, options)
  return data
}

export function fetchProblems({ page = 0, size = 20, signal } = {}) {
  return read('/api/problems', { params: pageParams(page, size), signal })
}

export function fetchCollections({ page = 0, size = 20, signal } = {}) {
  return read('/api/collections', { params: pageParams(page, size), signal })
}

export function fetchProblem(id, versionId = null, { signal } = {}) {
  const path = `/api/problems/${encodeURIComponent(id)}`
  return read(versionId == null ? path : `${path}/versions/${encodeURIComponent(versionId)}`, { signal })
}

export function fetchCollection(id, { signal } = {}) {
  return read(`/api/collections/${encodeURIComponent(id)}`, { signal })
}

export function fetchCollectionProgress(id, { signal } = {}) {
  return read(`/api/collections/${encodeURIComponent(id)}/progress`, { signal })
}

export function fetchLanguages({ signal } = {}) {
  return read('/api/languages', { signal })
}

export function fetchJudgedProgress({ signal } = {}) {
  return read('/api/users/me/progress', { signal })
}

export function solvedProblemIds(progress) {
  if (progress?.progressSource !== 'JUDGED_SUBMISSION') return new Set()
  return new Set((progress.tasks ?? []).filter(item => item.status === 'SOLVED').map(item => String(item.taskId)))
}
