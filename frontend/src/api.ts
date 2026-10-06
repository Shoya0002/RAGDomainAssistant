export type UploadResponse = {
  fileName: string
  chunksIndexed: number
}

export type ChatResponse = {
  answer: string
  sources: string[]
}

async function readError(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (typeof body === 'object' && body !== null && 'error' in body && typeof body.error === 'string') {
      return body.error
    }
  } catch {
    // Use a status-based message when the server response is not JSON.
  }
  return `Request failed (${response.status} ${response.statusText || 'server error'}).`
}

export async function uploadPdf(file: File): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch('/api/documents', { method: 'POST', body: formData })
  if (!response.ok) throw new Error(await readError(response))
  return response.json() as Promise<UploadResponse>
}

export async function askQuestion(question: string): Promise<ChatResponse> {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  })
  if (!response.ok) throw new Error(await readError(response))
  return response.json() as Promise<ChatResponse>
}

export async function checkApiHealth(): Promise<void> {
  const response = await fetch('/actuator/health')
  if (!response.ok) throw new Error('Backend is unavailable')
}
