import { client } from './client'

export async function askQuestion(payload) {
  const response = await client.post('/chat/ask', payload)
  return response.data
}
