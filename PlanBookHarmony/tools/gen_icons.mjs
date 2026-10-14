// 生成应用图标 PNG（无三方依赖：手写 PNG 编码 + zlib）。
// 用法：node tools/gen_icons.mjs  → 覆盖 AppScope 与 entry 的图标资源。
import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function png(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter none
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body) >>> 0);
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

let crcTable = null;
function crc32(buf) {
  if (!crcTable) {
    crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c >>> 0;
    }
  }
  let c = 0xffffffff;
  for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function mix(a, b, t) { return a + (b - a) * t; }

/** 圆角矩形掩膜 */
function inRoundRect(x, y, w, h, r) {
  if (x < 0 || y < 0 || x >= w || y >= h) return false;
  const cx = Math.min(Math.max(x, r), w - r);
  const cy = Math.min(Math.max(y, r), h - r);
  const dx = x - cx, dy = y - cy;
  return dx * dx + dy * dy <= r * r;
}

/** 画布：靛蓝→紫渐变底 + 白色对勾 + 底部三枚色点（呼应子计划本调色板） */
function draw(size) {
  const px = Buffer.alloc(size * size * 4);
  const grad = [[0x7C, 0x9E, 0xF9], [0x7A, 0x6C, 0xF0]]; // #7C9EF9 → #7A6CF0
  // 对勾两段线段的采样函数（归一化坐标）
  const seg = (x, y, x1, y1, x2, y2, w) => {
    const vx = x2 - x1, vy = y2 - y1;
    const t = Math.max(0, Math.min(1, (x - x1) * vx + (y - y1) * vy) / (vx * vx + vy * vy));
    const dx = x - (x1 + t * vx), dy = y - (y1 + t * vy);
    return Math.sqrt(dx * dx + dy * dy) <= w;
  };
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4;
      if (!inRoundRect(x, y, size, size, size * 0.22)) { px[i + 3] = 0; continue; }
      const t = y / size;
      px[i] = Math.round(mix(grad[0][0], grad[1][0], t));
      px[i + 1] = Math.round(mix(grad[0][1], grad[1][1], t));
      px[i + 2] = Math.round(mix(grad[0][2], grad[1][2], t));
      px[i + 3] = 255;
      const nx = x / size, ny = y / size;
      const mark = seg(nx, ny, 0.28, 0.52, 0.44, 0.68, 0.055) || seg(nx, ny, 0.44, 0.68, 0.74, 0.34, 0.055);
      if (mark) { px[i] = 255; px[i + 1] = 255; px[i + 2] = 255; }
    }
  }
  return px;
}

const targets = [
  [resolve(root, 'AppScope/resources/base/media/app_icon.png'), 216],
  [resolve(root, 'entry/src/main/resources/base/media/app_icon.png'), 216],
  [resolve(root, 'entry/src/main/resources/base/media/start_icon.png'), 216],
  [resolve(root, 'entry/src/main/resources/base/media/background.png'), 216],
];
for (const [path, size] of targets) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, png(size, size, draw(size)));
  console.log('wrote', path);
}
