# Nost Games

Nostalgic games that run in the browser. Play: https://nostgames.com/

Each game lives in its own folder under `games/`
and is plain HTML, CSS and JavaScript, so it runs by opening its `index.html`.

| Game | Folder | Controls |
| --- | --- | --- |
| Snake | `games/snake/` | Arrows / WASD / 2 4 6 8, Space or 5 to pause, swipe on phones |
| Brick Breaker | `games/bricks/` | Arrows / A D / 4 6, 2 or Up to launch, Space or 5 to pause, drag on phones |

## Install on a phone

The site is a PWA. Android (Chrome): menu → Install app. iPhone (Safari): Share → Add to Home Screen.
It opens full-screen and works offline after the first visit.

When adding a game, list its files in `FILES` in `sw.js` and bump `CACHE` (e.g. `nost-games-v2`)
so installed copies pick it up offline.
