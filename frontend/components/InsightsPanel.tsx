import Link from "next/link";
import type { Insight } from "@/lib/api";

const styles: Record<Insight["type"], string> = {
  CHEAPEST_PER_UNIT: "border-sky-200 bg-sky-50",
  GENUINE_DEAL: "border-emerald-200 bg-emerald-50",
  FAKE_DISCOUNT: "border-amber-200 bg-amber-50",
};

const icons: Record<Insight["type"], string> = {
  CHEAPEST_PER_UNIT: "💰",
  GENUINE_DEAL: "🔥",
  FAKE_DISCOUNT: "⚠️",
};

export function InsightsPanel({ insights, limit = 8 }: { insights: Insight[]; limit?: number }) {
  if (insights.length === 0) {
    return <p className="text-sm text-slate-500">No insights yet. They appear after the first price collection.</p>;
  }
  return (
    <ul className="grid gap-2 sm:grid-cols-2">
      {insights.slice(0, limit).map((i, idx) => (
        <li key={idx} className={`rounded-lg border p-3 text-sm ${styles[i.type]}`}>
          <Link href={`/products/${i.productId}`} dir="auto" className="hover:underline">
            {icons[i.type]} {i.message}
          </Link>
        </li>
      ))}
    </ul>
  );
}
