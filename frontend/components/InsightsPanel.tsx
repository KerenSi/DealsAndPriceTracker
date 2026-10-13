import Link from "next/link";
import type { Insight } from "@/lib/api";

const styles: Record<Insight["type"], string> = {
  CHEAPEST_PER_UNIT: "border-sky-200 bg-sky-50",
  GENUINE_DEAL: "border-emerald-200 bg-emerald-50",
};

const icons: Record<Insight["type"], string> = {
  CHEAPEST_PER_UNIT: "💰",
  GENUINE_DEAL: "🔥",
};

// The backend already picks and orders the insights (real deals first).
export function InsightsPanel({ insights, loggedIn }: { insights: Insight[]; loggedIn: boolean }) {
  const hint = loggedIn
    ? "Star products you buy to see the cheapest option in their size or stage."
    : "Log in and star products you buy to see the cheapest option in their size or stage.";
  if (insights.length === 0) {
    return <p className="text-sm text-slate-500">No real deals right now. {hint}</p>;
  }
  return (
    <ul className="grid gap-2 sm:grid-cols-2">
      {insights.map((i, idx) => (
        <li key={idx} className={`rounded-lg border p-3 text-sm ${styles[i.type]}`}>
          <Link href={`/products/${i.productId}`} dir="auto" className="hover:underline">
            {icons[i.type]} {i.message}
          </Link>
        </li>
      ))}
    </ul>
  );
}
