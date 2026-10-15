// Service worker for deal notifications. The backend sends { title, body, url, tag } as the push payload.

self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data ? event.data.text() : "" };
  }
  event.waitUntil(
    self.registration.showNotification(data.title || "Deals & Price Tracker", {
      body: data.body || "",
      tag: data.tag,
      // A notification replacing an earlier one with the same tag would otherwise arrive silently.
      renotify: Boolean(data.tag),
      data: { url: data.url || "/" },
      lang: "he",
      dir: "auto",
    }),
  );
});

// Opens the deal's product page, reusing an open tab of the site when there is one.
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.url || "/", self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      const open = windows.find((w) => new URL(w.url).origin === self.location.origin);
      if (open) {
        return open.navigate(url).then((w) => (w || open).focus());
      }
      return self.clients.openWindow(url);
    }),
  );
});
