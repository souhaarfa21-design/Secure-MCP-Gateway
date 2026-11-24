import { useEffect, useRef, useState } from "react";
import "./App.css";

const AGENT_URL = import.meta.env.VITE_AGENT_URL ?? "http://localhost:8081";
const CHAT_PATH = "/api/chat";

type Role = "user" | "agent" | "error";
type Turn = { id: number; role: Role; text: string };
type Link = "unknown" | "up" | "down";

const SUGGESTIONS = [
  "How many customers are on the PRO plan?",
  "Which tables can you read?",
  "Summarise the latest open ticket",
];

// The agent may name its answer field differently; accept the common ones.
function pickAnswer(data: unknown): string {
  if (data && typeof data === "object") {
    const d = data as Record<string, unknown>;
    for (const k of ["answer", "reply", "response", "message"]) {
      if (typeof d[k] === "string") return d[k] as string;
    }
  }
  return typeof data === "string" ? data : JSON.stringify(data);
}

export default function App() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [link, setLink] = useState<Link>("unknown");
  const endRef = useRef<HTMLDivElement>(null);
  const nextId = useRef(1);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [turns, busy]);

  const add = (role: Role, text: string) =>
      setTurns((t) => [...t, { id: nextId.current++, role, text }]);

  async function send(text: string) {
    const message = text.trim();
    if (!message || busy) return;
    add("user", message);
    setInput("");
    setBusy(true);
    try {
      const res = await fetch(`${AGENT_URL}${CHAT_PATH}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message }),
      });
      if (!res.ok) throw new Error(`The agent answered HTTP ${res.status}. Check the agent terminal for the error.`);
      add("agent", pickAnswer(await res.json()));
      setLink("up");
    } catch (e) {
      setLink("down");
      const m = e instanceof Error ? e.message : "";
      add("error", m.startsWith("The agent") ? m : "The agent is not reachable. Start it on port 8081 and allow this origin in CORS.");
    } finally {
      setBusy(false);
    }
  }

  const linkLabel = { unknown: "Not contacted yet", up: "Agent reachable", down: "Agent not reachable" }[link];

  return (
      <div className="shell">
        <aside className="rail">
          <div className="brand">
            <span className="mark" aria-hidden="true" />
            <div>
              <strong>Gateway</strong>
              <small>Secure MCP console</small>
            </div>
          </div>
          <nav className="nav" aria-label="Sections">
            <a className="active" aria-current="page">Conversation</a>
            <a className="soon" aria-disabled="true">Approvals <em>soon</em></a>
            <a className="soon" aria-disabled="true">Audit dashboard <em>soon</em></a>
          </nav>
          <section className="path">
            <h2>How an answer is made</h2>
            <ol>
              <li>Your question goes to the agent.</li>
              <li>The agent asks the gateway for a tool.</li>
              <li>The gateway runs it and logs the call.</li>
            </ol>
          </section>
        </aside>

        <main className="main">
          <header className="bar">
            <h1>Support assistant</h1>
            <span className={`pill ${link}`}><i />{linkLabel}</span>
          </header>

          <div className="thread" role="log" aria-live="polite">
            {turns.length === 0 && (
                <div className="empty">
                  <p>Ask about customers and tickets. The agent can only read data through the gateway&apos;s tools.</p>
                  <div className="chips">
                    {SUGGESTIONS.map((s) => (
                        <button key={s} onClick={() => send(s)}>{s}</button>
                    ))}
                  </div>
                </div>
            )}
            {turns.map((t) => (
                <div key={t.id} className={`turn ${t.role}`}>
                  {t.role === "agent" && <span className="avatar" aria-hidden="true">A</span>}
                  {/* Rendered as plain text on purpose: model output must never be injected as HTML. */}
                  <p>{t.text}</p>
                </div>
            ))}
            {busy && (
                <div className="turn agent" aria-label="The agent is working">
                  <span className="avatar" aria-hidden="true">A</span>
                  <p className="dots"><i /><i /><i /></p>
                </div>
            )}
            <div ref={endRef} />
          </div>

          <div className="composer">
          <textarea
              rows={1}
              value={input}
              placeholder="Ask a question"
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  send(input);
                }
              }}
          />
            <button onClick={() => send(input)} disabled={busy || !input.trim()}>Send question</button>
          </div>
        </main>
      </div>
  );
}