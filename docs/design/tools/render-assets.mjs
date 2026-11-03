// Small software 3D renderer for the Chayeon black/red/white asset set.
// Threads use a sphere-swept z-buffer; avatars and the key visual are ray-marched SDFs.
import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';

const OUT = process.argv[2];
const ONLY = process.argv[3] || 'all';
fs.mkdirSync(OUT, { recursive: true });

const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const smooth = (a, b, x) => {
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
};
const mix = (a, b, t) => a + (b - a) * t;
const hex = (s) => [1, 3, 5].map((i) => Math.pow(parseInt(s.slice(i, i + 2), 16) / 255, 2.2));

const crcTable = new Int32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});
const crc32 = (buf) => {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 255] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
};
function writePNG(name, w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const file = path.join(OUT, `${name}.png`);
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 6 })), chunk('IEND', Buffer.alloc(0))]));
  console.log(file);
}
const tone = (x) => Math.round(255 * Math.pow(clamp(1 - Math.exp(-x * 1.5)), 1 / 2.2));

// Studio environment: one big softbox, a small window and two red rim lights.
const E = [0, 0, 0];
function env(x, y, z) {
  const up = Math.max(0, y);
  let r = 0.02 + 0.12 * up;
  let g = 0.008 + 0.025 * up;
  let b = 0.012 + 0.035 * up;
  const s1 = smooth(0.78, 0.93, x * -0.45 + y * 0.72 + z * 0.53);
  r += 3.4 * s1;
  g += 3.2 * s1;
  b += 3.2 * s1;
  const s2 = smooth(0.95, 0.985, x * 0.62 + y * 0.55 + z * 0.56);
  r += 1.7 * s2;
  g += 1.6 * s2;
  b += 1.6 * s2;
  const s3 = smooth(0.55, 0.95, x * 0.85 - y * 0.25 + z * 0.46);
  r += 1.7 * s3;
  g += 0.1 * s3;
  b += 0.16 * s3;
  const s4 = smooth(0.6, 0.95, x * -0.9 - y * 0.3 + z * 0.3);
  r += 1.2 * s4;
  g += 0.08 * s4;
  b += 0.12 * s4;
  E[0] = r;
  E[1] = g;
  E[2] = b;
  return E;
}

const C = [0, 0, 0];
function shade(nx, ny, nz, vx, vy, vz, base, gloss, ao = 1) {
  const ndv = Math.max(0, nx * vx + ny * vy + nz * vz);
  const F = 0.04 + 0.96 * Math.pow(1 - ndv, 5);
  const k = 2 * (nx * vx + ny * vy + nz * vz);
  const e = env(k * nx - vx, k * ny - vy, k * nz - vz);
  const wrap = Math.pow((nx * -0.45 + ny * 0.72 + nz * 0.53) * 0.5 + 0.5, 2.2);
  const bounce = Math.max(0, nx * 0.6 - ny * 0.55 + nz * 0.5);
  const refl = (F + 0.05) * gloss;
  C[0] = base[0] * (0.12 + 1.15 * wrap + 0.35 * bounce) * ao + e[0] * refl;
  C[1] = base[1] * (0.12 + 1.15 * wrap + 0.08 * bounce) * ao + e[1] * refl;
  C[2] = base[2] * (0.12 + 1.15 * wrap + 0.1 * bounce) * ao + e[2] * refl;
  return C;
}

