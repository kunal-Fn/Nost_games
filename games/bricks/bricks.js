"use strict";

// Brick Breaker in the same phone frame as Snake. Everything is laid out in a
// fixed 210 x 150 world and scaled to the screen.

const W = 210;
const H = 150;
const BRICK_COLS = 10;
const BRICK_W = 20;
const BRICK_H = 7;
const BRICK_TOP = 14;
const BRICK_LEFT = (W - BRICK_COLS * BRICK_W) / 2;
const PADDLE_W = 34;
const PADDLE_H = 4;
const PADDLE_Y = H - 10;
const PADDLE_SPEED = 170; // world units per second
const BALL = 4;
const BASE_SPEED = 95;
const LEVEL_SPEEDUP = 12;
const MAX_SPEED = 190;
const MAX_BOUNCE = Math.PI / 3; // 60° from vertical at the paddle's edge
const LIVES = 3;
const LCD = "#c7f0d8";
const PIXEL = "#43523d";

// One string per row, "#" is a brick. Levels cycle after the last one, faster each time.
const LEVELS = [
  ["##########", "##########", "##########", "##########"],
  ["#.#.#.#.#.", ".#.#.#.#.#", "#.#.#.#.#.", ".#.#.#.#.#", "#.#.#.#.#.", ".#.#.#.#.#"],
  ["....##....", "...####...", "..######..", ".########.", "##########"],
  ["##########", "#........#", "#.######.#", "#.#....#.#", "#.######.#", "##########"],
  ["##########", "##########", "##########", "##########", "##########", "##########"],
];

const canvas = document.getElementById("game");
const ctx = canvas.getContext("2d");
const overlay = document.getElementById("overlay");
const scoreEl = document.getElementById("score");
const statusEl = document.getElementById("status");
const bestEl = document.getElementById("best");
const soundBtn = document.getElementById("sound");
const pauseBtn = document.getElementById("pause");

let bricks, paddleX, ball, speed, score, lives, level, state;
let pausedFrom = "playing";
let best = Number(localStorage.getItem("bricks.best")) || 0;
let soundOn = localStorage.getItem("bricks.sound") !== "off";
let scale = 2;
const held = { left: false, right: false };

// ---------- game state ----------

function newGame() {
  score = 0;
  lives = LIVES;
  level = 1;
  buildLevel();
  serve();
  updateHud();
}

function buildLevel() {
  const rows = LEVELS[(level - 1) % LEVELS.length];
  bricks = [];
  rows.forEach((row, r) => {
    [...row].forEach((ch, c) => {
      if (ch !== "#") return;
      bricks.push({
        x: BRICK_LEFT + c * BRICK_W,
        y: BRICK_TOP + r * BRICK_H,
        points: rows.length - r, // higher rows are worth more
        alive: true,
      });
    });
  });
  speed = Math.min(MAX_SPEED, BASE_SPEED + (level - 1) * LEVEL_SPEEDUP);
}

// Ball sits on the paddle until launched.
function serve() {
  paddleX = paddleX ?? (W - PADDLE_W) / 2;
  ball = { x: 0, y: 0, vx: 0, vy: 0 };
  stickBall();
  state = "serve";
  pauseBtn.textContent = "Pause";
}

function stickBall() {
  ball.x = paddleX + PADDLE_W / 2 - BALL / 2;
  ball.y = PADDLE_Y - BALL - 1;
}

function launch() {
  unlockAudio();
  if (state === "title" || state === "over") newGame();
  if (state === "paused") {
    resume();
    return;
  }
  if (state !== "serve") return;
  const angle = (Math.random() * 2 - 1) * (Math.PI / 9); // up to 20° either side
  ball.vx = speed * Math.sin(angle);
  ball.vy = -speed * Math.cos(angle);
  state = "playing";
  showOverlay(null);
}

function toggle() {
  if (state === "playing") pause();
  else launch();
}

function pause() {
  if (state !== "playing" && state !== "serve") return;
  pausedFrom = state;
  state = "paused";
  pauseBtn.textContent = "Resume";
  showOverlay('<div class="big">PAUSED</div><div>Press 5 or Space</div>');
}

function resume() {
  state = pausedFrom;
  pauseBtn.textContent = "Pause";
  showOverlay(null);
}

function loseLife() {
  lives--;
  beep(160, 0.3);
  if (lives <= 0) {
    gameOver();
    return;
  }
  serve();
  updateHud();
  showOverlay(`<div class="big">${lives} ${lives === 1 ? "LIFE" : "LIVES"} LEFT</div><div>Press 2 or tap to launch</div>`);
}

