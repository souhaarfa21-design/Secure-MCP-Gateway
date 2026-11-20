import { useState, type FormEvent } from 'react'
import './App.css'

// --- Typed request and response: the same shape as ChatController.java in the agent app ---
type ChatRequest = { message: string }
type ChatResponse = { answer: string }

// One bubble in the conversation
type Message = { role: 'user' | 'agent' | 'error'; text: string }

const AGENT_URL = 'http://localhost:8081/api/chat'

async function askAgent(message: string): Promise<ChatResponse> {
  const body: ChatRequest = { message }
  const res = await fetch(AGENT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    throw new Error(`The agent answered HTTP ${res.status}`)
  }
  return (await res.json()) as ChatResponse
}

function App() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)

  async function send(e: FormEvent) {
    e.preventDefault()
    const question = input.trim()
    if (!question || loading) return

    setMessages((m) => [...m, { role: 'user', text: question }])
    setInput('')
    setLoading(true)
    try {
      const { answer } = await askAgent(question)
      setMessages((m) => [...m, { role: 'agent', text: answer }])
    } catch (err) {
      // "Failed to fetch" usually means: agent not running, or CORS blocked the call
      const text = err instanceof Error ? err.message : String(err)
      setMessages((m) => [...m, { role: 'error', text }])
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="chat">
      <header>
        <h1>Support assistant</h1>
        <p>Ask about customers and tickets. The agent reads the database through the MCP gateway.</p>
      </header>

      <section className="messages" aria-live="polite">
        {messages.length === 0 && (
          <p className="hint">Try: “How many customers are on the Pro plan?”</p>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`bubble ${m.role}`}>
            {m.text}
          </div>
        ))}
        {loading && <div className="bubble agent thinking">Thinking…</div>}
      </section>

      <form onSubmit={send}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type your question…"
          aria-label="Your question"
          autoFocus
        />
        <button type="submit" disabled={loading || !input.trim()}>
          Send
        </button>
      </form>
    </main>
  )
}

export default App
