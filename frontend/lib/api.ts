// Types mirror the backend DTOs in com.dealstracker.api.Dtos.

export type Category = "DIAPERS" | "BABY_FOOD";

export type Interest = "WATCHED" | "NOT_INTERESTED";

export interface Store {
  id: string;
  chain: string;
  branchName: string;
  city: string;
}

export interface ProductSummary {
  id: string;
  barcode: string;
  title: string;
  brand: string | null;
  category: Category;
  sizeLabel: string | null;
  unitQuantity: number | null;
  unitType: "UNITS" | "GRAMS" | "ML" | null;
  lowestPrice: number | null;
  lowestPriceStore: string | null;
  lowestUnitPrice: number | null;
  unitLabel: string | null;
  storeCount: number;
  hasGenuineDeal: boolean;
  interest: Interest | null;
}

export interface DealEvaluation {
  genuineDeal: boolean;
  effectivePrice: number;
  baselinePrice: number | null;
  longBaselinePrice: number | null;
  dropPercent: number | null;
  claimedDiscountPercent: number | null;
  suspectedFakeDiscount: boolean;
  reasons: string[];
}

export interface StorePrice {
  listingId: string;
  store: Store;
  price: number;
  referencePrice: number | null;
  effectivePrice: number;
  unitPrice: number | null;
  unitLabel: string;
  promotion: boolean;
  promotionDescription: string | null;
  scrapedAt: string;
  deal: DealEvaluation;
}

export interface ProductDetail {
  product: ProductSummary;
  prices: StorePrice[];
}

export interface HistoryPoint {
  listingId: string;
  storeName: string;
  price: number;
  effectivePrice: number;
  promotion: boolean;
  scrapedAt: string;
}

export interface Insight {
  type: "CHEAPEST_PER_UNIT" | "GENUINE_DEAL";
  message: string;
  productId: string;
  storeName: string;
  price: number;
  unitPrice: number | null;
  unitLabel: string;
}

export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export interface User {
  id: string;
  email: string;
  showSimilarProducts: boolean;
}

// Pass the session token for anything that differs per user; those responses are never cached.
async function get<T>(path: string, token?: string): Promise<T> {
  const res = await fetch(
    `${BACKEND_URL}${path}`,
    token ? { headers: { authorization: `Bearer ${token}` }, cache: "no-store" } : { next: { revalidate: 300 } },
  );
  if (!res.ok) {
    throw new Error(`GET ${path} failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export function getProducts(category?: Category, q?: string, token?: string) {
  const params = new URLSearchParams();
  if (category) params.set("category", category);
  if (q) params.set("q", q);
  const qs = params.toString();
  return get<ProductSummary[]>(`/api/products${qs ? `?${qs}` : ""}`, token);
}

export const getProduct = (id: string, token?: string) => get<ProductDetail>(`/api/products/${id}`, token);
export const getHistory = (id: string, days = 60) =>
  get<HistoryPoint[]>(`/api/products/${id}/history?days=${days}`);
export const getInsights = (token?: string) => get<Insight[]>("/api/insights", token);
export const getMe = (token: string) => get<User>("/api/auth/me", token);

export const formatPrice = (v: number | null | undefined) => (v == null ? "—" : `₪${v.toFixed(2)}`);

export const categoryLabel: Record<Category, string> = {
  DIAPERS: "Diapers",
  BABY_FOOD: "Baby food",
};