// ---------- Threads: glossy rope, swept as spheres into a z-buffer ----------
function sweep(W, H, draw) {
  const zb = new Float32Array(W * H).fill(-1e9);
  const nb = new Float32Array(W * H * 3);
  const cb = new Uint8Array(W * H);
  const sphere = (cx, cy, cz, r, mat = 0) => {
    const x0 = Math.max(0, Math.floor(cx - r));
    const x1 = Math.min(W - 1, Math.ceil(cx + r));
    const y0 = Math.max(0, Math.floor(cy - r));
    const y1 = Math.min(H - 1, Math.ceil(cy + r));
    for (let y = y0; y <= y1; y++)
      for (let x = x0; x <= x1; x++) {
        const dx = x - cx;
        const dy = y - cy;
        const q = r * r - dx * dx - dy * dy;
        if (q < 0) continue;
        const dz = Math.sqrt(q);
        const z = cz + dz;
        const i = y * W + x;
        if (z > zb[i]) {
          zb[i] = z;
          nb[i * 3] = dx / r;
          nb[i * 3 + 1] = -dy / r;
          nb[i * 3 + 2] = dz / r;
          cb[i] = mat;
        }
      }
  };
  draw(sphere);
  return { zb, nb, cb };
}
function bakeRope(name, W, H, palette, draw) {
  const { zb, nb, cb } = sweep(W, H, draw);
  const out = Buffer.alloc(W * H * 4);
  for (let i = 0; i < W * H; i++) {
    if (zb[i] < -1e8) continue;
    const c = shade(nb[i * 3], nb[i * 3 + 1], nb[i * 3 + 2], 0, 0, 1, palette[cb[i]], 1);
    out[i * 4] = tone(c[0]);
    out[i * 4 + 1] = tone(c[1]);
    out[i * 4 + 2] = tone(c[2]);
    out[i * 4 + 3] = 255;
  }
  writePNG(name, W, H, out);
}
const cubic = (p0, p1, p2, p3, t) => {
  const u = 1 - t;
  return p0.map((_, i) => u * u * u * p0[i] + 3 * u * u * t * p1[i] + 3 * u * t * t * p2[i] + t * t * t * p3[i]);
};
function along(curves, cb, density = 1.2) {
  for (const c of curves) {
    const n = Math.ceil(c.len * density);
    for (let i = 0; i <= n; i++) cb(...cubic(c.p[0], c.p[1], c.p[2], c.p[3], i / n), i / n);
  }
}

const S = 4; // 800x160 design units rendered at 3200x640, downscaled afterwards
const RED = hex('#D5102F');
const WHITE = hex('#F4EEEE');
const u = (x, y, z = 0) => [x * S, y * S, z * S];
const rope = (sphere, curves, r, mat = 0, zfn = () => 0) =>
  along(curves, (x, y, z, t) => sphere(x, y, z + zfn(t) * S, r * S, mat), 1.4);
const crv = (a, b, c, d, len = 500) => ({ p: [u(...a), u(...b), u(...c), u(...d)], len: len * S });

const THREADS = {
  taut: (s) => {
    rope(s, [crv([12, 80], [130, 28], [270, 132], [400, 80]), crv([400, 80], [530, 28], [670, 132], [788, 80])], 9);
  },
  reach: (s) => {
    rope(s, [crv([12, 80], [120, 26], [220, 134], [340, 84]), crv([340, 84], [400, 62], [440, 62], [486, 80])], 9);
    s(492 * S, 81 * S, 2 * S, 17 * S, 1);
    [[540, 92, 8], [590, 96, 7], [640, 82, 6], [690, 66, 5], [740, 66, 4], [785, 78, 3]].forEach(([x, y, r]) => s(x * S, y * S, 0, r * S, 0));
  },
  wait: (s) => {
    const c = [crv([14, 80], [130, 28], [270, 132], [400, 80]), crv([400, 80], [530, 28], [670, 132], [786, 80])];
    for (const q of c)
      for (let i = 0; i <= 8; i++) {
        const [x, y] = cubic(q.p[0], q.p[1], q.p[2], q.p[3], i / 8);
        s(x, y, 0, (i % 2 ? 8.5 : 13) * S, i % 2 ? 1 : 0);
      }
  },
  cut: (s) => {
    rope(s, [crv([12, 80], [90, 34], [190, 118], [296, 92])], 9);
    rope(s, [crv([788, 80], [710, 126], [610, 42], [504, 68])], 9);
    rope(s, [crv([296, 92], [306, 88], [316, 80], [330, 74], 60)], 3.2);
    rope(s, [crv([296, 92], [310, 92], [322, 96], [338, 96], 60)], 3.2);
    rope(s, [crv([296, 92], [306, 100], [314, 108], [326, 114], 60)], 3.2);
    rope(s, [crv([504, 68], [494, 72], [484, 80], [470, 86], 60)], 3.2);
    rope(s, [crv([504, 68], [490, 68], [478, 64], [462, 64], 60)], 3.2);
    rope(s, [crv([504, 68], [494, 60], [486, 52], [474, 46], 60)], 3.2);
  },
  knot: (s) => {
    rope(s, [crv([12, 80], [130, 28], [260, 132], [358, 90])], 9);
    rope(s, [crv([442, 90], [540, 132], [670, 28], [788, 80])], 9);
    // A soft overhand knot: the strand dips behind itself so the crossings read as depth.
    const c = [crv([358, 90], [385, 30], [450, 30], [445, 80], 180), crv([445, 80], [440, 128], [372, 122], [372, 78], 180), crv([372, 78], [372, 34], [440, 40], [442, 90], 180)];
    along(c.slice(0, 1), (x, y, z, t) => s(x, y, z + mix(0, 16, t) * S, 11 * S), 1.4);
    along(c.slice(1, 2), (x, y, z, t) => s(x, y, z + mix(16, -16, t) * S, 11 * S), 1.4);
    along(c.slice(2, 3), (x, y, z, t) => s(x, y, z + mix(-16, 12, t) * S, 11 * S), 1.4);
  },
};

