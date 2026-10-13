import { forwardAsUser } from "@/lib/backendProxy";

// Sends a sample notification to the logged-in user's browsers.
export function POST() {
  return forwardAsUser("POST", "/api/me/push-subscription/test");
}
