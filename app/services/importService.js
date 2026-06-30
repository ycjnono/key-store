/**
 * 导入服务 — 同步 crypto_meta，支持跨客户端恢复密码
 */
const fs = require('fs');
const Database = require('better-sqlite3');
const { getDb, nowIso } = require('../db/database');
const cryptoService = require('../crypto/cryptoService');
const { VERIFICATION_PLAINTEXT } = require('../config');

/**
 * 转为 Buffer（SQLite BLOB 兼容）
 * @param {Buffer|Uint8Array|string|null} value
 * @returns {Buffer|null}
 */
function toBuffer(value) {
  if (value == null) return null;
  if (Buffer.isBuffer(value)) return value;
  return Buffer.from(value);
}

/**
 * 读取源库分类、账户与加密元数据
 * @param {string} sourcePath
 */
function readSource(sourcePath) {
  if (!fs.existsSync(sourcePath)) throw new Error('源文件不存在');
  const src = new Database(sourcePath, { readonly: true });
  const categories = src.prepare('SELECT * FROM category').all();
  const accounts = src.prepare('SELECT * FROM account').all();
  const cryptoMeta = src.prepare('SELECT * FROM crypto_meta WHERE id = 1').get() || null;
  src.close();
  if (categories.length === 0 && accounts.length === 0) {
    throw new Error('源文件为空或格式不正确');
  }
  return { categories, accounts, cryptoMeta };
}

/**
 * 判断两个 crypto_meta 是否一致
 */
function cryptoMetaEqual(a, b) {
  if (!a || !b) return false;
  return toBuffer(a.salt).equals(toBuffer(b.salt))
    && toBuffer(a.verification).equals(toBuffer(b.verification))
    && a.iterations === b.iterations;
}

/**
 * 校验源库主密码并返回源库主密钥（PBKDF2 仅执行一次）
 * @param {object} cryptoMeta
 * @param {string} sourcePassword
 * @returns {Buffer}
 */
function deriveSourceMasterKey(cryptoMeta, sourcePassword) {
  if (!sourcePassword) {
    throw new Error('请填写源库主密码（与导出端相同）');
  }
  const srcKey = cryptoService.deriveMasterKey(
    sourcePassword,
    toBuffer(cryptoMeta.salt),
    cryptoMeta.iterations
  );
  try {
    const plain = cryptoService.decrypt(srcKey, toBuffer(cryptoMeta.verification));
    if (plain !== VERIFICATION_PLAINTEXT) {
      throw new Error('源库主密码错误');
    }
  } catch (err) {
    if (err.message === '源库主密码错误') throw err;
    throw new Error('源库主密码错误');
  }
  return srcKey;
}

/**
 * 写入 crypto_meta（覆盖本地）
 * @param {object} cryptoMeta
 */
function applyCryptoMeta(cryptoMeta) {
  if (!cryptoMeta) {
    throw new Error('导出文件缺少加密信息(crypto_meta)，无法恢复账户密码');
  }
  getDb().prepare(`
    INSERT INTO crypto_meta (id, salt, verification, iterations) VALUES (1, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
      salt=excluded.salt,
      verification=excluded.verification,
      iterations=excluded.iterations
  `).run(cryptoMeta.salt, cryptoMeta.verification, cryptoMeta.iterations);
}

/**
 * 构建密码转换器：跨库导入时预派生密钥，避免每条账户重复 PBKDF2
 * @param {object|null} sourceMeta
 * @param {string|null} sourcePassword
 * @returns {(blob: Buffer|Uint8Array|null, accountName?: string) => Buffer|null}
 */
function createPasswordTransformer(sourceMeta, sourcePassword) {
  const localMeta = getDb().prepare('SELECT * FROM crypto_meta WHERE id = 1').get();
  if (!localMeta) throw new Error('本地库未初始化');

  const needReencrypt = sourceMeta && !cryptoMetaEqual(sourceMeta, localMeta);
  if (!needReencrypt) {
    return (blob) => (blob == null ? blob : toBuffer(blob));
  }

  const srcKey = deriveSourceMasterKey(sourceMeta, sourcePassword);
  const destKey = cryptoService.getMasterKey();

  return (blob, accountName = '') => {
    if (blob == null) return blob;
    try {
      const plain = cryptoService.decrypt(srcKey, toBuffer(blob));
      return cryptoService.encrypt(destKey, plain);
    } catch (err) {
      const hint = accountName ? `（账户：${accountName}）` : '';
      throw new Error(`密码解密失败${hint}，请确认源库主密码是否正确`);
    }
  };
}

/**
 * 追加导入预览：按分类名称 + 账户名称匹配本地数据
 */
function buildAppendPreviewKeys(categories, accounts) {
  const localCategories = getDb().prepare('SELECT id, name FROM category').all();
  const localAccounts = getDb().prepare(`
    SELECT a.name AS account_name, c.name AS category_name
    FROM account a
    JOIN category c ON c.id = a.category_id
  `).all();

  const existingKeys = new Set(
    localAccounts.map((r) => `${r.category_name.toLowerCase()}_${r.account_name.toLowerCase()}`)
  );

  const srcCatNameById = new Map(categories.map((c) => [c.id, c.name.toLowerCase()]));

  let newAccounts = 0;
  let updatedAccounts = 0;
  for (const a of accounts) {
    const catName = srcCatNameById.get(a.category_id) || '';
    const key = `${catName}_${a.name.toLowerCase()}`;
    if (existingKeys.has(key)) {
      updatedAccounts++;
    } else {
      newAccounts++;
    }
  }
  return { newAccounts, updatedAccounts, skippedAccounts: 0 };
}