function renderThreads() {
  for (const [k, fn] of Object.entries(THREADS)) bakeRope(`thread-${k}`, 800 * S, 160 * S, [RED, WHITE], fn);
  // Long ribbon that runs along the bottom of hero panels.
  const LW = 1536 * 2;
  const LH = 90 * 2;
  bakeRope('thread-ribbon', LW, LH, [RED, WHITE], (s) => {
    along([{ p: [[-40, 110, 0], [480, 10, 0], [840, 170, 0], [1500, 70, 0]], len: 1600 }, { p: [[1500, 70, 0], [2100, -20, 0], [2500, 150, 0], [LW + 40, 80, 0]], len: 1600 }], (x, y, z) => s(x, y, z, 15), 1.4);
  });
}

// ---------- SDF helpers ----------
const smin = (a, b, k) => {
  const h = Math.max(k - Math.abs(a - b), 0) / k;
  return Math.min(a, b) - h * h * k * 0.25;
};
const sph = (x, y, z, cx, cy, cz, r) => Math.hypot(x - cx, y - cy, z - cz) - r;
const ell = (x, y, z, cx, cy, cz, rx, ry, rz) => {
  const px = x - cx;
  const py = y - cy;
  const pz = z - cz;
  const k0 = Math.hypot(px / rx, py / ry, pz / rz);
  const k1 = Math.hypot(px / (rx * rx), py / (ry * ry), pz / (rz * rz));
  return k1 === 0 ? -Math.min(rx, ry, rz) : (k0 * (k0 - 1)) / k1;
};
const cap = (x, y, z, ax, ay, az, bx, by, bz, r) => {
  const pax = x - ax;
  const pay = y - ay;
  const paz = z - az;
  const bax = bx - ax;
  const bay = by - ay;
  const baz = bz - az;
  const h = clamp((pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz));
  return Math.hypot(pax - bax * h, pay - bay * h, paz - baz * h) - r;
};

// ---------- Avatars: soft mochi characters ----------
const COL = { red: hex('#D5102F'), deep: hex('#8C0A20'), white: hex('#F6F1F1'), black: hex('#1A1416'), eye: hex('#120C0E'), blush: hex('#FF6B7D'), redBright: hex('#F0213F') };

