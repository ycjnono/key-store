/**
 * Electron 主进程 — 窗口管理、系统托盘与 IPC
 */
const {app, BrowserWindow, ipcMain, dialog, clipboard, Tray, Menu, nativeImage, shell} = require('electron');
const path = require('path');
const fs = require('fs');

const authService = require('../app/services/authService');
const categoryService = require('../app/services/categoryService');
const accountService = require('../app/services/accountService');
const exportService = require('../app/services/exportService');
const importService = require('../app/services/importService');
const {evaluate: evalStrength} = require('../app/utils/passwordStrength');
const {closeDb} = require('../app/db/database');
const {getDbPath} = require('../app/config');

/** @type {BrowserWindow|null} */
let mainWindow = null;
/** @type {Tray|null} */
let tray = null;
/** 用户主动退出时为 true，否则关闭/最小化仅隐藏到托盘 */
let isQuitting = false;

const APP_ID = 'com.changjiang.keystore';

/**
 * 资源目录（开发/打包均可用）
 * @returns {string}
 */
function getAssetsDir() {
    return path.join(__dirname, '../assets');
}

/**
 * 获取应用窗口图标路径
 * @returns {string|undefined}
 */
function getAppIconPath() {
    const assetsDir = getAssetsDir();
    const candidates = ['app-icon.png', 'app-icon.ico', 'tray-icon.png', 'tray-icon.ico'];
    for (const name of candidates) {
        const full = path.join(assetsDir, name);
        if (fs.existsSync(full)) return full;
    }
    return undefined;
}

/**
 * 获取托盘图标（Windows 优先 .ico 16x16，避免托盘不显示）
 * @returns {Electron.NativeImage}
 */
function getTrayIconImage() {
    const assetsDir = getAssetsDir();
    const candidates = process.platform === 'win32'
        ? ['tray-icon-16.png', 'tray-icon.png', 'tray-icon.ico', 'app-icon.png', 'app-icon.ico']
        : ['tray-icon.png', 'tray-icon-16.png', 'tray-icon.ico', 'app-icon.png'];

    let image = nativeImage.createEmpty();
    for (const name of candidates) {
        const full = path.join(assetsDir, name);
        if (!fs.existsSync(full)) continue;
        const loaded = nativeImage.createFromPath(full);
        if (!loaded.isEmpty()) {
            image = loaded;
            break;
        }
    }

    if (image.isEmpty()) {
        throw new Error('Tray icon not found in assets/. Run: node scripts/generate-icons.js');
    }

    // Windows 托盘标准尺寸 16x16
    if (process.platform === 'win32') {
        const {width, height} = image.getSize();
        if (width !== 16 || height !== 16) {
            image = image.resize({width: 16, height: 16, quality: 'best'});
        }
    }

    return image;
}

/**
 * 隐藏主窗口到系统托盘（任务栏隐藏区域）
 */
function hideToTray() {
    if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.hide();
        if (tray) {
            try {
                tray.displayBalloon({
                    title: 'Key-Store',
                    content: '程序已最小化到托盘，双击托盘图标可恢复窗口',
                    iconType: 'info',
                });
            } catch {
                // Windows 10+ 可能不支持 balloon，忽略
            }
        }
    }
}

/**
 * 从托盘恢复并显示主窗口；若已自动锁定则跳转登录页
 */
function showMainWindow() {
    if (!mainWindow || mainWindow.isDestroyed()) {
        createWindow();
        return;
    }
    if (mainWindow.isMinimized()) {
        mainWindow.restore();
    }
    mainWindow.show();
    mainWindow.focus();
    if (!authService.isUnlocked()) {
        navigateToLogin();
    }
}

/**
 * 加载登录页（会话锁定或未解锁时）
 */
function navigateToLogin() {
    if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
    }
}

/**
 * 创建系统托盘图标与右键菜单
 */
