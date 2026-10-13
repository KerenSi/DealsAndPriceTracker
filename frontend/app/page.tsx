import Link from "next/link";
import { InsightsPanel } from "@/components/InsightsPanel";
import { categoryLabel, formatPrice, getInsights, getProducts, type Category } from "@/lib/api";

export const dynamic = "force-dynamic";

const categories: (Category | undefined)[] = [undefined, "DIAPERS", "BABY_FOOD"];

export default async function HomePage({
  searchParams,
}: {
  searchParams: { category?: string; q?: string };
}) {
  const category = (["DIAPERS", "BABY_FOOD"] as const).find((c) => c === searchParams.category);
  const [products, insights] = await Promise.all([getProducts(category, searchParams.q), getInsights()]);

  return (
    <div className="space-y-8">
      <section>
        <h2 className="mb-3 text-lg font-semibold">Insights</h2>
        <InsightsPanel insights={insights} />
      </section>

      <section>
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <h2 className="mr-4 text-lg font-semibold">Products</h2>
          {categories.map((c) => (
            <Link
              key={c ?? "all"}
              href={c ? `/?category=${c}` : "/"}
              className={`rounded-full border px-3 py-1 text-sm ${
                c === category ? "border-slate-900 bg-slate-900 text-white" : "bg-white"
              }`}
            >
              {c ? categoryLabel[c] : "All"}
            </Link>
          ))}
          <form className="ml-auto" action="/">
            {category && <input type="hidden" name="category" value={category} />}
            <input
              name="q"
              defaultValue={searchParams.q}
              placeholder="Search brand or product…"
              className="rounded-md border px-3 py-1 text-sm"
            />
          </form>
        </div>

        <div className="overflow-x-auto rounded-lg border bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-100 text-left text-slate-600">
              <tr>
                <th className="px-3 py-2">Product</th>
                <th className="px-3 py-2">Brand</th>
                <th className="px-3 py-2">Size</th>
                <th className="px-3 py-2">Lowest price</th>
                <th className="px-3 py-2">Per unit</th>
                <th className="px-3 py-2">Cheapest at</th>
                <th className="px-3 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id} className="border-t hover:bg-slate-50">
                  <td className="px-3 py-2" dir="auto">
                    <Link href={`/products/${p.id}`} className="font-medium hover:underline">
                      {p.title}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{p.brand}</td>
                  <td className="px-3 py-2">{p.sizeLabel ?? "—"}</td>
                  <td className="px-3 py-2">{formatPrice(p.lowestPrice)}</td>
                  <td className="px-3 py-2">
                    {p.lowestUnitPrice != null ? `${formatPrice(p.lowestUnitPrice)} / ${p.unitLabel}` : "—"}
                  </td>
                  <td className="px-3 py-2">{p.lowestPriceStore ?? "—"}</td>
                  <td className="px-3 py-2">
                    {p.hasGenuineDeal && (
                      <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs text-emerald-800">Deal</span>
                    )}
                  </td>
                </tr>
              ))}
              {products.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-3 py-6 text-center text-slate-500">
                    No products found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