const ACC = {
  moon: (x, y, z) => Math.max(sph(x, y, z, 0.1, 0.98, 0.12, 0.36), -sph(x, y, z, 0.21, 1.07, 0.12, 0.31)) - 0.015,
  sun: (x, y, z) => {
    let d = sph(x, y, z, 0.06, 0.84, 0.1, 0.22);
    for (let i = 0; i < 8; i++) {
      const a = (i / 8) * Math.PI * 2;
      d = smin(d, sph(x, y, z, 0.06 + Math.cos(a) * 0.34, 0.84 + Math.sin(a) * 0.34, 0.1, 0.085), 0.03);
    }
    return d;
  },
  note: (x, y, z) => {
    let d = ell(x, y, z, 0.02, 0.76, 0.28, 0.17, 0.13, 0.13);
    d = smin(d, cap(x, y, z, 0.16, 0.78, 0.28, 0.16, 1.22, 0.28, 0.045), 0.04);
    d = smin(d, cap(x, y, z, 0.16, 1.22, 0.28, 0.42, 1.08, 0.28, 0.05), 0.05);
    return d;
  },
  wave: (x, y, z) => {
    let d = 1e9;
    const pts = [[-0.46, 0.6, 0.12, 0.16], [-0.24, 0.78, 0.14, 0.18], [0.04, 0.92, 0.16, 0.19], [0.3, 0.92, 0.16, 0.17], [0.46, 0.76, 0.16, 0.13], [0.36, 0.62, 0.2, 0.09]];
    for (const p of pts) d = smin(d, sph(x, y, z, p[0], p[1], p[2], p[3]), 0.2);
    return d;
  },
  leaf: (x, y, z) => {
    let d = cap(x, y, z, 0, 0.62, 0.05, 0.02, 0.92, 0.08, 0.045);
    const lobe = (sx) => {
      const px = (x - 0.02) * sx;
      const py = y - 1.02;
      const a = 0.6;
      const rx = px * Math.cos(a) + py * Math.sin(a);
      const ry = -px * Math.sin(a) + py * Math.cos(a);
      return ell(rx, ry, z, 0.2, 0, 0.08, 0.26, 0.12, 0.09);
    };
    d = smin(d, lobe(1), 0.05);
    d = smin(d, lobe(-1), 0.05);
    return d;
  },
  star: (x, y, z) => {
    let d = 1e9;
    const cx = 0.06;
    const cy = 0.98;
    for (let i = 0; i < 5; i++) {
      const a = Math.PI / 2 + (i / 5) * Math.PI * 2;
      d = smin(d, cap(x, y, z, cx, cy, 0.12, cx + Math.cos(a) * 0.32, cy + Math.sin(a) * 0.32, 0.12, 0.085), 0.09);
    }
    return d;
  },
};

const CHARS = [
  { name: 'av-dawn', body: COL.red, acc: 'moon', accCol: COL.white },
  { name: 'av-slow', body: COL.white, acc: 'sun', accCol: COL.redBright },
  { name: 'av-jazz', body: COL.black, acc: 'note', accCol: COL.white, faceLight: true },
  { name: 'av-sea', body: COL.white, acc: 'wave', accCol: COL.redBright },
  { name: 'av-forest', body: COL.deep, acc: 'leaf', accCol: COL.white },
  { name: 'av-star', body: COL.black, acc: 'star', accCol: COL.redBright, faceLight: true },
];

