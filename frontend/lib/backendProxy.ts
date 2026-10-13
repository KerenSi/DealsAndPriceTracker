import { NextResponse } from "next/server";
import { BACKEND_URL } from "@/lib/api";
import { getSessionToken } from "@/lib/session";

// Forwards a logged-in user's request to the backend with their session token.
export async function forwardAsUser(method: "PUT" | "DELETE", path: string, body?: string) {
  const token = getSessionToken();
  if (!token) {
    return NextResponse.json({ error: "Login required" }, { status: 401 });
  }
  const res = await fetch(`${BACKEND_URL}${path}`, {
    method,
    headers: { authorization: `Bearer ${token}`, ...(body ? { "content-type": "application/json" } : {}) },
    body,
    cache: "no-store",
  });
  return new NextResponse(res.status === 204 ? null : await res.text(), {
    status: res.status,
    headers: res.status === 204 ? undefined : { "content-type": "application/json" },
  });
}
