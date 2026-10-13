"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

// Whether "cheapest" insights may suggest other brands in the same size or stage as a watched product.
export function SimilarProductsToggle({ initial }: { initial: boolean }) {
  const router = useRouter();
  const [on, setOn] = useState(initial);
  const [busy, setBusy] = useState(false);

  async function change(next: boolean) {
    setBusy(true);
    const res = await fetch("/api/settings", {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ showSimilarProducts: next }),
    });
    setBusy(false);
    if (res.ok) {
      setOn(next);
      router.refresh();
    }
  }

  return (
    <label className="flex items-center gap-2 text-sm text-slate-600">
      <input type="checkbox" checked={on} disabled={busy} onChange={(e) => change(e.target.checked)} />
      Show similar products
    </label>
  );
}
