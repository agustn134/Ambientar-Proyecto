import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) !== 0 ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (let i = 0; i < buffer.length; i++) {
    crc = CRC_TABLE[(crc ^ buffer[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function makeChunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const lenBuf = Buffer.alloc(4);
  lenBuf.writeUInt32BE(data.length, 0);
  const crcInput = Buffer.concat([typeBuf, data]);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(crcInput), 0);
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function pointInRoundedRect(x, y, left, top, right, bottom, radius) {
  if (x < left || x > right || y < top || y > bottom) return false;
  const cx = Math.max(left + radius, Math.min(x, right - radius));
  const cy = Math.max(top + radius, Math.min(y, bottom - radius));
  const dx = x - cx;
  const dy = y - cy;
  return dx * dx + dy * dy <= radius * radius;
}

function distToSegment(px, py, ax, ay, bx, by) {
  const vx = bx - ax;
  const vy = by - ay;
  const wx = px - ax;
  const wy = py - ay;
  const c1 = vx * wx + vy * wy;
  if (c1 <= 0) return Math.hypot(px - ax, py - ay);
  const c2 = vx * vx + vy * vy;
  if (c2 <= c1) return Math.hypot(px - bx, py - by);
  const b = c1 / c2;
  return Math.hypot(px - (ax + b * vx), py - (ay + b * vy));
}

function renderIconPng(size) {
  const raw = Buffer.alloc(size * (1 + size * 4));
  const s = size;
  const radius = s * 0.22;
  const pad = s * 0.04;
  const innerPad = s * 0.11;

  for (let y = 0; y < s; y++) {
    const rowStart = y * (1 + s * 4);
    raw[rowStart] = 0;
    for (let x = 0; x < s; x++) {
      const px = x + 0.5;
      const py = y + 0.5;
      const idx = rowStart + 1 + x * 4;

      if (!pointInRoundedRect(px, py, pad, pad, s - pad, s - pad, radius)) {
        raw[idx] = 0;
        raw[idx + 1] = 0;
        raw[idx + 2] = 0;
        raw[idx + 3] = 0;
        continue;
      }

      // Base primary #17449E with deep #103378 rim
      let r = 0x17;
      let g = 0x44;
      let b = 0x9e;

      if (!pointInRoundedRect(px, py, innerPad, innerPad, s - innerPad, s - innerPad, radius * 0.72)) {
        r = 0x10;
        g = 0x33;
        b = 0x78;
      }

      // Escudo blanco central y marca de verificación GPago
      const nx = (px - s * 0.5) / (s * 0.34);
      const ny = (py - s * 0.5) / (s * 0.34);
      const shieldOuter = Math.abs(nx) <= 0.78 && ny >= -0.78 && ny <= (0.88 - Math.abs(nx) * 0.45);
      const shieldInner = Math.abs(nx) <= 0.60 && ny >= -0.60 && ny <= (0.70 - Math.abs(nx) * 0.45);

      if (shieldOuter && !shieldInner) {
        r = 0xff;
        g = 0xff;
        b = 0xff;
      }

      const d1 = distToSegment(px, py, s * 0.34, s * 0.51, s * 0.46, s * 0.63);
      const d2 = distToSegment(px, py, s * 0.46, s * 0.63, s * 0.67, s * 0.39);
      if (Math.min(d1, d2) <= s * 0.055) {
        r = 0xdc;
        g = 0xfc;
        b = 0xe7;
      }

      raw[idx] = r;
      raw[idx + 1] = g;
      raw[idx + 2] = b;
      raw[idx + 3] = 0xff;
    }
  }

  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // 8-bit
  ihdr[9] = 6; // RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const compressed = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([
    signature,
    makeChunk('IHDR', ihdr),
    makeChunk('IDAT', compressed),
    makeChunk('IEND', Buffer.alloc(0)),
  ]);
}

fs.writeFileSync(path.join(__dirname, 'icon-192.png'), renderIconPng(192));
fs.writeFileSync(path.join(__dirname, 'icon-512.png'), renderIconPng(512));
console.log('Generated icon-192.png and icon-512.png');
