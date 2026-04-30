import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 120000
})

export async function askQuestion(payload) {
  const response = await client.post('/chat/ask', payload)
  return response.data
}

export async function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await client.post('/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}

export async function listDocuments() {
  const response = await client.get('/documents')
  return response.data
}

export async function reindexDocuments() {
  const response = await client.post('/documents/reindex')
  return response.data
}
