import axios from 'axios'

const TOKEN_KEY = 'knowledgehub-access-token'

const client = axios.create({
  baseURL: '/api',
  timeout: 120000
})

client.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export function getAccessToken() {
  return window.localStorage.getItem(TOKEN_KEY) || ''
}

export function setAccessToken(token) {
  if (!token) {
    clearAccessToken()
    return
  }
  window.localStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken() {
  window.localStorage.removeItem(TOKEN_KEY)
}

export async function login(payload) {
  const response = await client.post('/auth/login', payload)
  return response.data
}

export async function switchTenant(tenantId) {
  const response = await client.post('/auth/switch-tenant', { tenantId })
  return response.data
}

export async function getMe() {
  const response = await client.get('/auth/me')
  return response.data
}

export async function logout() {
  const response = await client.post('/auth/logout')
  return response.data
}

export async function listKnowledgeBases() {
  const response = await client.get('/knowledge-bases')
  return response.data
}

export async function createKnowledgeBase(payload) {
  const response = await client.post('/knowledge-bases', payload)
  return response.data
}

export async function updateKnowledgeBase(knowledgeBaseId, payload) {
  const response = await client.patch(`/knowledge-bases/${knowledgeBaseId}`, payload)
  return response.data
}

export async function askQuestion(payload) {
  const response = await client.post('/chat/ask', payload)
  return response.data
}

export async function uploadDocument(file, knowledgeBaseId) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('knowledgeBaseId', knowledgeBaseId)
  const response = await client.post('/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}

export async function listDocuments(knowledgeBaseId) {
  const response = await client.get('/documents', {
    params: { knowledgeBaseId }
  })
  return response.data
}

export async function reindexDocuments(knowledgeBaseId) {
  const response = await client.post('/documents/reindex', null, {
    params: { knowledgeBaseId }
  })
  return response.data
}

export async function listTenants() {
  const response = await client.get('/admin/tenants')
  return response.data
}

export async function createTenant(payload) {
  const response = await client.post('/admin/tenants', payload)
  return response.data
}

export async function updateTenant(tenantId, payload) {
  const response = await client.patch(`/admin/tenants/${tenantId}`, payload)
  return response.data
}

export async function listUsers(tenantId) {
  const response = await client.get('/admin/users', {
    params: { tenantId }
  })
  return response.data
}

export async function createUser(payload) {
  const response = await client.post('/admin/users', payload)
  return response.data
}

export async function updateUser(userId, payload) {
  const response = await client.patch(`/admin/users/${userId}`, payload)
  return response.data
}

export async function listRoles() {
  const response = await client.get('/admin/roles')
  return response.data
}

export async function assignUserRoles(userId, payload) {
  const response = await client.post(`/admin/users/${userId}/roles`, payload)
  return response.data
}

export async function listKnowledgeBaseMembers(knowledgeBaseId) {
  const response = await client.get(`/admin/knowledge-bases/${knowledgeBaseId}/members`)
  return response.data
}

export async function saveKnowledgeBaseMember(knowledgeBaseId, payload) {
  const response = await client.post(`/admin/knowledge-bases/${knowledgeBaseId}/members`, payload)
  return response.data
}

export async function deleteKnowledgeBaseMember(knowledgeBaseId, userId) {
  const response = await client.delete(`/admin/knowledge-bases/${knowledgeBaseId}/members`, {
    params: { userId }
  })
  return response.data
}

export async function listAuditLogs(params) {
  const response = await client.get('/admin/audit-logs', { params })
  return response.data
}
