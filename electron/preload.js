/**
 * Preload — 安全暴露 API 给渲染进程
 */
const {contextBridge, ipcRenderer} = require('electron');

function invoke(channel, ...args) {
    return ipcRenderer.invoke(channel, ...args);
}

contextBridge.exposeInMainWorld('keystore', {
    auth: {
        isFirstUse: () => invoke('auth:isFirstUse'),
        setup: (password) => invoke('auth:setup', password),
        verify: (password) => invoke('auth:verify', password),
        lock: () => invoke('auth:lock'),
        isUnlocked: () => invoke('auth:isUnlocked'),
        passwordStrength: (pwd) => invoke('auth:passwordStrength', pwd),
    },
    category: {
        list: () => invoke('category:list'),
        countAccounts: (id) => invoke('category:countAccounts', id),
        create: (name) => invoke('category:create', name),
        update: (id, name, icon, sort) => invoke('category:update', id, name, icon, sort),
        delete: (id) => invoke('category:delete', id),
    },
    account: {
        list: (categoryId, keyword) => invoke('account:list', categoryId, keyword),
        get: (id) => invoke('account:get', id),
        create: (data) => invoke('account:create', data),
        update: (id, data) => invoke('account:update', id, data),
        delete: (id) => invoke('account:delete', id),
        decryptPassword: (id) => invoke('account:decryptPassword', id),
    },
    export: {
        all: (path) => invoke('export:all', path),
        categories: (path, ids) => invoke('export:categories', path, ids),
    },
    import: {
        preview: (path, mode) => invoke('import:preview', path, mode),
        overwrite: (path) => invoke('import:overwrite', path),
        append: (path, sourcePassword) => invoke('import:append', path, sourcePassword),
    },
    dialog: {
        saveFile: (name) => invoke('dialog:saveFile', name),
        openFile: () => invoke('dialog:openFile'),
    },
    clipboard: {write: (text) => invoke('clipboard:write', text)},
    app: {getDbPath: () => invoke('app:getDbPath')},
    nav: {
        login: () => invoke('nav:login'),
        dashboard: () => invoke('nav:dashboard'),
    },
    window: {
        minimize: () => invoke('window:minimize'),
        close: () => invoke('window:close'),
    },
});
