"use client";

import { useState } from "react";

interface Message {
  role: "user" | "assistant";
  content: string;
}

const SUGGESTIONS = [
  "What is the cheapest size 4 diaper right now?",
  "Are there any real deals on formula this week?",
  "איפה הכי זול לקנות מטרנה שלב 1?",
];

export default function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);

  async function send(text: string) {
    const content = text.trim();
    if (!content || loading) return;
    const next: Message[] = [...messages, { role: "user", content }];
    setMessages(next);
    setInput("");
    setLoading(true);
    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ messages: next.slice(-20) }),
      });
      const data = res.ok ? await res.json() : { reply: "Something went wrong, please try again." };
      setMessages([...next, { role: "assistant", content: data.reply }]);
    } catch {
      setMessages([...next, { role: "assistant", content: "Could not reach the server." }]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4">
      <h1 className="text-xl font-semibold">Ask about prices</h1>

      <div className="min-h-[300px] space-y-3 rounded-lg border bg-white p-4">
        {messages.length === 0 && (
          <div className="space-y-2">
            <p className="text-sm text-slate-500">Try one of these:</p>
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                onClick={() => send(s)}
                dir="auto"
                className="block rounded-md border px-3 py-1 text-left text-sm hover:bg-slate-50"
              >
                {s}
              </button>
            ))}
          </div>
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            dir="auto"
            className={`whitespace-pre-wrap rounded-lg px-3 py-2 text-sm ${
              m.role === "user" ? "ml-auto max-w-[80%] bg-slate-900 text-white" : "max-w-[90%] bg-slate-100"
            }`}
          >
            {m.content}
          </div>
        ))}
        {loading && <p className="text-sm text-slate-400">Thinking…</p>}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
        className="flex gap-2"
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          dir="auto"
          placeholder="Ask a question…"
          className="flex-1 rounded-md border px-3 py-2 text-sm"
        />
        <button
          type="submit"
          disabled={loading}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm text-white disabled:opacity-50"
        >
          Send
        </button>
      </form>
    </div>
  );
}
