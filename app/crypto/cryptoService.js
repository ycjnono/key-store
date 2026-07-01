/**
 * 加密服务 — 与 Java CryptoService 算法兼容
 * PBKDF2-HMAC-SHA256 + AES-256-GCM，密文格式: [IV 12B][密文+Tag]
 */
const crypto = require('crypto');
const { SESSION_LOCKED_MESSAGE } = require('../config');

const IV_LENGTH = 12;
const SALT_LENGTH = 16;
const TAG_LENGTH = 16;

/** @type {Buffer|null} 内存中的主密钥 */
let masterKey = null;

/**
 * 派生主密钥
 * @param {string} password 主密码
 * @param {Buffer} salt 盐值
 * @param {number} iterations 迭代次数
 * @returns {Buffer} 32 字节 AES 密钥
 */
function deriveMasterKey(password, salt, iterations) {
  return crypto.pbkdf2Sync(password, salt, iterations, 32, 'sha256');
}

/**
 * 生成随机盐
 * @returns {Buffer}
 */
function generateSalt() {
  return crypto.randomBytes(SALT_LENGTH);
}

/**
 * AES-GCM 加密
 * @param {Buffer} key 主密钥
 * @param {string} plaintext 明文
 * @returns {Buffer}
 */
function encrypt(key, plaintext) {
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([
    cipher.update(plaintext, 'utf8'),
    cipher.final(),
  ]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, encrypted, tag]);
}

/**
 * AES-GCM 解密
 * @param {Buffer} key 主密钥
 * @param {Buffer} data 密文
 * @returns {string}
 */
function decrypt(key, data) {
  const iv = data.subarray(0, IV_LENGTH);
  const payload = data.subarray(IV_LENGTH);
  const tag = payload.subarray(payload.length - TAG_LENGTH);
  const ciphertext = payload.subarray(0, payload.length - TAG_LENGTH);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

function setMasterKey(key) {
  masterKey = key;
}

function clearMasterKey() {
  masterKey = null;
}

function isUnlocked() {
  return masterKey !== null;
}

function getMasterKey() {
  if (!masterKey) {
    throw new Error(SESSION_LOCKED_MESSAGE);
  }
  return masterKey;
}

function encryptVerification(plaintext) {
  return encrypt(getMasterKey(), plaintext);
}

function decryptVerification(data) {
  return decrypt(getMasterKey(), data);
}

function encryptPassword(plaintext) {
  if (plaintext == null) return null;
  return encrypt(getMasterKey(), plaintext);
}

function decryptPassword(data) {
  if (!data || data.length === 0) return null;
  return decrypt(getMasterKey(), data);
}

module.exports = {
  deriveMasterKey,
  generateSalt,
  encrypt,
  decrypt,
  setMasterKey,
  clearMasterKey,
  isUnlocked,
  getMasterKey,
  encryptVerification,
  decryptVerification,
  encryptPassword,
  decryptPassword,
};
