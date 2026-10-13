"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

// Star toggle for marking a product as interesting. Logged-out users get a link to log in.
export function WatchButton({
  productId,
  watched,
  loggedIn,
  withLabel = false,
}: {
  productId: string;
  watched: boolean;
  loggedIn: boolean;
  withLabel?: boolean;
}) {
  const router = useRouter();
  const [on, setOn] = useState(watched);
  const [busy, setBusy] = useState(false);
  const label = on ? "Watching" : "Watch";

  if (!loggedIn) {
    return (
      <Link href="/login" title="Log in to watch products" className="text-slate-300 hover:text-amber-400">
        ☆{withLabel && <span className="ml-1 text-sm text-slate-600">Watch</span>}
      </Link>
    );
  }

  async function toggle() {
    setBusy(true);
    const next = !on;
    const res = await fetch(`/api/watchlist/${productId}`, { method: next ? "PUT" : "DELETE" });
    setBusy(false);
    if (res.status === 401) {
      router.push("/login");
      return;
    }
    if (res.ok) {
      setOn(next);
      router.refresh();
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={busy}
      aria-pressed={on}
      title={on ? "Stop watching" : "Watch this product"}
      className={`${on ? "text-amber-500" : "text-slate-300"} hover:text-amber-400 disabled:opacity-50`}
    >
      {on ? "★" : "☆"}
      {withLabel && <span className="ml-1 text-sm text-slate-600">{label}</span>}
    </button>
  );
}
