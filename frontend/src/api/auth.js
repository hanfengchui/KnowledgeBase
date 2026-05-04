import { clearAccessToken, client, getAccessToken, hasAccessToken, setAccessToken } from './client'

export { clearAccessToken, getAccessToken, hasAccessToken, setAccessToken }

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
