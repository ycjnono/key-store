/**
 * 构建前从 logo.png 生成各尺寸图标，并复制到 build/icon.ico（electron-builder 标准路径）
 *
 * 调用方: package.json prebuild、build-exe.bat
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'assets');
const BUILD = path.join(ROOT, 'build');
const LOGO = path.join(ASSETS, 'logo.png');
const APP_ICO = path.join(ASSETS, 'app-icon.ico');
const BUILD_ICO = path.join(BUILD, 'icon.ico');

/** 最小有效 ICO 体积（过小通常缺少 256x256，会导致安装包使用默认 Electron 图标） */
const MIN_ICO_BYTES = 10000;

/**
 * 若存在 logo.png，调用 Python 脚本从原图导出 app-icon.ico 等文件
 * @returns {boolean} 是否成功执行 Python 导出
 */
function runProcessLogo() {
  const script = path.join(ROOT, 'scripts', 'process-logo.py');
  if (!fs.existsSync(LOGO)) {
    console.log('[prepare-icons] assets/logo.png 不存在，使用现有 app-icon.ico');
    return false;
  }
  try {
    execSync(`python "${script}"`, { cwd: ROOT, stdio: 'inherit' });
    return true;
  } catch (err) {
    console.warn(`[prepare-icons] 无法运行 process-logo.py: ${err.message}`);
    console.warn('[prepare-icons] 请安装 Python 与 Pillow，或手动执行: python scripts/process-logo.py');
    return false;
  }
}

/**
 * 将 app-icon.ico 复制到 build/ 供 electron-builder 嵌入 exe 与 NSIS 安装包
 * @throws {Error} 缺少有效 ICO 时抛出
 */
function copyToBuildResources() {
  if (!fs.existsSync(APP_ICO)) {
    throw new Error(
      '缺少 assets/app-icon.ico。请将 logo 放到 assets/logo.png 后执行: python scripts/process-logo.py'
    );
  }
  const { size } = fs.statSync(APP_ICO);
  if (size < MIN_ICO_BYTES) {
    console.warn(
      `[WARN] app-icon.ico 仅 ${size} 字节，可能缺少 256x256。请从 logo.png 重新生成。`
    );
  }

  fs.mkdirSync(BUILD, { recursive: true });
  for (const name of ['icon.ico', 'installerIcon.ico', 'uninstallerIcon.ico']) {
    fs.copyFileSync(APP_ICO, path.join(BUILD, name));
  }
  console.log(`[prepare-icons] 已写入 build/icon.ico (${size} bytes)`);
}

runProcessLogo();
copyToBuildResources();
