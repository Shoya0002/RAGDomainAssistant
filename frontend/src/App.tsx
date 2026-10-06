import { useState, type FormEvent } from 'react'
import './App.css'

type ChatResponse = {
  answer: string
  sources: string[]
  intent: string | null
}

type ChatMessage = {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources?: string[]
  intent?: string | null
}

type Operation = 'pdf' | 'intents' | 'chat' | null

async function readResponse<T>(response: Response): Promise<T> {
  const body = await response.text()
  let payload: unknown = null
  if (body.trim()) {
    try {
      payload = JSON.parse(body) as unknown
    } catch {
      if (response.ok) {
        throw new Error('The server returned an invalid response. Please try again.')
      }
    }
  }

  if (!response.ok) {
    const error = typeof payload === 'object' && payload !== null
      && 'error' in payload && typeof payload.error === 'string'
      ? payload.error
      : body.trim().slice(0, 240) || `Request failed (${response.status})`
    throw new Error(error)
  }
  if (payload === null) {
    throw new Error('The server returned an empty response. Please try again.')
  }
  return payload as T
}

function App() {
  const [pdf, setPdf] = useState<File | null>(null)
  const [intents, setIntents] = useState<File | null>(null)
  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [operation, setOperation] = useState<Operation>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [nextMessageId, setNextMessageId] = useState(0)

  async function uploadPdf() {
    if (!pdf) return
    setOperation('pdf')
    setError('')
    setNotice('')
    try {
      const body = new FormData()
      body.append('file', pdf)
      const response = await fetch('/api/documents', { method: 'POST', body })
      const result = await readResponse<{ fileName: string; chunksIndexed: number }>(response)
      setNotice(`${result.fileName} added to the knowledge base (${result.chunksIndexed} chunks).`)
      setPdf(null)
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : 'PDF upload failed.')
    } finally {
      setOperation(null)
    }
  }

  async function uploadIntents() {
    if (!intents) return
    setOperation('intents')
    setError('')
    setNotice('')
    try {
      const content = await intents.text()
      const payload: unknown = JSON.parse(content)
      const response = await fetch('/api/intents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      const result = await readResponse<{ intentsIndexed: number; questionsIndexed: number }>(response)
      setNotice(`Intent taxonomy loaded: ${result.intentsIndexed} intents and ${result.questionsIndexed} question vectors.`)
      setIntents(null)
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : 'Intent JSON upload failed.')
    } finally {
      setOperation(null)
    }
  }

  async function askQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const submittedQuestion = question.trim()
    if (!submittedQuestion || operation) return

    setQuestion('')
    setError('')
    setNotice('')
    setOperation('chat')
    setMessages((previous) => [...previous, {
      id: nextMessageId,
      role: 'user',
      content: submittedQuestion,
    }])
    setNextMessageId((id) => id + 1)
    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: submittedQuestion }),
      })
      const result = await readResponse<ChatResponse>(response)
      setMessages((previous) => [...previous, {
        id: nextMessageId + 1,
        role: 'assistant',
        content: result.answer,
        sources: result.sources,
        intent: result.intent,
      }])
      setNextMessageId((id) => id + 1)
    } catch (askError) {
      setError(askError instanceof Error ? askError.message : 'Could not get an answer.')
    } finally {
      setOperation(null)
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="VIT Knowledge Desk home">
          <span className="brand-mark">V</span>
          <span>VIT <strong>Knowledge Desk</strong></span>
        </a>
        <span className="service-indicator"><span /> RAG assistant</span>
      </header>

      <section className="intro">
        <p className="eyebrow">YOUR CAMPUS KNOWLEDGE, CONNECTED</p>
        <h1>How can we help?</h1>
        <p>Ask a question about VIT. Answers are grounded in your uploaded documents.</p>
      </section>

      <section className="workspace" aria-label="Knowledge desk">
        <aside className="knowledge-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">SET UP YOUR KNOWLEDGE BASE</p>
              <h2>Sources</h2>
            </div>
            <span className="source-count">{messages.length > 0 ? 'LIVE' : 'READY'}</span>
          </div>
          <p className="panel-description">
            Add PDFs for factual answers and your intent JSON to recognize question categories.
          </p>

          <div className="upload-card">
            <div className="upload-icon pdf-icon" aria-hidden="true">PDF</div>
            <div className="upload-copy">
              <h3>Academic documents</h3>
              <p>{pdf?.name ?? 'Upload a searchable PDF'}</p>
            </div>
            <label className="file-button">
              Browse
              <input
                type="file"
                accept=".pdf,application/pdf"
                onChange={(event) => {
                  setPdf(event.target.files?.[0] ?? null)
                  event.currentTarget.value = ''
                }}
                disabled={operation !== null}
              />
            </label>
            <button className="primary-button" type="button" onClick={uploadPdf} disabled={!pdf || operation !== null}>
              {operation === 'pdf' ? 'Adding…' : 'Add PDF'}
            </button>
          </div>

          <div className="upload-card">
            <div className="upload-icon json-icon" aria-hidden="true">{'{ }'}</div>
            <div className="upload-copy">
              <h3>Intent questions</h3>
              <p>{intents?.name ?? 'Upload your intents.json file'}</p>
            </div>
            <label className="file-button">
              Browse
              <input
                type="file"
                accept=".json,application/json"
                onChange={(event) => {
                  setIntents(event.target.files?.[0] ?? null)
                  event.currentTarget.value = ''
                }}
                disabled={operation !== null}
              />
            </label>
            <button className="secondary-button" type="button" onClick={uploadIntents} disabled={!intents || operation !== null}>
              {operation === 'intents' ? 'Indexing…' : 'Index JSON'}
            </button>
          </div>

          <div className="hint">
            <span aria-hidden="true">i</span>
            <p>Intent questions classify what someone is asking; PDFs provide the facts used in answers.</p>
          </div>
          {notice && <p className="notice" role="status">{notice}</p>}
          {error && <p className="error-message" role="alert">{error}</p>}
        </aside>

        <section className="chat-panel" aria-label="Ask the VIT assistant">
          <div className="chat-heading">
            <div className="assistant-avatar" aria-hidden="true">V</div>
            <div>
              <h2>VIT Assistant</h2>
              <p>Answers from your knowledge base</p>
            </div>
            <span className="online-dot" title="Ready" />
          </div>

          <div className="conversation" aria-live="polite">
            {messages.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon" aria-hidden="true">✦</div>
                <h3>Start a conversation</h3>
                <p>Try asking about academic dates, exams, courses, fees, or campus services.</p>
              </div>
            ) : messages.map((message) => (
              <article className={`message ${message.role}`} key={message.id}>
                <p>{message.content}</p>
                {message.role === 'assistant' && (message.intent || (message.sources && message.sources.length > 0)) && (
                  <div className="message-meta">
                    {message.intent && <span className="intent-tag">{message.intent}</span>}
                    {message.sources?.length ? <span>Sources: {message.sources.join(', ')}</span> : null}
                  </div>
                )}
              </article>
            ))}
            {operation === 'chat' && <p className="typing-indicator">Searching your knowledge base…</p>}
          </div>

          <form className="question-form" onSubmit={askQuestion}>
            <label className="sr-only" htmlFor="question">Ask a question</label>
            <input
              id="question"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Ask anything about VIT…"
              disabled={operation !== null}
            />
            <button type="submit" disabled={!question.trim() || operation !== null} aria-label="Send question">
              <span aria-hidden="true">↑</span>
            </button>
          </form>
          <p className="privacy-note">The assistant uses indexed PDFs for facts and your intent taxonomy for classification.</p>
        </section>
      </section>
      <footer>VIT Knowledge Desk <span>·</span> Grounded answers, made simple.</footer>
    </main>
  )
}

export default App
