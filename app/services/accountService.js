/**
 * 账户服务
 */
const { getDb, nowIso } = require('../db/database');
const cryptoService = require('../crypto/cryptoService');

function rowToAccount(row, decrypt = false) {
  if (!row) return null;
  const account = { ...row };
  if (decrypt && row.password) {
    try {
      account.passwordPlain = cryptoService.decryptPassword(row.password);
    } catch {
      account.passwordPlain = null;
    }
  }
  return account;
}

function getAllAccounts() {
  return getDb().prepare('SELECT * FROM account ORDER BY name COLLATE NOCASE').all();
}

function getAccountsByCategory(categoryId) {
  return getDb().prepare('SELECT * FROM account WHERE category_id = ? ORDER BY name COLLATE NOCASE').all(categoryId);
}

function searchAccounts(keyword) {
  const like = `%${keyword}%`;
  return getDb().prepare(
    'SELECT * FROM account WHERE name LIKE ? OR username LIKE ? ORDER BY name COLLATE NOCASE'
  ).all(like, like);
}

function getAccountById(id, decrypt = false) {
  const row = getDb().prepare('SELECT * FROM account WHERE id = ?').get(id);
  return rowToAccount(row, decrypt);
}

function createAccount(data) {
  const ts = nowIso();
  const enc = cryptoService.encryptPassword(data.password || '');
  const info = getDb().prepare(`
    INSERT INTO account (category_id, name, address, port, username, password, valid_from, valid_to, notes, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    data.categoryId, data.name.trim(), data.address || null, data.port || null,
    data.username || null, enc, data.validFrom || null, data.validTo || null,
    data.notes || null, ts, ts
  );
  return getAccountById(info.lastInsertRowid);
}

function updateAccount(id, data) {
  const ts = nowIso();
  let enc;
  if (data.password != null && data.password !== '') {
    enc = cryptoService.encryptPassword(data.password);
    getDb().prepare(`
      UPDATE account SET category_id=?, name=?, address=?, port=?, username=?, password=?,
      valid_from=?, valid_to=?, notes=?, updated_at=? WHERE id=?
    `).run(
      data.categoryId, data.name.trim(), data.address || null, data.port || null,
      data.username || null, enc, data.validFrom || null, data.validTo || null,
      data.notes || null, ts, id
    );
  } else {
    getDb().prepare(`
      UPDATE account SET category_id=?, name=?, address=?, port=?, username=?,
      valid_from=?, valid_to=?, notes=?, updated_at=? WHERE id=?
    `).run(
      data.categoryId, data.name.trim(), data.address || null, data.port || null,
      data.username || null, data.validFrom || null, data.validTo || null,
      data.notes || null, ts, id
    );
  }
  return getAccountById(id);
}

function deleteAccount(id) {
  getDb().prepare('DELETE FROM account WHERE id = ?').run(id);
}

function decryptPasswordForAccount(id) {
  const row = getDb().prepare('SELECT password FROM account WHERE id = ?').get(id);
  if (!row) return null;
  return cryptoService.decryptPassword(row.password);
}

module.exports = {
  getAllAccounts,
  getAccountsByCategory,
  searchAccounts,
  getAccountById,
  createAccount,
  updateAccount,
  deleteAccount,
  decryptPasswordForAccount,
};
