import { forwardAsUser } from "@/lib/backendProxy";

// Saves this browser's push subscription for the logged-in user (PUT), or forgets it (DELETE { endpoint }).
export async function PUT(request: Request) {
  return forwardAsUser("PUT", "/api/me/push-subscription", await request.text());
}

export async function DELETE(request: Request) {
  return forwardAsUser("DELETE", "/api/me/push-subscription", await request.text());
}
