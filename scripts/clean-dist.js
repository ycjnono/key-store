/**
 * 清理项目内 release/ 与 dist/（若存在）
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const TARGETS = ['release', 'dist'].map((name) => path.join(ROOT, name));

function stopKeyStoreProcesses() {
  if (process.platform !== 'win32') return;
  try {
    execSync('taskkill /F /IM Key-Store.exe', { stdio: 'ignore' });
  } catch {
    // ignore
  }
}

function removeDir(target) {
  if (!fs.existsSync(target)) return false;
  fs.rmSync(target, {
    recursive: true,
    force: true,
    maxRetries: 5,
    retryDelay: 200,
  });
  return true;
}

stopKeyStoreProcesses();

let failed = false;
for (const dir of TARGETS) {
  if (!fs.existsSync(dir)) continue;
  const stale = `${dir}-stale-${Date.now()}`;
  try {
    fs.renameSync(dir, stale);
    try {
      removeDir(stale);
    } catch (err) {
      console.warn(`[WARN] Could not remove ${path.basename(stale)}: ${err.message}`);
    }
    console.log(`Cleaned ${path.basename(dir)}/`);
  } catch (err) {
    console.warn(`[WARN] Skip ${path.basename(dir)}/ (${err.message})`);
    failed = true;
  }
}

if (failed) {
  console.warn('Some folders are locked by Cursor or a running app.');
  console.warn('Build will use TEMP directory and write to release/ automatically.');
}