function renderAvatar(ch) {
  const N = 1024;
  const out = Buffer.alloc(N * N * 4);
  const acc = ACC[ch.acc];
  const body = (x, y, z) => smin(ell(x, y, z, 0, -0.18, 0, 0.95, 0.82, 0.86), ell(x, y, z, 0, -0.46, 0, 1.04, 0.54, 0.9), 0.28);
  const mouth = (x, y, z) => {
    let d = 1e9;
    for (let i = 0; i <= 8; i++) {
      const a = mix(-1.05, 1.05, i / 8);
      d = Math.min(d, sph(x, y, z, Math.sin(a) * 0.15, -0.3 - Math.cos(a) * 0.09, 0.975, 0.03));
    }
    return d;
  };
  const eyes = (x, y, z) => Math.min(ell(x, y, z, -0.34, -0.1, 0.83, 0.09, 0.115, 0.06), ell(x, y, z, 0.34, -0.1, 0.83, 0.09, 0.115, 0.06));
  const map = (x, y, z) => Math.min(body(x, y, z), acc(x, y, z), eyes(x, y, z), mouth(x, y, z));
  const ro = [0, 0.2, 5.2];
  for (let py = 0; py < N; py++)
    for (let px = 0; px < N; px++) {
      const ux = ((px + 0.5) / N - 0.5) * 2;
      const uy = -(((py + 0.5) / N - 0.5) * 2);
      let dx = ux * 0.3;
      let dy = uy * 0.3 - 0.004;
      let dz = -1;
      const l = Math.hypot(dx, dy, dz);
      dx /= l;
      dy /= l;
      dz /= l;
      let t = 2.4;
      let hit = false;
      for (let s = 0; s < 70 && t < 8; s++) {
        const d = map(ro[0] + dx * t, ro[1] + dy * t, ro[2] + dz * t);
        if (d < 0.0012) {
          hit = true;
          break;
        }
        t += d * 0.95;
      }
      if (!hit) continue;
      const x = ro[0] + dx * t;
      const y = ro[1] + dy * t;
      const z = ro[2] + dz * t;
      const h = 0.002;
      let nx = map(x + h, y, z) - map(x - h, y, z);
      let ny = map(x, y + h, z) - map(x, y - h, z);
      let nz = map(x, y, z + h) - map(x, y, z - h);
      const nl = Math.hypot(nx, ny, nz) || 1;
      nx /= nl;
      ny /= nl;
      nz /= nl;
      const dB = body(x, y, z);
      const dA = acc(x, y, z);
      const dE = eyes(x, y, z);
      const dM = mouth(x, y, z);
      const m = Math.min(dB, dA, dE, dM);
      let base = ch.body;
      let gloss = ch.faceLight ? 0.28 : ch.body === COL.white ? 0.3 : 0.32;
      if (m === dA) {
        base = ch.accCol;
        gloss = 1;
      } else if (m === dE) {
        base = ch.faceLight ? COL.white : COL.eye;
        gloss = 1.4;
      } else if (m === dM) {
        base = ch.faceLight ? COL.white : COL.eye;
        gloss = 0.3;
      } else {
        const blush = Math.max(smooth(0.2, 0.03, Math.hypot(x + 0.58, y + 0.3)), smooth(0.2, 0.03, Math.hypot(x - 0.58, y + 0.3)));
        const amt = blush * (ch.faceLight ? 0.3 : 0.7);
        if (blush > 0 && z > 0) base = [mix(base[0], COL.blush[0], amt), mix(base[1], COL.blush[1], amt), mix(base[2], COL.blush[2], amt)];
      }
      const ao = clamp(map(x + nx * 0.12, y + ny * 0.12, z + nz * 0.12) / 0.12, 0.35, 1);
      const c = shade(nx, ny, nz, -dx, -dy, -dz, base, gloss, ao);
      const i = (py * N + px) * 4;
      out[i] = tone(c[0]);
      out[i + 1] = tone(c[1]);
      out[i + 2] = tone(c[2]);
      out[i + 3] = 255;
    }
  writePNG(ch.name, N, N, out);
}

