/**
 * 认证服务
 */
const fs = require('fs');
const { getDbPath, ensureDataDir, PBKDF2_ITERATIONS, VERIFICATION_PLAINTEXT, AUTO_LOCK_MINUTES } = require('../config');
const { getDb } = require('../db/database');
const cryptoService = require('../crypto/cryptoService');
const categoryService = require('./categoryService');

let unlocked = false;
let lastActivity = Date.now();
/** @type {NodeJS.Timeout|null} */
let lockTimer = null;

function isFirstUse() {
  if (!fs.existsSync(getDbPath())) return true;
  try {
    const stat = fs.statSync(getDbPath());
    if (stat.size === 0) return true;
    getDb();
    const row = getDb().prepare('SELECT id FROM crypto_meta WHERE id = 1').get();
    return !row;
  } catch {
    return true;
  }
}

function setupPassword(password) {
  ensureDataDir();
  getDb();
  const salt = cryptoService.generateSalt();
  const masterKey = cryptoService.deriveMasterKey(password, salt, PBKDF2_ITERATIONS);
  cryptoService.setMasterKey(masterKey);
  const verification = cryptoService.encryptVerification(VERIFICATION_PLAINTEXT);

  getDb().prepare(`
    INSERT INTO crypto_meta (id, salt, verification, iterations) VALUES (1, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET salt=excluded.salt, verification=excluded.verification, iterations=excluded.iterations
  `).run(salt, verification, PBKDF2_ITERATIONS);

  categoryService.createDefaultCategories();
  unlocked = true;
  lastActivity = Date.now();
  startLockTimer();
}

function verifyPassword(password) {
  getDb();
  const meta = getDb().prepare('SELECT salt, verification, iterations FROM crypto_meta WHERE id = 1').get();
  if (!meta) throw new Error('数据库未初始化，请先设置主密码');

  const masterKey = cryptoService.deriveMasterKey(password, meta.salt, meta.iterations);
  cryptoService.setMasterKey(masterKey);
  const decrypted = cryptoService.decryptVerification(meta.verification);
  if (decrypted !== VERIFICATION_PLAINTEXT) {
    cryptoService.clearMasterKey();
    unlocked = false;
    throw new Error('密码错误');
  }
  unlocked = true;
  lastActivity = Date.now();
  startLockTimer();
}

function lock() {
  cryptoService.clearMasterKey();
  unlocked = false;
  stopLockTimer();
}

function isUnlocked() {
  return unlocked && cryptoService.isUnlocked();
}

function recordActivity() {
  if (unlocked) lastActivity = Date.now();
}

function startLockTimer() {
  stopLockTimer();
  lockTimer = setInterval(() => {
    if (!unlocked) return;
    const elapsed = Date.now() - lastActivity;
    if (elapsed >= AUTO_LOCK_MINUTES * 60 * 1000) lock();
  }, 30_000);
}

function stopLockTimer() {
  if (lockTimer) {
    clearInterval(lockTimer);
    lockTimer = null;
  }
}

module.exports = {
  isFirstUse,
  setupPassword,
  verifyPassword,
  lock,
  isUnlocked,
  recordActivity,
};
