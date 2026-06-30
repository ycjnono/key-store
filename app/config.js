/**
 * 应用全局配置
 * 数据目录与 Java 版结构一致：{userData}/data/key_store.db
 */
const path = require('path');
const fs = require('fs');
const { app } = require('electron');

const PBKDF2_ITERATIONS = 100_000;
const VERIFICATION_PLAINTEXT = 'KEYSTORE_VERIFY';
const AUTO_LOCK_MINUTES = 5;

/**
 * 获取安装/数据根目录
 * @returns {string} 用户数据根路径
 */
function getUserDataRoot() {
  return app.getPath('userData');
}

/**
 * 获取数据目录路径
 * @returns {string} data 目录绝对路径
 */
function getDataDir() {
  return path.join(getUserDataRoot(), 'data');
}

/**
 * 获取数据库文件路径
 * @returns {string} SQLite 数据库路径
 */
function getDbPath() {
  return path.join(getDataDir(), 'key_store.db');
}

/**
 * 确保数据目录存在
 */
function ensureDataDir() {
  fs.mkdirSync(getDataDir(), { recursive: true });
}

module.exports = {
  PBKDF2_ITERATIONS,
  VERIFICATION_PLAINTEXT,
  AUTO_LOCK_MINUTES,
  getUserDataRoot,
  getDataDir,
  getDbPath,
  ensureDataDir,
};
