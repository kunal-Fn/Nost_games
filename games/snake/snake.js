"use strict";

// Classic phone Snake: the snake wraps around the edges, speeds up as it eats,
// and the game ends only when it runs into itself.

const COLS = 21;
const ROWS = 15;
const START_DELAY = 160; // ms per step
const MIN_DELAY = 60;
const SPEEDUP = 4; // ms faster per food eaten
const LCD = "#c7f0d8";
const PIXEL = "#43523d";

const DIRS = {
  up: { x: 0, y: -1 },
  down: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
};

const canvas = document.getElementById("game");
const ctx = canvas.getContext("2d");
const overlay = document.getElementById("overlay");
const scoreEl = document.getElementById("score");
const bestEl = document.getElementById("best");
const soundBtn = document.getElementById("sound");
const pauseBtn = document.getElementById("pause");

let snake, dir, queue, food, score, delay, state, timer;
let best = Number(localStorage.getItem("snake.best")) || 0;
let soundOn = localStorage.getItem("snake.sound") !== "off";
let cell = 16;

// ---------- game state ----------

function reset() {
  const midY = Math.floor(ROWS / 2);
  snake = [
    { x: 6, y: midY },
    { x: 5, y: midY },
    { x: 4, y: midY },
  ];
  dir = "right";
  queue = [];
  score = 0;
  delay = START_DELAY;
  placeFood();
  updateHud();
  draw();
}

function placeFood() {
  const free = [];
  for (let y = 0; y < ROWS; y++) {
    for (let x = 0; x < COLS; x++) {
      if (!snake.some((s) => s.x === x && s.y === y)) free.push({ x, y });
    }
  }
  food = free[Math.floor(Math.random() * free.length)];
}

function step() {
  // Take at most one queued turn per step so quick double taps both register.
  if (queue.length) dir = queue.shift();
  const d = DIRS[dir];
  const head = {
    x: (snake[0].x + d.x + COLS) % COLS,
    y: (snake[0].y + d.y + ROWS) % ROWS,
  };

  const eating = head.x === food.x && head.y === food.y;
  // The tail moves out of the way this step unless we're growing.
  const body = eating ? snake : snake.slice(0, -1);
  if (body.some((s) => s.x === head.x && s.y === head.y)) {
    gameOver();
    return;
  }

  snake.unshift(head);
  if (eating) {
    score++;
    delay = Math.max(MIN_DELAY, delay - SPEEDUP);
    beep(880, 0.05);
    if (snake.length === COLS * ROWS) {
      gameOver(true);
      return;
    }
    placeFood();
    updateHud();
  } else {
    snake.pop();
  }

  draw();
  timer = setTimeout(step, delay);
}

function turn(name) {
  if (state === "ready" || state === "over") start();
  if (state !== "playing") return;
  const last = queue.length ? queue[queue.length - 1] : dir;
  const a = DIRS[last];
  const b = DIRS[name];
  // Ignore repeats and direct reversals into the snake's own neck.
  if (a.x + b.x === 0 && a.y + b.y === 0) return;
  if (name === last) return;
  if (queue.length < 2) queue.push(name);
}

function start() {
  unlockAudio();
  if (state === "over") reset();
  state = "playing";
  showOverlay(null);
  pauseBtn.textContent = "Pause";
  clearTimeout(timer);
  timer = setTimeout(step, delay);
}

function pause() {
  if (state !== "playing") return;
  state = "paused";
  clearTimeout(timer);
  pauseBtn.textContent = "Resume";
  showOverlay('<div class="big">PAUSED</div><div>Press 5 or Space</div>');
}

function toggle() {
  if (state === "playing") pause();
  else start();
}

function gameOver(won = false) {
  state = "over";
  clearTimeout(timer);
  beep(160, 0.35);
  const newBest = score > best;
  if (newBest) {
    best = score;
    localStorage.setItem("snake.best", String(best));
  }
  updateHud();
  draw();
  showOverlay(
    `<div class="big">${won ? "YOU WIN!" : "GAME OVER"}</div>` +
      `<div>Score ${pad(score)}</div>` +
      (newBest ? "<div>New high score!</div>" : "") +
      "<div>Press 5 or Space</div>"
  );
  pauseBtn.textContent = "Play";
}

