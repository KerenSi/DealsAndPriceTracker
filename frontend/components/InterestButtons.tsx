"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Interest } from "@/lib/api";

// Star (watch) and "not interested" toggles for one product. Logged-out users get a link to log in.
export function InterestButtons({
  productId,
  interest,
  loggedIn,
  withLabels = false,
}: {
  productId: string;
  interest: Interest | null;
  loggedIn: boolean;
  withLabels?: boolean;
}) {
  const router = useRouter();
  const [current, setCurrent] = useState(interest);
  const [busy, setBusy] = useState(false);

  if (!loggedIn) {
    return (
      <Link href="/login" title="Log in to watch products" className="text-slate-300 hover:text-amber-400">
        ☆{withLabels && <span className="ml-1 text-sm text-slate-600">Watch</span>}
      </Link>
    );
  }

  async function toggle(target: Interest) {
    const next = current === target ? null : target;
    setBusy(true);
    const res = await fetch(`/api/interest/${productId}`, {
      method: next ? "PUT" : "DELETE",
      headers: next ? { "content-type": "application/json" } : undefined,
      body: next ? JSON.stringify({ interest: next }) : undefined,
    });
    setBusy(false);
    if (res.status === 401) {
      router.push("/login");
      return;
    }
    if (res.ok) {
      setCurrent(next);
      router.refresh();
    }
  }

  const watched = current === "WATCHED";
  const hidden = current === "NOT_INTERESTED";
  return (
    <span className="inline-flex items-center gap-2 whitespace-nowrap">
      <button
        type="button"
        onClick={() => toggle("WATCHED")}
        disabled={busy}
        aria-pressed={watched}
        title={watched ? "Stop watching" : "Watch this product"}
        className={`${watched ? "text-amber-500" : "text-slate-300"} hover:text-amber-400 disabled:opacity-50`}
      >
        {watched ? "★" : "☆"}
        {withLabels && <span className="ml-1 text-sm text-slate-600">{watched ? "Watching" : "Watch"}</span>}
      </button>
      <button
        type="button"
        onClick={() => toggle("NOT_INTERESTED")}
        disabled={busy}
        aria-pressed={hidden}
        title={hidden ? "Show this product's deals again" : "Not interested: hide its deals"}
        className={`${hidden ? "text-slate-700" : "text-slate-300"} hover:text-slate-500 disabled:opacity-50`}
      >
        ⊘{withLabels && <span className="ml-1 text-sm text-slate-600">{hidden ? "Not interested" : "Hide"}</span>}
      </button>
    </span>
  );
}
