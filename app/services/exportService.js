/**
 * 导出服务 — 导出为独立 SQLite 文件
 */
const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');
const { getDb } = require('../db/database');

function copySchema(targetDb) {
  const source = getDb();
  const tables = ['crypto_meta', 'category', 'account'];
  for (const table of tables) {
    const ddl = source.prepare(
      `SELECT sql FROM sqlite_master WHERE type='table' AND name=?`
    ).get(table);
    if (ddl?.sql) targetDb.exec(ddl.sql);
  }
}

function exportAll(targetPath) {
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  if (fs.existsSync(targetPath)) fs.unlinkSync(targetPath);

  const target = new Database(targetPath);
  copySchema(target);

  const meta = getDb().prepare('SELECT * FROM crypto_meta WHERE id=1').get();
  if (meta) {
    target.prepare('INSERT INTO crypto_meta (id,salt,verification,iterations) VALUES (1,?,?,?)')
      .run(meta.salt, meta.verification, meta.iterations);
  }

  const categories = getDb().prepare('SELECT * FROM category').all();
  const catStmt = target.prepare(
    'INSERT INTO category (id,name,icon,sort_order,created_at,updated_at) VALUES (?,?,?,?,?,?)'
  );
  for (const c of categories) {
    catStmt.run(c.id, c.name, c.icon, c.sort_order, c.created_at, c.updated_at);
  }

  const accounts = getDb().prepare('SELECT * FROM account').all();
  const accStmt = target.prepare(`
    INSERT INTO account (id,category_id,name,address,port,username,password,valid_from,valid_to,notes,created_at,updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
  `);
  for (const a of accounts) {
    accStmt.run(a.id, a.category_id, a.name, a.address, a.port, a.username, a.password,
      a.valid_from, a.valid_to, a.notes, a.created_at, a.updated_at);
  }
  target.close();

  return { description: `已导出 ${categories.length} 个分类、${accounts.length} 个账户` };
}

function exportByCategories(targetPath, categoryIds) {
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  if (fs.existsSync(targetPath)) fs.unlinkSync(targetPath);

  const target = new Database(targetPath);
  copySchema(target);

  const meta = getDb().prepare('SELECT * FROM crypto_meta WHERE id=1').get();
  if (meta) {
    target.prepare('INSERT INTO crypto_meta (id,salt,verification,iterations) VALUES (1,?,?,?)')
      .run(meta.salt, meta.verification, meta.iterations);
  }

  const placeholders = categoryIds.map(() => '?').join(',');
  const categories = getDb().prepare(`SELECT * FROM category WHERE id IN (${placeholders})`).all(...categoryIds);
  const catStmt = target.prepare(
    'INSERT INTO category (id,name,icon,sort_order,created_at,updated_at) VALUES (?,?,?,?,?,?)'
  );
  for (const c of categories) {
    catStmt.run(c.id, c.name, c.icon, c.sort_order, c.created_at, c.updated_at);
  }

  const accounts = getDb().prepare(
    `SELECT * FROM account WHERE category_id IN (${placeholders})`
  ).all(...categoryIds);
  const accStmt = target.prepare(`
    INSERT INTO account (id,category_id,name,address,port,username,password,valid_from,valid_to,notes,created_at,updated_at)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
  `);
  for (const a of accounts) {
    accStmt.run(a.id, a.category_id, a.name, a.address, a.port, a.username, a.password,
      a.valid_from, a.valid_to, a.notes, a.created_at, a.updated_at);
  }
  target.close();

  return { description: `已导出 ${categories.length} 个分类、${accounts.length} 个账户` };
}

module.exports = { exportAll, exportByCategories };