// ---------- Key visual: a glass bubble holding a glossy red heart over a mirror floor ----------
const heartD = (x, y, z) => {
  const ax = Math.abs(x);
  const zz = z / 0.74;
  let d = sph(ax, y, zz, 0.44, 0.3, 0, 0.56);
  for (let i = 0; i < 7; i++) {
    const t = i / 6;
    d = smin(d, sph(x, y, zz, 0, mix(0.08, -0.9, t), 0, mix(0.68, 0.035, Math.pow(t, 0.8))), 0.16);
  }
  return d * 0.72;
};
const BC = [0, 1.62, 0];
const BR = 1.22;
const hash = (n) => {
  const s = Math.sin(n * 127.1) * 43758.5453;
  return s - Math.floor(s);
};
function sky(dx, dy, dz, out) {
  const e = dy;
  const hor = Math.exp(-Math.abs(e) * 26);
  let r = 0.003 + 0.13 * hor;
  let g = 0.001 + 0.004 * hor;
  let b = 0.002 + 0.01 * hor;
  // Soft light shafts from the upper left.
  const az = Math.atan2(dx, -dz);
  const shaft = Math.pow(Math.max(0, Math.sin(az * 9 + e * 5 + 1.2)), 6) * smooth(0.05, 0.5, e) * smooth(0.1, -0.5, az);
  r += 0.05 * shaft;
  g += 0.002 * shaft;
  b += 0.005 * shaft;
  // Bokeh lights along the horizon.
  const cell = Math.floor(az * 110);
  for (let k = cell - 1; k <= cell + 1; k++) {
    const hb = hash(k + 3.1);
    if (hb < 0.45) continue;
    const ca = (k + 0.5 + (hash(k + 9.7) - 0.5) * 0.5) / 110;
    const ce = 0.008 + hash(k + 5.3) * 0.042;
    const rad = 0.0022 + hash(k + 1.9) * 0.0032;
    const v = smooth(rad, rad * 0.55, Math.hypot(az - ca, e - ce));
    const white = hash(k + 7.7) > 0.72;
    r += v * (white ? 0.5 : 0.6);
    g += v * (white ? 0.42 : 0.03);
    b += v * (white ? 0.42 : 0.05);
  }
  out[0] = r;
  out[1] = g;
  out[2] = b;
}
const TMP = [0, 0, 0];
function heartHit(ox, oy, oz, dx, dy, dz, out) {
  const scale = 0.62;
  const cx = BC[0];
  const cy = BC[1] + 0.02;
  const cz = BC[2];
  const bx = ox - cx;
  const by = oy - cy;
  const bz = oz - cz;
  const b = bx * dx + by * dy + bz * dz;
  const disc = b * b - (bx * bx + by * by + bz * bz - (scale * 1.25) ** 2);
  if (disc < 0) return false;
  let t = Math.max(0, -b - Math.sqrt(disc));
  const tEnd = -b + Math.sqrt(disc);
  const f = (x, y, z) => heartD((x - cx) / scale, (y - cy) / scale, (z - cz) / scale) * scale;
  for (let i = 0; i < 90 && t < tEnd; i++) {
    const x = ox + dx * t;
    const y = oy + dy * t;
    const z = oz + dz * t;
    const d = f(x, y, z);
    if (d < 0.0008) {
      const h = 0.004;
      let nx = f(x + h, y, z) - f(x - h, y, z);
      let ny = f(x, y + h, z) - f(x, y - h, z);
      let nz = f(x, y, z + h) - f(x, y, z - h);
      const l = Math.hypot(nx, ny, nz) || 1;
      const c = shade(nx / l, ny / l, nz / l, -dx, -dy, -dz, COL.red, 0.8);
      out[0] = c[0];
      out[1] = c[1];
      out[2] = c[2];
      return true;
    }
    t += d;
  }
  return false;
}
function bubbleTrace(ox, oy, oz, dx, dy, dz, out, depth = 0) {
  const bx = ox - BC[0];
  const by = oy - BC[1];
  const bz = oz - BC[2];
  const b = bx * dx + by * dy + bz * dz;
  const disc = b * b - (bx * bx + by * by + bz * bz - BR * BR);
  if (disc > 0 && -b - Math.sqrt(disc) > 0) {
    const t = -b - Math.sqrt(disc);
    const x = ox + dx * t;
    const y = oy + dy * t;
    const z = oz + dz * t;
    const nx = (x - BC[0]) / BR;
    const ny = (y - BC[1]) / BR;
    const nz = (z - BC[2]) / BR;
    const ndv = Math.max(0, -(nx * dx + ny * dy + nz * dz));
    const F = 0.05 + 0.95 * Math.pow(1 - ndv, 3.2);
    const k = 2 * ndv;
    const e = env(k * nx + dx, k * ny + dy, k * nz + dz);
    const er = e[0];
    const eg = e[1];
    const eb = e[2];
    // Thin shell: rays bend only near the rim.
    const bend = Math.pow(1 - ndv, 2) * 0.32;
    let rx = dx + nx * bend;
    let ry = dy + ny * bend;
    let rz = dz + nz * bend;
    const l = Math.hypot(rx, ry, rz);
    rx /= l;
    ry /= l;
    rz /= l;
    const inner = [0, 0, 0];
    if (!heartHit(x, y, z, rx, ry, rz, inner)) sceneNoBubble(x, y, z, rx, ry, rz, inner, depth);
    const rim = Math.pow(1 - ndv, 5);
    out[0] = inner[0] * (1 - F * 0.6) + er * F * 0.34 + rim * 1.0;
    out[1] = inner[1] * (1 - F * 0.6) + eg * F * 0.34 + rim * 0.12;
    out[2] = inner[2] * (1 - F * 0.6) + eb * F * 0.34 + rim * 0.18;
    return;
  }
  sceneNoBubble(ox, oy, oz, dx, dy, dz, out, depth);
}
function sceneNoBubble(ox, oy, oz, dx, dy, dz, out, depth) {
  if (dy < -1e-4 && oy > 0) {
    const t = -oy / dy;
    const x = ox + dx * t;
    const z = oz + dz * t;
    const rr = Math.hypot(x, z);
    const fres = 0.05 + 0.3 * Math.pow(1 + dy, 10);
    let r = 0;
    let g = 0;
    let b = 0;
    if (depth < 1) {
      const R = [0, 0, 0];
      bubbleTrace(x, 0.0001, z, dx, -dy, dz, R, depth + 1);
      const fade = Math.exp(-Math.max(0, z - 0.3) * 1.5);
      r = R[0] * fres * fade;
      g = R[1] * fres * fade;
      b = R[2] * fres * fade;
    }
    // Glowing rings under the bubble.
    const ring = (R0, w, a) => a * Math.exp(-(((rr - R0) / w) ** 2));
    const halo = ring(1.05, 0.035, 1.5) + ring(1.26, 0.016, 0.9) + ring(0.8, 0.012, 0.4) + ring(1.1, 0.34, 0.12);
    r += halo * 1.15;
    g += halo * 0.04 + ring(1.05, 0.012, 1.1) * 0.7;
    b += halo * 0.07 + ring(1.05, 0.012, 1.1) * 0.7;
    out[0] = r + 0.0015;
    out[1] = g + 0.0006;
    out[2] = b + 0.001;
    return;
  }
  sky(dx, dy, dz, out);
}