// ---------- drawing ----------

function resize() {
  const width = canvas.parentElement.clientWidth - 4; // minus the 2px border each side
  cell = Math.max(8, Math.floor(width / COLS));
  const ratio = window.devicePixelRatio || 1;
  canvas.style.width = `${cell * COLS}px`;
  canvas.style.height = `${cell * ROWS}px`;
  canvas.width = cell * COLS * ratio;
  canvas.height = cell * ROWS * ratio;
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  if (snake) draw();
}

function draw() {
  ctx.fillStyle = LCD;
  ctx.fillRect(0, 0, cell * COLS, cell * ROWS);
  ctx.fillStyle = PIXEL;

  // Each segment is a square with a small gap, like LCD pixels.
  const gap = Math.max(1, Math.round(cell / 8));
  snake.forEach((s) => ctx.fillRect(s.x * cell + gap, s.y * cell + gap, cell - 2 * gap, cell - 2 * gap));

  // Food is a small diamond.
  const cx = food.x * cell + cell / 2;
  const cy = food.y * cell + cell / 2;
  const r = cell / 2 - gap;
  ctx.beginPath();
  ctx.moveTo(cx, cy - r);
  ctx.lineTo(cx + r, cy);
  ctx.lineTo(cx, cy + r);
  ctx.lineTo(cx - r, cy);
  ctx.closePath();
  ctx.fill();
}

function updateHud() {
  scoreEl.textContent = pad(score);
  bestEl.textContent = `HI ${pad(best)}`;
}

function pad(n) {
  return String(n).padStart(4, "0");
}

function showOverlay(html) {
  overlay.innerHTML = html || "";
  overlay.classList.toggle("hidden", !html);
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

const KEYS = {
  ArrowUp: "up", w: "up", W: "up", 2: "up",
  ArrowDown: "down", s: "down", S: "down", 8: "down",
  ArrowLeft: "left", a: "left", A: "left", 4: "left",
  ArrowRight: "right", d: "right", D: "right", 6: "right",
};

document.addEventListener("keydown", (e) => {
  if (KEYS[e.key]) {
    e.preventDefault();
    turn(KEYS[e.key]);
  } else if (e.key === " " || e.key === "5" || e.key === "p" || e.key === "P" || e.key === "Enter") {
    e.preventDefault();
    toggle();
  }
});

document.querySelectorAll(".key").forEach((btn) => {
  // pointerdown reacts faster than click on phones.
  btn.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    if (btn.dataset.dir) turn(btn.dataset.dir);
    else toggle();
  });
});

pauseBtn.addEventListener("click", toggle);
soundBtn.addEventListener("click", () => {
  soundOn = !soundOn;
  localStorage.setItem("snake.sound", soundOn ? "on" : "off");
  updateSoundButton();
  unlockAudio();
});

// Swipe anywhere on the screen to steer, tap to start/pause.
const board = canvas.parentElement;
let touchStart = null;
board.addEventListener("pointerdown", (e) => {
  touchStart = { x: e.clientX, y: e.clientY };
});
board.addEventListener("pointerup", (e) => {
  if (!touchStart) return;
  const dx = e.clientX - touchStart.x;
  const dy = e.clientY - touchStart.y;
  touchStart = null;
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 20) {
    toggle();
  } else if (Math.abs(dx) > Math.abs(dy)) {
    turn(dx > 0 ? "right" : "left");
  } else {
    turn(dy > 0 ? "down" : "up");
  }
});

document.addEventListener("visibilitychange", () => {
  if (document.hidden) pause();
});
window.addEventListener("resize", resize);

// ---------- boot ----------

state = "ready";
updateSoundButton();
resize();
reset();
showOverlay('<div class="big">SNAKE</div><div>Press 5, Space or tap</div><div>to start</div>');
pauseBtn.textContent = "Play";
