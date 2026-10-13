import { NextResponse } from "next/server";
import { BACKEND_URL } from "@/lib/api";
import { SESSION_COOKIE, getSessionToken } from "@/lib/session";

export async function POST(request: Request) {
  const token = getSessionToken();
  if (token) {
    await fetch(`${BACKEND_URL}/api/auth/logout`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}` },
      cache: "no-store",
    }).catch(() => undefined);
  }
  const response = NextResponse.redirect(new URL("/", request.url), 303);
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