function createTray() {
    if (tray) return;

    const icon = getTrayIconImage();
    tray = new Tray(icon);
    tray.setToolTip('Key-Store');

    const contextMenu = Menu.buildFromTemplate([
        {label: '显示主窗口', click: showMainWindow},
        {type: 'separator'},
        {
            label: '退出',
            click: () => {
                isQuitting = true;
                app.quit();
            },
        },
    ]);
    tray.setContextMenu(contextMenu);

    // Windows 有 setContextMenu 时，click 可能不触发，用 right-click 菜单 + double-click
    tray.on('double-click', showMainWindow);
}

/**
 * 设置应用菜单（替换 Electron 默认 Help 菜单）
 */
function setAppMenu() {
    const version = app.getVersion();
    const repoUrl = 'https://github.com/ycjnono/key-store';
    const feedbackEmail = 'ycjnono@gmail.com';

    const template = [
        ...(process.platform === 'darwin'
            ? [{
                label: app.name,
                submenu: [
                    { role: 'about' },
                    { type: 'separator' },
                    { role: 'hide' },
                    { role: 'hideOthers' },
                    { role: 'unhide' },
                    { type: 'separator' },
                    { role: 'quit' },
                ],
            }]
            : []),
        { role: 'fileMenu' },
        { role: 'editMenu' },
        { role: 'viewMenu' },
        { role: 'windowMenu' },
        {
            role: 'help',
            submenu: [
                {
                    label: '查看版本',
                    click: async () => {
                        await dialog.showMessageBox(mainWindow, {
                            type: 'info',
                            title: '版本信息',
                            message: `Key-Store v${version}`,
                        });
                    },
                },
                {
                    label: 'GitHub 地址',
                    click: async () => {
                        const res = await dialog.showMessageBox(mainWindow, {
                            type: 'info',
                            title: 'GitHub',
                            message: repoUrl,
                            buttons: ['打开链接', '关闭'],
                            defaultId: 0,
                        });
                        if (res.response === 0) shell.openExternal(repoUrl);
                    },
                },
                {
                    label: '反馈',
                    click: async () => {
                        const mailto = `mailto:${feedbackEmail}`;
                        const res = await dialog.showMessageBox(mainWindow, {
                            type: 'info',
                            title: '反馈',
                            message: feedbackEmail,
                            buttons: ['打开邮箱', '复制邮箱', '关闭'],
                            defaultId: 0,
                        });
                        if (res.response === 0) shell.openExternal(mailto);
                        if (res.response === 1) clipboard.writeText(feedbackEmail);
                    },
                },
            ],
        },
    ];

    Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function createWindow() {
    const iconPath = getAppIconPath();
    mainWindow = new BrowserWindow({
        width: 960,
        height: 640,
        minWidth: 900,
        minHeight: 600,
        title: 'Key-Store',
        icon: iconPath,
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            contextIsolation: true,
            nodeIntegration: false,
        },
        show: false,
    });

    mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
    mainWindow.once('ready-to-show', () => mainWindow.show());

    // 点击标题栏最小化 → 正常最小化（不隐藏到托盘）
    // 托盘仅用于“关闭隐藏”，避免用户误以为最小化=退出/消失
    mainWindow.on('minimize', () => {
        // no-op: keep default minimize behavior
    });

    // 点击标题栏关闭 → 隐藏到托盘（不退出）
    mainWindow.on('close', (event) => {
        if (!isQuitting) {
            event.preventDefault();
            hideToTray();
        }
    });

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

function wrap(handler) {
    return async (_event, ...args) => {
        try {
            authService.recordActivity();
            return {ok: true, data: await handler(...args)};
        } catch (err) {
            return {ok: false, error: err.message || String(err)};
        }
    };
}

function registerIpc() {
    ipcMain.handle('auth:isFirstUse', wrap(() => authService.isFirstUse()));
    ipcMain.handle('auth:setup', wrap((password) => {
        authService.setupPassword(password);
        return true;
    }));
    ipcMain.handle('auth:verify', wrap((password) => {
        authService.verifyPassword(password);
        return true;
    }));
    ipcMain.handle('auth:lock', wrap(() => {
        authService.lock();
        return true;
    }));
    ipcMain.handle('auth:isUnlocked', wrap(() => authService.isUnlocked()));
    ipcMain.handle('auth:passwordStrength', wrap((pwd) => evalStrength(pwd)));

    ipcMain.handle('category:list', wrap(() => categoryService.getAllCategories()));
    ipcMain.handle('category:countAccounts', wrap((id) => categoryService.countAccounts(id)));
    ipcMain.handle('category:create', wrap((name) => categoryService.createCategory(name)));
    ipcMain.handle('category:update', wrap((id, name, icon, sort) => categoryService.updateCategory(id, name, icon, sort)));
    ipcMain.handle('category:delete', wrap((id) => categoryService.deleteCategory(id)));

    ipcMain.handle('account:list', wrap((categoryId, keyword) => {
        if (keyword) return accountService.searchAccounts(keyword);
        if (categoryId) return accountService.getAccountsByCategory(categoryId);
        return accountService.getAllAccounts();
    }));
    ipcMain.handle('account:get', wrap((id) => accountService.getAccountById(id, true)));
    ipcMain.handle('account:create', wrap((data) => accountService.createAccount(data)));
    ipcMain.handle('account:update', wrap((id, data) => accountService.updateAccount(id, data)));
    ipcMain.handle('account:delete', wrap((id) => accountService.deleteAccount(id)));
    ipcMain.handle('account:decryptPassword', wrap((id) => accountService.decryptPasswordForAccount(id)));

    ipcMain.handle('export:all', wrap((targetPath) => exportService.exportAll(targetPath)));
    ipcMain.handle('export:categories', wrap((targetPath, ids) => exportService.exportByCategories(targetPath, ids)));

    ipcMain.handle('import:preview', wrap((sourcePath, mode) => importService.previewImport(sourcePath, mode)));
    ipcMain.handle('import:overwrite', wrap((sourcePath) => {
        const result = importService.importOverwrite(sourcePath);
        authService.lock();
        return result;
    }));
    ipcMain.handle('import:append', wrap((sourcePath, sourcePassword) =>
        importService.importAppend(sourcePath, sourcePassword || null)
    ));

    ipcMain.handle('dialog:saveFile', async (_e, defaultName) => {
        const result = await dialog.showSaveDialog(mainWindow, {
            title: '选择导出文件位置',
            defaultPath: defaultName || 'keystore_export.db',
            filters: [{name: 'SQLite', extensions: ['db']}],
        });
        return result.canceled ? null : result.filePath;
    });

    ipcMain.handle('dialog:openFile', async () => {
        const result = await dialog.showOpenDialog(mainWindow, {
            title: '选择导入文件',
            filters: [{name: 'SQLite', extensions: ['db']}],
            properties: ['openFile'],
        });
        return result.canceled ? null : result.filePaths[0];
    });

    ipcMain.handle('clipboard:write', wrap((text) => {
        clipboard.writeText(text || '');
        return true;
    }));

    ipcMain.handle('app:getDbPath', () => getDbPath());
    ipcMain.handle('nav:login', () => {
        navigateToLogin();
        return true;
    });
    ipcMain.handle('nav:dashboard', () => {
        mainWindow.loadFile(path.join(__dirname, '../renderer/dashboard.html'));
        return true;
    });

    ipcMain.handle('window:minimize', () => {
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.minimize();
        }
        return true;
    });

    ipcMain.handle('window:close', () => {
        isQuitting = true;
        app.quit();
        return true;
    });
}

app.whenReady().then(() => {
    if (process.platform === 'win32') {
        app.setAppUserModelId(APP_ID);
    }
    setAppMenu();
    registerIpc();
    authService.onLocked(navigateToLogin);
    createTray();
    createWindow();
});

// 窗口隐藏到托盘时不退出，仅用户主动退出时清理
app.on('window-all-closed', () => {
    if (isQuitting) {
        authService.lock();
        closeDb();
        if (process.platform !== 'darwin') app.quit();
    }
});

app.on('before-quit', () => {
    isQuitting = true;
    authService.lock();
    closeDb();
    if (tray) {
        tray.destroy();
        tray = null;
    }
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
    } else {
        showMainWindow();
    }
});
