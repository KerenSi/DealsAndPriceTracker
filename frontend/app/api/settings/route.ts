import { forwardAsUser } from "@/lib/backendProxy";

export async function PUT(request: Request) {
  return forwardAsUser("PUT", "/api/me/settings", await request.text());
}
