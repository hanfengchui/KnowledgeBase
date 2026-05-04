import { client } from './client'

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
