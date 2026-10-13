import type { HistoryPoint } from "@/lib/api";

const COLORS = ["#2563eb", "#16a34a", "#dc2626", "#9333ea", "#ea580c", "#0891b2"];

/** Simple dependency-free SVG line chart of effective price per store over time. */
export function PriceHistoryChart({ points }: { points: HistoryPoint[] }) {
  if (points.length < 2) {
    return <p className="text-sm text-slate-500">Not enough history yet.</p>;
  }
  const width = 720;
  const height = 240;
  const pad = { top: 10, right: 10, bottom: 24, left: 48 };

  const times = points.map((p) => new Date(p.scrapedAt).getTime());
  const prices = points.map((p) => p.effectivePrice);
  const [tMin, tMax] = [Math.min(...times), Math.max(...times)];
  const [pMin, pMax] = [Math.min(...prices) * 0.95, Math.max(...prices) * 1.05];
  const x = (t: number) => pad.left + ((t - tMin) / Math.max(tMax - tMin, 1)) * (width - pad.left - pad.right);
  const y = (p: number) => pad.top + (1 - (p - pMin) / (pMax - pMin)) * (height - pad.top - pad.bottom);

  const byStore = new Map<string, HistoryPoint[]>();
  for (const p of points) {
    byStore.set(p.storeName, [...(byStore.get(p.storeName) ?? []), p]);
  }
  const stores = [...byStore.keys()].sort();
  const ticks = [pMin, (pMin + pMax) / 2, pMax];

  return (
    <div>
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full" role="img" aria-label="Price history">
        {ticks.map((t) => (
          <g key={t}>
            <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} stroke="#e2e8f0" />
            <text x={pad.left - 6} y={y(t) + 4} textAnchor="end" fontSize="11" fill="#64748b">
              ₪{t.toFixed(0)}
            </text>
          </g>
        ))}
        <text x={pad.left} y={height - 6} fontSize="11" fill="#64748b">
          {new Date(tMin).toLocaleDateString("en-GB")}
        </text>
        <text x={width - pad.right} y={height - 6} fontSize="11" fill="#64748b" textAnchor="end">
          {new Date(tMax).toLocaleDateString("en-GB")}
        </text>
        {stores.map((store, i) => (
          <polyline
            key={store}
            fill="none"
            stroke={COLORS[i % COLORS.length]}
            strokeWidth={2}
            points={byStore
              .get(store)!
              .map((p) => `${x(new Date(p.scrapedAt).getTime())},${y(p.effectivePrice)}`)
              .join(" ")}
          />
        ))}
      </svg>
      <ul className="mt-2 flex flex-wrap gap-4 text-xs">
        {stores.map((store, i) => (
          <li key={store} className="flex items-center gap-1">
            <span className="inline-block h-2 w-4 rounded" style={{ background: COLORS[i % COLORS.length] }} />
            {store}
          </li>
        ))}
      </ul>
    </div>
  );
}
