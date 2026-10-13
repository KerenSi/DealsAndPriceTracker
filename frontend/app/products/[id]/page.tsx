import Link from "next/link";
import { notFound } from "next/navigation";
import { PriceHistoryChart } from "@/components/PriceHistoryChart";
import { InterestButtons } from "@/components/InterestButtons";
import { categoryLabel, formatPrice, getHistory, getProduct, type StorePrice } from "@/lib/api";
import { getSessionToken } from "@/lib/session";

export const dynamic = "force-dynamic";

function DealBadge({ p }: { p: StorePrice }) {
  if (p.deal.genuineDeal) {
    return <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs text-emerald-800">Real deal</span>;
  }
  if (p.deal.suspectedFakeDiscount) {
    return <span className="rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-800">Suspicious sale</span>;
  }
  return null;
}

export default async function ProductPage({ params }: { params: { id: string } }) {
  const token = getSessionToken();
  const detail = await getProduct(params.id, token).catch(() => null);
  if (!detail) notFound();
  const history = await getHistory(params.id);
  const { product, prices } = detail;

  return (
    <div className="space-y-6">
      <div>
        <Link href="/" className="text-sm text-slate-500 hover:underline">
          ← All products
        </Link>
        <div className="mt-2 flex items-center gap-3">
          <h1 className="text-2xl font-semibold" dir="auto">
            {product.title}
          </h1>
          <InterestButtons productId={product.id} interest={product.interest} loggedIn={token != null} withLabels />
        </div>
        <p className="text-sm text-slate-600">
          {product.brand} · {categoryLabel[product.category]}
          {product.sizeLabel && ` · ${product.sizeLabel}`}
          {product.unitQuantity && ` · ${product.unitQuantity} ${product.unitType === "UNITS" ? "units" : "g"}`}
        </p>
      </div>

      <section>
        <h2 className="mb-2 text-lg font-semibold">Prices by store</h2>
        <div className="overflow-x-auto rounded-lg border bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-100 text-left text-slate-600">
              <tr>
                <th className="px-3 py-2">Store</th>
                <th className="px-3 py-2">Shelf price</th>
                <th className="px-3 py-2">You pay</th>
                <th className="px-3 py-2">Per unit</th>
                <th className="px-3 py-2">30-day avg</th>
                <th className="px-3 py-2">Promotion</th>
                <th className="px-3 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {prices.map((p) => (
                <tr key={p.listingId} className="border-t align-top">
                  <td className="px-3 py-2 font-medium">{p.store.branchName}</td>
                  <td className="px-3 py-2">
                    {formatPrice(p.price)}
                    {p.referencePrice != null && (
                      <span className="ml-1 text-xs text-slate-400 line-through">{formatPrice(p.referencePrice)}</span>
                    )}
                  </td>
                  <td className="px-3 py-2 font-semibold">{formatPrice(p.effectivePrice)}</td>
                  <td className="px-3 py-2">
                    {p.unitPrice != null ? `${formatPrice(p.unitPrice)} / ${p.unitLabel}` : "—"}
                  </td>
                  <td className="px-3 py-2">{formatPrice(p.deal.baselinePrice)}</td>
                  <td className="px-3 py-2" dir="auto">
                    {p.promotionDescription ?? "—"}
                  </td>
                  <td className="px-3 py-2">
                    <DealBadge p={p} />
                    {(p.deal.genuineDeal || p.deal.suspectedFakeDiscount) && (
                      <ul className="mt-1 list-disc pl-4 text-xs text-slate-500">
                        {p.deal.reasons.map((r) => (
                          <li key={r}>{r}</li>
                        ))}
                      </ul>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="mb-2 text-lg font-semibold">Price history (60 days)</h2>
        <div className="rounded-lg border bg-white p-4">
          <PriceHistoryChart points={history} />
        </div>
      </section>
    </div>
  );
}
