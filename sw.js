// Offline support: serves files from the cache straight away and refreshes them
// in the background, so a new version shows up on the next launch.
// Add each new game's files to FILES.

const CACHE = "nost-games-v1";
const FILES = [
  "./",
  "index.html",
  "manifest.webmanifest",
  "icons/icon-192.png",
  "icons/icon-512.png",
  "icons/apple-touch-icon.png",
  "games/shared/phone.css",
  "games/shared/install.js",
  "games/snake/",
  "games/snake/index.html",
  "games/snake/snake.js",
  "games/bricks/",
  "games/bricks/index.html",
  "games/bricks/bricks.js",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(FILES)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET" || new URL(req.url).origin !== location.origin) return;

  event.respondWith(
    caches.open(CACHE).then(async (cache) => {
      const cached = await cache.match(req, { ignoreSearch: true });
      const network = fetch(req)
        .then((res) => {
          if (res.ok) cache.put(req, res.clone());
          return res;
        })
        .catch(() => cached);
      event.waitUntil(network);
      return cached || network;
    })
  );
});
