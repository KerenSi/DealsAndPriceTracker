import { forwardAuth } from "@/lib/authRoute";

export function POST(request: Request) {
  return forwardAuth(request, "register");
}