function levelUp() {
  level++;
  beep(660, 0.08);
  setTimeout(() => beep(990, 0.12), 100);
  buildLevel();
  serve();
  updateHud();
  showOverlay(`<div class="big">LEVEL ${level}</div><div>Press 2 or tap to launch</div>`);
}

function gameOver() {
  state = "over";
  const newBest = score > best;
  if (newBest) {
    best = score;
    localStorage.setItem("bricks.best", String(best));
  }
  updateHud();
  pauseBtn.textContent = "Play";
  showOverlay(
    '<div class="big">GAME OVER</div>' +
      `<div>Score ${pad(score)}</div>` +
      (newBest ? "<div>New high score!</div>" : "") +
      "<div>Press 2, 5 or tap</div>"
  );
}

// ---------- physics ----------

function update(dt) {
  const move = (held.right ? 1 : 0) - (held.left ? 1 : 0);
  if (move) movePaddle(paddleX + move * PADDLE_SPEED * dt);

  if (state === "serve" || state === "title") {
    stickBall();
    return;
  }
  if (state !== "playing") return;

  // Small sub-steps so the ball can't skip through a brick in one frame.
  const steps = Math.ceil((speed * dt) / 2);
  for (let i = 0; i < steps && state === "playing"; i++) {
    stepBall(dt / steps);
  }
}

function stepBall(dt) {
  ball.x += ball.vx * dt;
  ball.y += ball.vy * dt;

  if (ball.x < 0) {
    ball.x = 0;
    ball.vx = Math.abs(ball.vx);
    beep(520, 0.02);
  } else if (ball.x + BALL > W) {
    ball.x = W - BALL;
    ball.vx = -Math.abs(ball.vx);
    beep(520, 0.02);
  }
  if (ball.y < 0) {
    ball.y = 0;
    ball.vy = Math.abs(ball.vy);
    beep(520, 0.02);
  }
  if (ball.y > H) {
    loseLife();
    return;
  }

  // Paddle: where the ball lands sets the bounce angle, so players can aim.
  if (
    ball.vy > 0 &&
    ball.y + BALL >= PADDLE_Y &&
    ball.y + BALL <= PADDLE_Y + PADDLE_H + 2 &&
    ball.x + BALL >= paddleX &&
    ball.x <= paddleX + PADDLE_W
  ) {
    const hit = (ball.x + BALL / 2 - (paddleX + PADDLE_W / 2)) / (PADDLE_W / 2);
    const angle = Math.max(-1, Math.min(1, hit)) * MAX_BOUNCE;
    ball.vx = speed * Math.sin(angle);
    ball.vy = -speed * Math.cos(angle);
    ball.y = PADDLE_Y - BALL;
    beep(440, 0.03);
  }

  for (const b of bricks) {
    if (!b.alive) continue;
    const overlapX = Math.min(b.x + BRICK_W, ball.x + BALL) - Math.max(b.x, ball.x);
    const overlapY = Math.min(b.y + BRICK_H, ball.y + BALL) - Math.max(b.y, ball.y);
    if (overlapX <= 0 || overlapY <= 0) continue;

    // Bounce off whichever side the ball went into the least.
    if (overlapX < overlapY) {
      ball.vx = -ball.vx;
      ball.x += ball.vx > 0 ? overlapX : -overlapX;
    } else {
      ball.vy = -ball.vy;
      ball.y += ball.vy > 0 ? overlapY : -overlapY;
    }
    b.alive = false;
    score += b.points;
    updateHud();
    beep(880, 0.03);
    if (!bricks.some((x) => x.alive)) levelUp();
    break;
  }
}

function movePaddle(x) {
  paddleX = Math.max(0, Math.min(W - PADDLE_W, x));
}

// ---------- drawing ----------

function resize() {
  const width = canvas.parentElement.clientWidth - 4; // minus the 2px border each side
  scale = width / W;
  const ratio = window.devicePixelRatio || 1;
  canvas.style.width = `${W * scale}px`;
  canvas.style.height = `${H * scale}px`;
  canvas.width = Math.round(W * scale * ratio);
  canvas.height = Math.round(H * scale * ratio);
  ctx.setTransform(scale * ratio, 0, 0, scale * ratio, 0, 0);
}

function draw() {
  ctx.fillStyle = LCD;
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = PIXEL;

  // Bricks get a 1-unit gap so they read like LCD blocks.
  bricks.forEach((b) => {
    if (b.alive) ctx.fillRect(b.x + 1, b.y + 1, BRICK_W - 2, BRICK_H - 2);
  });
  ctx.fillRect(paddleX, PADDLE_Y, PADDLE_W, PADDLE_H);
  ctx.fillRect(ball.x, ball.y, BALL, BALL);
}

