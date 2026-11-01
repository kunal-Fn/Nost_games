// Registers the service worker so the site can be installed and played offline.
// Each page passes the path back to the site root in data-root.
(() => {
  if (!("serviceWorker" in navigator)) return;
  const root = document.currentScript.dataset.root || "./";
  window.addEventListener("load", () => {
    navigator.serviceWorker.register(`${root}sw.js`, { scope: root }).catch(() => {});
  });
})();
