import { forwardAsUser } from "@/lib/backendProxy";

// Marks a product as watched or not interested (PUT { interest }), or clears the mark (DELETE).
const path = (productId: string) => `/api/me/products/${encodeURIComponent(productId)}/interest`;

export async function PUT(request: Request, { params }: { params: { productId: string } }) {
  return forwardAsUser("PUT", path(params.productId), await request.text());
}

export function DELETE(_: Request, { params }: { params: { productId: string } }) {
  return forwardAsUser("DELETE", path(params.productId));
}
