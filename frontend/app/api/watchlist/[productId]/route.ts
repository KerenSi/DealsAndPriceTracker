import { NextResponse } from "next/server";
import { BACKEND_URL } from "@/lib/api";
import { getSessionToken } from "@/lib/session";

// Adds or removes a product from the logged-in user's watchlist.
async function forward(method: "PUT" | "DELETE", productId: string) {
  const token = getSessionToken();
  if (!token) {
    return NextResponse.json({ error: "Login required" }, { status: 401 });
  }
  const res = await fetch(`${BACKEND_URL}/api/me/watchlist/${encodeURIComponent(productId)}`, {
    method,
    headers: { authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  return new NextResponse(null, { status: res.status });
}

export function PUT(_: Request, { params }: { params: { productId: string } }) {
  return forward("PUT", params.productId);
}

export function DELETE(_: Request, { params }: { params: { productId: string } }) {
  return forward("DELETE", params.productId);
}
