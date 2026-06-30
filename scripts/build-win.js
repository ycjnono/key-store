/**
 * Windows 打包：在系统临时目录构建，避免 Cursor 占用 workspace/dist 文件句柄
 */
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const RELEASE = path.join(ROOT, 'release');
const TEMP_OUT = path.join(os.tmpdir(), 'keystore-electron-build');

function stopKeyStore() {
  if (process.platform !== 'win32') return;
  try {
    execSync('taskkill /F /IM Key-Store.exe', { stdio: 'ignore' });
  } catch {
    // ignore
  }
}

function rmSafe(target) {
  if (!fs.existsSync(target)) return;
  fs.rmSync(target, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}

function copyInstallers(srcDir, destDir) {
  fs.mkdirSync(destDir, { recursive: true });
  const files = fs.readdirSync(srcDir).filter((f) => f.endsWith('.exe') || f.endsWith('.blockmap'));
  if (!files.length) {
    throw new Error(`No installer found in ${srcDir}`);
  }
  for (const file of files) {
    const from = path.join(srcDir, file);
    const to = path.join(destDir, file);
    fs.copyFileSync(from, to);
    console.log(`Copied: release/${file}`);
  }
}

stopKeyStore();
rmSafe(TEMP_OUT);
fs.mkdirSync(RELEASE, { recursive: true });

const outputArg = TEMP_OUT.replace(/\\/g, '/');
console.log(`Building in temp: ${TEMP_OUT}`);

execSync(`npx electron-builder --win --config.directories.output="${outputArg}"`, {
  cwd: ROOT,
  stdio: 'inherit',
  env: process.env,
});

copyInstallers(TEMP_OUT, RELEASE);
rmSafe(TEMP_OUT);

console.log('Build complete. Output: release/');
