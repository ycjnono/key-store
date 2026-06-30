/**
 * SQLite 数据库管理（better-sqlite3）
 */
const Database = require('better-sqlite3');
const { getDbPath, ensureDataDir } = require('../config');

/** @type {Database.Database|null} */
let db = null;

/**
 * 获取数据库连接
 * @returns {Database.Database}
 */
function getDb() {
  if (!db) {
    ensureDataDir();
    db = new Database(getDbPath());
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    db.pragma('synchronous = NORMAL');
    initSchema(db);
  }
  return db;
}

/**
 * 初始化表结构（与 Java 版一致）
 * @param {Database.Database} database
 */
function initSchema(database) {
  database.exec(`
    CREATE TABLE IF NOT EXISTS crypto_meta (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      salt BLOB NOT NULL,
      verification BLOB NOT NULL,
      iterations INTEGER NOT NULL DEFAULT 100000
    );

    CREATE TABLE IF NOT EXISTS category (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL COLLATE NOCASE,
      icon TEXT,
      sort_order INTEGER DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS account (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      category_id INTEGER NOT NULL,
      name TEXT NOT NULL COLLATE NOCASE,
      address TEXT,
      port INTEGER,
      username TEXT,
      password BLOB NOT NULL,
      valid_from TEXT,
      valid_to TEXT,
      notes TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS import_log (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      source_file TEXT NOT NULL,
      import_mode TEXT NOT NULL,
      accounts_added INTEGER DEFAULT 0,
      accounts_updated INTEGER DEFAULT 0,
      imported_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_account_category ON account(category_id);
    CREATE INDEX IF NOT EXISTS idx_account_name ON account(name);
    CREATE INDEX IF NOT EXISTS idx_account_valid_to ON account(valid_to);
  `);
}

function closeDb() {
  if (db) {
    db.close();
    db = null;
  }
}

function nowIso() {
  return new Date().toISOString().slice(0, 19);
}

module.exports = { getDb, closeDb, nowIso };