function renderKV() {
  const N = 2048;
  const out = Buffer.alloc(N * N * 4);
  const ro = [0, 1.32, 8.1];
  const col = [0, 0, 0];
  // Neon threads in image space: a restless line from the left and a calm one from the right.
  const LV = 0.305;
  const lineL = (u) => {
    const t = Math.min(1, u / 0.5);
    return LV + Math.sin(t * 34) * 0.05 * Math.pow(1 - t, 1.4) * (0.65 + 0.35 * Math.sin(t * 11));
  };
  for (let py = 0; py < N; py++) {
    for (let px = 0; px < N; px++) {
      const u0 = (px + 0.5) / N;
      const v0 = (py + 0.5) / N;
      let dx = (u0 - 0.5) * 0.92;
      let dy = -(v0 - 0.5) * 0.92 - 0.16;
      let dz = -1.42;
      const l = Math.hypot(dx, dy, dz);
      dx /= l;
      dy /= l;
      dz /= l;
      bubbleTrace(ro[0], ro[1], ro[2], dx, dy, dz, col);
      let r = col[0];
      let g = col[1];
      let b = col[2];
      if (Math.abs(v0 - LV) < 0.16) {
        const f = u0 < 0.5 ? lineL(u0) : LV;
        const slope = u0 < 0.5 ? (lineL(u0 + 0.001) - lineL(u0 - 0.001)) / 0.002 : 0;
        const dl = Math.abs(v0 - f) / Math.sqrt(1 + slope * slope);
        const core = Math.exp(-((dl / 0.0018) ** 2));
        const glow = Math.exp(-dl / 0.011) * 0.42;
        const hd = Math.hypot((u0 - 0.5) / 0.13, (v0 - LV) / 0.13);
        const mask = smooth(0.7, 1, hd);
        r += (core * 3 + glow * 0.9) * mask;
        g += (core * 1.8 + glow * 0.03) * mask;
        b += (core * 1.8 + glow * 0.06) * mask;
      }
      const vig = 1 - 0.55 * smooth(0.5, 1.05, v0);
      const i = (py * N + px) * 4;
      out[i] = tone(r * vig);
      out[i + 1] = tone(g * vig);
      out[i + 2] = tone(b * vig);
      out[i + 3] = 255;
    }
    if (py % 400 === 0) console.log('kv row', py);
  }
  writePNG('kv', N, N, out);
}

if (ONLY === 'all' || ONLY === 'threads') renderThreads();
if (ONLY === 'all' || ONLY === 'avatars') CHARS.forEach(renderAvatar);
if (ONLY === 'all' || ONLY === 'kv') renderKV();
