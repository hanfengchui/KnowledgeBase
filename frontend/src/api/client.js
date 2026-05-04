import axios from 'axios'

const TOKEN_KEY = 'knowledgehub-access-token'

export const client = axios.create({
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

export function hasAccessToken() {
  return Boolean(getAccessToken())
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
