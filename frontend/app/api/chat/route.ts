import { NextResponse } from "next/server";
import { BACKEND_URL } from "@/lib/api";

// Proxies chat requests to the backend so the browser never talks to it directly.
export async function POST(request: Request) {
  const body = await request.text();
  const res = await fetch(`${BACKEND_URL}/api/chat`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
    cache: "no-store",
  });
  return new NextResponse(await res.text(), {
    status: res.status,
    headers: { "content-type": "application/json" },
  });
}
