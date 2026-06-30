/**
 * 分类服务
 */
const { getDb, nowIso } = require('../db/database');

const DEFAULT_CATEGORIES = ['网站', 'APP', '服务器', 'API', '邮箱', '项目', '银行卡', '门禁', '其他'];

function getAllCategories() {
  return getDb().prepare('SELECT * FROM category ORDER BY sort_order ASC, id ASC').all();
}

function getCategoryById(id) {
  return getDb().prepare('SELECT * FROM category WHERE id = ?').get(id);
}

function countAccounts(categoryId) {
  const row = getDb().prepare('SELECT COUNT(*) AS c FROM account WHERE category_id = ?').get(categoryId);
  return row.c;
}

function createCategory(name, icon = null, sortOrder = null) {
  const ts = nowIso();
  if (sortOrder == null) {
    const max = getDb().prepare('SELECT COALESCE(MAX(sort_order), 0) AS m FROM category').get();
    sortOrder = max.m + 1;
  }
  const info = getDb().prepare(
    'INSERT INTO category (name, icon, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?)'
  ).run(name.trim(), icon, sortOrder, ts, ts);
  return getCategoryById(info.lastInsertRowid);
}

function updateCategory(id, name, icon, sortOrder) {
  const ts = nowIso();
  getDb().prepare(
    'UPDATE category SET name=?, icon=?, sort_order=?, updated_at=? WHERE id=?'
  ).run(name, icon, sortOrder, ts, id);
  return getCategoryById(id);
}

function deleteCategory(id) {
  getDb().prepare('DELETE FROM category WHERE id = ?').run(id);
}

function createDefaultCategories() {
  const existing = getDb().prepare('SELECT COUNT(*) AS c FROM category').get();
  if (existing.c > 0) return;
  DEFAULT_CATEGORIES.forEach((name, i) => createCategory(name, null, i + 1));
}

module.exports = {
  getAllCategories,
  getCategoryById,
  countAccounts,
  createCategory,
  updateCategory,
  deleteCategory,
  createDefaultCategories,
};
