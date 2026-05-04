import { client } from './client'

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
