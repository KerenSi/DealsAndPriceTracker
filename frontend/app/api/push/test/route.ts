import { forwardAsUser } from "@/lib/backendProxy";

// Sends a sample notification to one of the logged-in user's browsers ({ endpoint }), or to all of them.
export async function POST(request: Request) {
  return forwardAsUser("POST", "/api/me/push-subscription/test", (await request.text()) || undefined);
}
