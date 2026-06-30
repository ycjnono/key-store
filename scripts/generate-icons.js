/**
 * 生成 Key-Store 托盘/应用图标
 * 运行: node scripts/generate-icons.js
 */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const OUT_DIR = path.join(__dirname, '../assets');

/** 主题蓝 #2563eb */
const BLUE = { r: 37, g: 99, b: 235, a: 255 };
const WHITE = { r: 255, g: 255, b: 255, a: 255 };

/**
 * 32x32 蓝色圆角底 + 白色钥匙圆头与柄（高对比，托盘易识别）
 */
function sample32(x, y) {
  const dx = x - 15.5;
  const dy = y - 15.5;
  const inBg = Math.abs(dx) <= 13 && Math.abs(dy) <= 13 && Math.abs(dx) + Math.abs(dy) * 0.55 <= 15;
  if (!inBg) return { r: 0, g: 0, b: 0, a: 0 };

  const head = (x - 19) ** 2 + (y - 12) ** 2 <= 16;
  const stem = x >= 10 && x <= 13 && y >= 12 && y <= 22;
  const teeth = (x >= 13 && x <= 16 && y >= 19 && y <= 21)
    || (x >= 16 && x <= 18 && y >= 17 && y <= 21);

  return (head || stem || teeth) ? WHITE : BLUE;
}

function crc32(buf) {
  let c = 0xffffffff;
  const table = crc32.table || (crc32.table = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let v = n;
      for (let k = 0; k < 8; k++) v = (v & 1) ? (0xedb88320 ^ (v >>> 1)) : (v >>> 1);
      t[n] = v >>> 0;
    }
    return t;
  })());
  for (let i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crcData = Buffer.concat([typeBuf, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(crcData), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}

function buildPng(size, sampler) {
  const rowSize = size * 4 + 1;
  const raw = Buffer.alloc(rowSize * size);
  for (let y = 0; y < size; y++) {
    const rowStart = y * rowSize;
    raw[rowStart] = 0;
    for (let x = 0; x < size; x++) {
      const sx = Math.floor((x + 0.5) * 32 / size);
      const sy = Math.floor((y + 0.5) * 32 / size);
      const c = sampler(Math.min(31, sx), Math.min(31, sy));
      const i = rowStart + 1 + x * 4;
      raw[i] = c.r;
      raw[i + 1] = c.g;
      raw[i + 2] = c.b;
      raw[i + 3] = c.a;
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function buildIco(pngList) {
  const headerSize = 6;
  const entrySize = 16;
  const offsetStart = headerSize + entrySize * pngList.length;
  let cursor = offsetStart;
  const entries = pngList.map(({ size, png }) => {
    const entry = { size, png, offset: cursor };
    cursor += png.length;
    return entry;
  });

  const buf = Buffer.alloc(cursor);
  buf.writeUInt16LE(0, 0);
  buf.writeUInt16LE(1, 2);
  buf.writeUInt16LE(entries.length, 4);

  entries.forEach((entry, idx) => {
    const base = headerSize + idx * entrySize;
    const dim = entry.size >= 256 ? 0 : entry.size;
    buf[base] = dim;
    buf[base + 1] = dim;
    buf[base + 2] = 0;
    buf[base + 3] = 0;
    buf.writeUInt16LE(1, base + 4);
    buf.writeUInt16LE(32, base + 6);
    buf.writeUInt32LE(entry.png.length, base + 8);
    buf.writeUInt32LE(entry.offset, base + 12);
    entry.png.copy(buf, entry.offset);
  });

  return buf;
}

fs.mkdirSync(OUT_DIR, { recursive: true });

const png32 = buildPng(32, sample32);
const png16 = buildPng(16, sample32);
const png256 = buildPng(256, sample32);
const ico = buildIco([
  { size: 16, png: png16 },
  { size: 32, png: png32 },
  { size: 48, png: buildPng(48, sample32) },
  { size: 256, png: png256 },
]);

fs.writeFileSync(path.join(OUT_DIR, 'tray-icon.png'), png32);
fs.writeFileSync(path.join(OUT_DIR, 'tray-icon-16.png'), png16);
fs.writeFileSync(path.join(OUT_DIR, 'app-icon.png'), png256);
fs.writeFileSync(path.join(OUT_DIR, 'tray-icon.ico'), ico);
fs.writeFileSync(path.join(OUT_DIR, 'app-icon.ico'), ico);

console.log('Generated: tray-icon.ico', ico.length, 'bytes, app-icon.png', png256.length, 'bytes');