function previewImport(sourcePath, mode) {
  const { categories, accounts, cryptoMeta } = readSource(sourcePath);
  let newAccounts = 0;
  let updatedAccounts = 0;
  let skippedAccounts = 0;

  if (mode === 'OVERWRITE') {
    newAccounts = accounts.length;
  } else {
    const preview = buildAppendPreviewKeys(categories, accounts);
    newAccounts = preview.newAccounts;
    updatedAccounts = preview.updatedAccounts;
    skippedAccounts = preview.skippedAccounts;
  }

  const localMeta = getDb().prepare('SELECT * FROM crypto_meta WHERE id = 1').get();
  const needSourcePwd = cryptoMeta && localMeta && !cryptoMetaEqual(cryptoMeta, localMeta);
  const cryptoHint = needSourcePwd
    ? ' | 跨库导入需填写源库主密码'
    : cryptoMeta
      ? ' | 同源加密参数，可直接导入'
      : ' | 警告：缺少加密信息，密码可能无法恢复';

  return {
    categoryCount: categories.length,
    newAccounts,
    updatedAccounts,
    skippedAccounts,
    summary: `分类 ${categories.length} 个 | 新增 ${newAccounts} 个账户 | 更新 ${updatedAccounts} 个账户 | 跳过 ${skippedAccounts} 个${cryptoHint}`,
  };
}

/**
 * 覆盖导入 — 同步 crypto_meta，导入后需用源库主密码重新登录
 */
function importOverwrite(sourcePath) {
  const { categories, accounts, cryptoMeta } = readSource(sourcePath);
  const db = getDb();

  const tx = db.transaction(() => {
    db.prepare('DELETE FROM account').run();
    db.prepare('DELETE FROM category').run();
    db.prepare('DELETE FROM import_log').run();

    const catStmt = db.prepare(
      'INSERT INTO category (id,name,icon,sort_order,created_at,updated_at) VALUES (?,?,?,?,?,?)'
    );
    for (const c of categories) {
      catStmt.run(c.id, c.name, c.icon, c.sort_order, c.created_at, c.updated_at);
    }

    const accStmt = db.prepare(`
      INSERT INTO account (id,category_id,name,address,port,username,password,valid_from,valid_to,notes,created_at,updated_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
    `);
    for (const a of accounts) {
      accStmt.run(
        a.id, a.category_id, a.name, a.address, a.port, a.username, a.password,
        a.valid_from, a.valid_to, a.notes, a.created_at, a.updated_at
      );
    }

    applyCryptoMeta(cryptoMeta);

    db.prepare(
      'INSERT INTO import_log (source_file,import_mode,accounts_added,accounts_updated,imported_at) VALUES (?,?,?,?,?)'
    ).run(require('path').basename(sourcePath), 'OVERWRITE', accounts.length, 0, nowIso());
  });
  tx();

  return {
    summary: `覆盖导入完成，共 ${accounts.length} 个账户。请使用导出端的原主密码重新登录。`,
    requireRelogin: true,
  };
}

/**
 * 追加导入 — 跨库时用源库主密码重新加密密码字段
 * @param {string} sourcePath
 * @param {string|null} sourcePassword
 */
function importAppend(sourcePath, sourcePassword = null) {
  const { categories, accounts, cryptoMeta } = readSource(sourcePath);
  const transformPassword = createPasswordTransformer(cryptoMeta, sourcePassword);
  const db = getDb();
  let added = 0;
  let updated = 0;

  const tx = db.transaction(() => {
    const nameToId = new Map(
      db.prepare('SELECT id, name FROM category').all().map((c) => [c.name.toLowerCase(), c.id])
    );

    for (const srcCat of categories) {
      if (!nameToId.has(srcCat.name.toLowerCase())) {
        const ts = nowIso();
        const info = db.prepare(
          'INSERT INTO category (name,icon,sort_order,created_at,updated_at) VALUES (?,?,?,?,?)'
        ).run(srcCat.name, srcCat.icon, srcCat.sort_order, ts, ts);
        nameToId.set(srcCat.name.toLowerCase(), info.lastInsertRowid);
      }
    }

    const srcCatIdToLocal = new Map();
    for (const srcCat of categories) {
      srcCatIdToLocal.set(srcCat.id, nameToId.get(srcCat.name.toLowerCase()));
    }

    for (const a of accounts) {
      const localCatId = srcCatIdToLocal.get(a.category_id);
      if (!localCatId) continue;

      const password = transformPassword(a.password, a.name);

      const existing = db.prepare(
        'SELECT id FROM account WHERE category_id=? AND name=? COLLATE NOCASE'
      ).get(localCatId, a.name);

      if (existing) {
        db.prepare(`
          UPDATE account SET address=?, port=?, username=?, password=?, valid_from=?, valid_to=?, notes=?, updated_at=?
          WHERE id=?
        `).run(a.address, a.port, a.username, password, a.valid_from, a.valid_to, a.notes, nowIso(), existing.id);
        updated++;
      } else {
        const ts = nowIso();
        db.prepare(`
          INSERT INTO account (category_id,name,address,port,username,password,valid_from,valid_to,notes,created_at,updated_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?)
        `).run(localCatId, a.name, a.address, a.port, a.username, password, a.valid_from, a.valid_to, a.notes, ts, ts);
        added++;
      }
    }

    db.prepare(
      'INSERT INTO import_log (source_file,import_mode,accounts_added,accounts_updated,imported_at) VALUES (?,?,?,?,?)'
    ).run(require('path').basename(sourcePath), 'APPEND', added, updated, nowIso());
  });
  tx();

  return { summary: `追加导入完成：新增 ${added} 个，更新 ${updated} 个`, requireRelogin: false };
}

module.exports = { previewImport, importOverwrite, importAppend };
