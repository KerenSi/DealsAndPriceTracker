import { cookies } from "next/headers";
import { getMe, type User } from "@/lib/api";

// The backend's bearer token lives in an httpOnly cookie so browser scripts never see it.
export const SESSION_COOKIE = "session";

export function getSessionToken(): string | undefined {
  return cookies().get(SESSION_COOKIE)?.value;
}

/** The logged-in user, or null when there's no session or it has expired. */
export async function getCurrentUser(): Promise<User | null> {
  const token = getSessionToken();
  if (!token) return null;
  return getMe(token).catch(() => null);
}
