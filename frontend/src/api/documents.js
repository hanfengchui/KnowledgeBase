import { client } from './client'

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