function updateHud() {
  scoreEl.textContent = pad(score);
  statusEl.textContent = `L${level} ♥${lives}`;
  bestEl.textContent = `HI ${pad(best)}`;
}

function pad(n) {
  return String(n).padStart(4, "0");
}

function showOverlay(html) {
  overlay.innerHTML = html || "";
  overlay.classList.toggle("hidden", !html);
}

let last = performance.now();
function frame(now) {
  const dt = Math.min(0.033, (now - last) / 1000); // cap so a slow frame can't teleport the ball
  last = now;
  update(dt);
  draw();
  requestAnimationFrame(frame);
}

// ---------- sound ----------

let audio = null;

function unlockAudio() {
  // Browsers only allow audio after a user gesture, so it's created on the first key or tap.
  if (!audio && window.AudioContext) audio = new AudioContext();
  if (audio && audio.state === "suspended") audio.resume();
}

function beep(freq, seconds) {
  if (!soundOn || !audio) return;
  const osc = audio.createOscillator();
  const gain = audio.createGain();
  osc.type = "square";
  osc.frequency.value = freq;
  gain.gain.value = 0.05;
  osc.connect(gain).connect(audio.destination);
  osc.start();
  osc.stop(audio.currentTime + seconds);
}

function updateSoundButton() {
  soundBtn.textContent = `Sound: ${soundOn ? "on" : "off"}`;
}

// ---------- input ----------

const MOVE_KEYS = {
  ArrowLeft: "left", a: "left", A: "left", 4: "left",
  ArrowRight: "right", d: "right", D: "right", 6: "right",
};

document.addEventListener("keydown", (e) => {
  if (MOVE_KEYS[e.key]) {
    e.preventDefault();
    unlockAudio();
    held[MOVE_KEYS[e.key]] = true;
  } else if (["ArrowUp", "w", "W", "2"].includes(e.key)) {
    e.preventDefault();
    launch();
  } else if ([" ", "5", "p", "P", "Enter"].includes(e.key)) {
    e.preventDefault();
    toggle();
  }
});

document.addEventListener("keyup", (e) => {
  if (MOVE_KEYS[e.key]) held[MOVE_KEYS[e.key]] = false;
});

document.querySelectorAll(".key").forEach((btn) => {
  const dir = btn.dataset.dir;
  btn.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    unlockAudio();
    if (dir) held[dir] = true;
    else if (btn.dataset.action === "launch") launch();
    else toggle();
  });
  if (dir) {
    // Release on every way a finger can leave the key, or the paddle keeps sliding.
    ["pointerup", "pointerleave", "pointercancel"].forEach((type) =>
      btn.addEventListener(type, () => (held[dir] = false))
    );
  }
});

pauseBtn.addEventListener("click", toggle);
soundBtn.addEventListener("click", () => {
  soundOn = !soundOn;
  localStorage.setItem("bricks.sound", soundOn ? "on" : "off");
  updateSoundButton();
  unlockAudio();
});

// Drag (or move the mouse) over the screen to steer; a tap launches the ball.
const board = canvas.parentElement;
let drag = null;

function pointerToPaddle(e) {
  const rect = canvas.getBoundingClientRect();
  movePaddle((e.clientX - rect.left) / scale - PADDLE_W / 2);
}

board.addEventListener("pointerdown", (e) => {
  unlockAudio();
  drag = { x: e.clientX, y: e.clientY, moved: 0 };
  board.setPointerCapture(e.pointerId);
  pointerToPaddle(e);
});
board.addEventListener("pointermove", (e) => {
  if (drag) drag.moved = Math.max(drag.moved, Math.abs(e.clientX - drag.x) + Math.abs(e.clientY - drag.y));
  if (drag || e.pointerType === "mouse") pointerToPaddle(e);
});
board.addEventListener("pointerup", () => {
  if (drag && drag.moved < 10 && state !== "playing") launch();
  drag = null;
});
board.addEventListener("pointercancel", () => (drag = null));

document.addEventListener("visibilitychange", () => {
  if (document.hidden) pause();
});
window.addEventListener("resize", resize);

// ---------- boot ----------

updateSoundButton();
resize();
newGame();
state = "title";
pauseBtn.textContent = "Play";
showOverlay('<div class="big">BRICK BREAKER</div><div>Press 2, 5 or tap</div><div>to start</div>');
requestAnimationFrame(frame);
