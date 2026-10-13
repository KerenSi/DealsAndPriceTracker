import { NextResponse } from "next/server";
import { BACKEND_URL } from "@/lib/api";

// The server's VAPID key, which browsers need to subscribe to push.
export async function GET() {
  const res = await fetch(`${BACKEND_URL}/api/push/public-key`, { cache: "no-store" });
  return NextResponse.json(await res.json(), { status: res.status });
}
