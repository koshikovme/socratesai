import api from './api'

const problemPath = (id) => `/api/teacher/problems/${id}`
const collectionPath = (id) => `/api/teacher/collections/${id}`
const data = (request) => request.then((response) => response.data)

export const teacherService = {
  problems: (page = 0, config = {}) => data(api.get('/api/teacher/problems', { ...config, params: { page, size: 20 } })),
  problem: (id, config) => data(api.get(problemPath(id), config)),
  createProblem: (draft, config) => data(api.post('/api/teacher/problems', draft, config)),
  saveProblem: (id, draft, config) => data(api.put(`${problemPath(id)}/draft`, draft, config)),
  cloneDraft: (id, lockVersion, config) => data(api.post(`${problemPath(id)}/draft`, { lockVersion }, config)),
  verify: (id, lockVersion, solutions, config) => data(api.post(`${problemPath(id)}/verify`, { lockVersion, solutions }, config)),
  verification: (id, verificationId, config) => data(api.get(`${problemPath(id)}/verifications/${verificationId}`, config)),
  publishProblem: (id, lockVersion, verificationId, config) => data(api.post(`${problemPath(id)}/publish`, { lockVersion, verificationId }, config)),
  archiveProblem: (id, lockVersion, config) => data(api.post(`${problemPath(id)}/archive`, { lockVersion }, config)),
  deleteProblem: (id, lockVersion, draftOnly, config = {}) => data(api.delete(`${problemPath(id)}${draftOnly ? '/draft' : ''}`, { ...config, params: { lockVersion } })),
  languages: (config) => data(api.get('/api/languages', config)),
  collections: (page = 0, config = {}) => data(api.get('/api/teacher/collections', { ...config, params: { page, size: 20 } })),
  collection: (id, config) => data(api.get(collectionPath(id), config)),
  createCollection: (draft, config) => data(api.post('/api/teacher/collections', draft, config)),
  saveCollection: (id, draft, config) => data(api.put(collectionPath(id), draft, config)),
  publishCollection: (id, lockVersion, config) => data(api.post(`${collectionPath(id)}/publish`, { lockVersion }, config)),
  archiveCollection: (id, lockVersion, config) => data(api.post(`${collectionPath(id)}/archive`, { lockVersion }, config)),
  deleteCollection: (id, lockVersion, config = {}) => data(api.delete(collectionPath(id), { ...config, params: { lockVersion } })),
  publishedProblems: (page = 0, config = {}) => data(api.get('/api/problems', { ...config, params: { page, size: 20 } })),
  changeRole: (id, request, config) => data(api.patch(`/api/admin/users/${id}/role`, request, config)),
}
