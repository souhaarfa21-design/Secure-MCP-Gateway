import { useEffect, useRef, useState, type FormEvent } from "react";
import "./App.css";

const AGENT_URL = import.meta.env.VITE_AGENT_URL ?? "http://localhost:8081";
const CHAT_PATH = "/api/chat";
// The gateway hands out tokens (POST /token). The agent forwards them on every tool call.
const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL ?? "http://localhost:8080";
const AGENT_NAME = "support-agent";

// What the gateway's /token endpoint returns
type TokenResponse = { access_token: string; token_type: string; expires_in: number };
// Who is signed in. Kept in memory only: a page reload signs you out (safer than localStorage).
type Session = { username: string; token: string; expiresAt: number };

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
  const [session, setSession] = useState<Session | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const nextId = useRef(1);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [turns, busy]);

  const add = (role: Role, text: string) =>
      setTurns((t) => [...t, { id: nextId.current++, role, text }]);

  // Sign out automatically when the token expires (the gateway would refuse it anyway)
  useEffect(() => {
    if (!session) return;
    const t = setTimeout(() => {
      setSession(null);
      add("error", "Your session expired after 15 minutes. Sign in again.");
    }, session.expiresAt - Date.now());
    return () => clearTimeout(t);
  }, [session]);

  async function send(text: string) {
    const message = text.trim();
    if (!message || busy || !session) return;
    add("user", message);
    setInput("");
    setBusy(true);
    try {
      const res = await fetch(`${AGENT_URL}${CHAT_PATH}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          // the user's token: the agent passes it on to the gateway with every tool call
          Authorization: `Bearer ${session.token}`,
        },
        body: JSON.stringify({ message }),
      });
      if (res.status === 401) {
        setSession(null);
        throw new Error("The agent answered HTTP 401: please sign in again.");
      }
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
            <div className="bar-right">
              <span className={`pill ${link}`}><i />{linkLabel}</span>
              {session && (
                  <span className="who">
                    Signed in as <strong>{session.username}</strong>
                    <button type="button" onClick={() => setSession(null)}>Sign out</button>
                  </span>
              )}
            </div>
          </header>

          <div className="thread" role="log" aria-live="polite">
            {!session && <LoginCard onSignedIn={setSession} />}
            {session && turns.length === 0 && (
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
              placeholder={session ? "Ask a question" : "Sign in to ask a question"}
              disabled={!session}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  send(input);
                }
              }}
          />
            <button onClick={() => send(input)} disabled={busy || !session || !input.trim()}>Send question</button>
          </div>
        </main>
      </div>
  );
}
/** Login form: asks the GATEWAY for a token (POST /token). */
function LoginCard({ onSignedIn }: { onSignedIn: (s: Session) => void }) {
  const [username, setUsername] = useState("alice");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function signIn(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const res = await fetch(`${GATEWAY_URL}/token`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password, agent: AGENT_NAME }),
      });
      if (res.status === 401) throw new Error("Wrong username or password.");
      if (!res.ok) throw new Error(`The gateway answered HTTP ${res.status}.`);
      const data = (await res.json()) as TokenResponse;
      onSignedIn({ username, token: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 });
    } catch (err) {
      const m = err instanceof Error ? err.message : "";
      setError(m.startsWith("Wrong") || m.startsWith("The gateway") ? m : "The gateway is not reachable on port 8080.");
    } finally {
      setBusy(false);
    }
  }

  return (
      <form className="login" onSubmit={signIn}>
        <h2>Sign in</h2>
        <p>The gateway gives you a token. Every tool call the agent makes is then done as you.</p>
        <label>
          Username
          <select value={username} onChange={(e) => setUsername(e.target.value)}>
            <option value="alice">alice (support)</option>
            <option value="bob">bob (support)</option>
            <option value="carol">carol (support)</option>
            <option value="admin">admin</option>
          </select>
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete="current-password" required autoFocus />
        </label>
        {error && <p className="login-error" role="alert">{error}</p>}
        <button type="submit" disabled={busy || !password}>{busy ? "Signing in…" : "Sign in"}</button>
      </form>
  );
}
