import type { Metadata } from "next";
import Link from "next/link";
import { getCurrentUser } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "Deals & Price Tracker",
  description: "Diaper and baby food prices across Yad Eliyahu stores, with real-deal detection.",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const user = await getCurrentUser();
  return (
    <html lang="en">
      <body className="min-h-screen">
        <header className="border-b bg-white">
          <nav className="mx-auto flex max-w-5xl items-center gap-6 px-4 py-3">
            <Link href="/" className="font-semibold">
              🍼 Deals &amp; Price Tracker
            </Link>
            <Link href="/" className="text-sm text-slate-600 hover:text-slate-900">
              Products
            </Link>
            <Link href="/chat" className="text-sm text-slate-600 hover:text-slate-900">
              Ask
            </Link>
            <div className="ml-auto flex items-center gap-3 text-sm">
              {user ? (
                <>
                  <span className="text-slate-600">{user.email}</span>
                  <form action="/api/auth/logout" method="post">
                    <button type="submit" className="text-slate-600 hover:text-slate-900">
                      Log out
                    </button>
                  </form>
                </>
              ) : (
                <Link href="/login" className="text-slate-600 hover:text-slate-900">
                  Log in
                </Link>
              )}
            </div>
          </nav>
        </header>
        <main className="mx-auto max-w-5xl px-4 py-6">{children}</main>
      </body>
    </html>
  );
}
