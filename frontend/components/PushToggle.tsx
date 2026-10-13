"use client";

import { useEffect, useState } from "react";

type Status = "loading" | "unsupported" | "ios" | "denied" | "off" | "on";

// The VAPID key is base64url; PushManager.subscribe wants raw bytes.
function keyBytes(base64url: string): Uint8Array {
  const base64 = (base64url + "=".repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function sameKey(a: ArrayBuffer | null | undefined, b: Uint8Array) {
  if (!a) return false;
  const bytes = new Uint8Array(a);
  return bytes.length === b.length && bytes.every((v, i) => v === b[i]);
}

async function serverKey() {
  const res = await fetch("/api/push/public-key");
  const { publicKey } = (await res.json()) as { publicKey: string };
  return keyBytes(publicKey);
}

function save(subscription: PushSubscription) {
  return fetch("/api/push/subscription", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(subscription),
  });
}

// Browser notifications for real deals on starred products.
export function PushToggle() {
  const [status, setStatus] = useState<Status>("loading");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);

  useEffect(() => {
    if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window)) {
      // iPhones only allow web push for sites added to the Home Screen.
      setStatus(/iPhone|iPad/.test(navigator.userAgent) ? "ios" : "unsupported");
      return;
    }
    (async () => {
      const registration = await navigator.serviceWorker.register("/sw.js");
      const existing = await registration.pushManager.getSubscription();
      if (Notification.permission === "denied") {
        setStatus("denied");
      } else if (existing) {
        // Re-save it: the user may have logged in on this browser, or the server key may have changed.
        const key = await serverKey();
        const current = sameKey(existing.options.applicationServerKey, key)
          ? existing
          : await existing.unsubscribe().then(() =>
              registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key }),
            );
        setStatus((await save(current)).ok ? "on" : "off");
      } else {
        setStatus("off");
      }
    })().catch(() => setStatus("unsupported"));
  }, []);

  async function enable() {
    setBusy(true);
    setNote(null);
    try {
      if ((await Notification.requestPermission()) !== "granted") {
        setStatus(Notification.permission === "denied" ? "denied" : "off");
        return;
      }
      const registration = await navigator.serviceWorker.ready;
      await (await registration.pushManager.getSubscription())?.unsubscribe();
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: await serverKey(),
      });
      if ((await save(subscription)).ok) {
        setStatus("on");
      } else {
        await subscription.unsubscribe();
        setNote("Couldn't turn on alerts. Try again.");
      }
    } catch {
      setNote("Couldn't turn on alerts. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function disable() {
    setBusy(true);
    setNote(null);
    const registration = await navigator.serviceWorker.ready;
    const subscription = await registration.pushManager.getSubscription();
    if (subscription) {
      await fetch("/api/push/subscription", {
        method: "DELETE",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ endpoint: subscription.endpoint }),
      });
      await subscription.unsubscribe();
    }
    setStatus("off");
    setBusy(false);
  }

  async function test() {
    setBusy(true);
    const res = await fetch("/api/push/test", { method: "POST" });
    const delivered = res.ok ? ((await res.json()) as { delivered: number }).delivered : 0;
    setNote(delivered > 0 ? "Test notification sent." : "The test notification didn't go through.");
    setBusy(false);
  }

  const hint: Partial<Record<Status, string>> = {
    unsupported: "This browser can't show notifications.",
    ios: "On iPhone, add this site to your Home Screen to get deal alerts.",
    denied: "Notifications are blocked for this site in your browser settings.",
  };

  if (status === "loading") return null;
  if (hint[status]) return <span className="text-sm text-slate-500">{hint[status]}</span>;

  return (
    <div className="flex items-center gap-3 text-sm text-slate-600">
      <label className="flex items-center gap-2" title="Get a browser notification when a starred product has a real deal">
        <input
          type="checkbox"
          checked={status === "on"}
          disabled={busy}
          onChange={(e) => (e.target.checked ? enable() : disable())}
        />
        Deal alerts
      </label>
      {status === "on" && (
        <button type="button" onClick={test} disabled={busy} className="text-slate-500 underline hover:text-slate-900">
          Send test
        </button>
      )}
      {note && <span className="text-slate-500">{note}</span>}
    </div>
  );
}
