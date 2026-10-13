import { NextResponse } from "next/server";
import { BACKEND_URL } from "@/lib/api";
import { SESSION_COOKIE } from "@/lib/session";

interface AuthResponse {
  token: string;
  expiresAt: string;
}

// The backend doesn't put messages in error bodies, so map the statuses it uses.
const errors: Record<number, string> = {
  400: "Enter a valid email and a password of 8 to 72 characters.",
  401: "Wrong email or password.",
  409: "An account with this email already exists.",
};

// Forwards a login or register request to the backend and stores the returned token in a cookie.
export async function forwardAuth(request: Request, path: "login" | "register") {
  const res = await fetch(`${BACKEND_URL}/api/auth/${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: await request.text(),
    cache: "no-store",
  });
  if (!res.ok) {
    return NextResponse.json({ error: errors[res.status] ?? "Something went wrong" }, { status: res.status });
  }
  const { token, expiresAt } = (await res.json()) as AuthResponse;
  const response = NextResponse.json({ ok: true });
  response.cookies.set(SESSION_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    expires: new Date(expiresAt),
  });
  return response;
}
