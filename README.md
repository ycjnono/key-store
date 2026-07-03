# Key-Store

本地离线账户密码管理工具（JavaScript / Electron 版）。

基于 **Electron + Node.js + better-sqlite3** 实现，数据保存在本机 SQLite 数据库中，账户密码经主密码派生密钥后 AES-256-GCM 加密存储，不依赖网络服务。

## 文档索引

| 文档 | 说明 |
|------|------|
| [使用说明](docs/使用说明.md) | 安装、登录、账户管理、导入导出、卸载等用户操作 |
| [开发手册](docs/开发手册.md) | 项目结构、架构、本地开发、打包构建、排错与扩展 |

## 环境要求

| 工具 | 版本 |
|------|------|
| Node.js | 18 LTS 或更高 |
| npm | 9+ |
| Windows | 10/11（开发与打包） |
| Python + Pillow | 可选，打包自定义图标时需要 |

## 快速开始

```bat
install-deps.bat
npm start
```

## 打包安装程序

```bat
build-exe.bat
```

输出：`release/KeyStore-1.0.3-Setup.exe`

## 项目结构（概览）

```
key-store/
├── electron/           # 主进程、preload、IPC、系统托盘
├── app/                # 配置、加密、数据库、业务服务
├── renderer/           # 登录页与主界面（HTML/CSS/JS）
├── assets/             # logo 与各尺寸图标
├── build/              # electron-builder 资源（icon.ico、NSIS 脚本）
├── scripts/            # 构建与图标处理脚本
├── install-deps.bat    # 安装依赖（绕过失效全局镜像）
├── build-exe.bat       # 一键打包 Windows 安装程序
└── package.json
```

## 数据与加密（摘要）

- **数据库路径：** `%APPDATA%\keystore\data\key_store.db`
- **KDF：** PBKDF2-HMAC-SHA256，100,000 次迭代
- **加密：** AES-256-GCM
- **自动锁定：** 无操作 5 分钟后清除内存中的主密钥

详细说明见 [使用说明](docs/使用说明.md) 与 [开发手册](docs/开发手册.md)。

## 常用命令

```bash
npm start              # 开发运行
npm run prepare:icons  # 从 assets/logo.png 生成图标
npm run build          # 打包 NSIS 安装程序
npm run build:dir      # 输出未打包目录（调试）
```

## 许可证

本项目采用 [MIT License](LICENSE) 开源。
