/*
 * ============================================================
 * 导入服务类
 * ============================================================
 * 功能 : 从标准 SQLite 文件导入账户数据，支持两种模式：
 *        - 覆盖导入（OVERWRITE）：清空当前数据后恢复
 *        - 追加导入（APPEND）：保留现有数据，按名称+分类去重
 *
 * 调用方 : ImportExportController（导入按钮）
 * ============================================================
 */
package com.changjiang.keystore.service;

import com.changjiang.keystore.model.ImportLog;
import com.changjiang.keystore.model.enums.ImportMode;
import com.changjiang.keystore.repository.CryptoMetaRepository;
import com.changjiang.keystore.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入服务类
 * <p>
 * 从标准 SQLite 文件导入账户数据。
 * </p>
 */
public class ImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportService.class);

    /**
     * 导入预览信息
     */
    public static class ImportPreview {
        /** 新增账户数 */
        public int newAccounts;
        /** 更新账户数 */
        public int updatedAccounts;
        /** 跳过的账户数（重复） */
        public int skippedAccounts;
        /** 分类数 */
        public int categoryCount;
        /** 来源文件名 */
        public String sourceFile;

        public String getSummary() {
            return String.format("分类 %d 个 | 新增 %d 个账户 | 更新 %d 个账户 | 跳过 %d 个",
                    categoryCount, newAccounts, updatedAccounts, skippedAccounts);
        }
    }

    // ==================== 预览导入 ====================

    /**
     * 预览导入操作（不实际写入数据）
     * <p>
     * 调用方: ImportExportController（导入前展示预览）
     * </p>
     *
     * @param sourcePath 源文件路径
     * @param mode       导入模式（OVERWRITE / APPEND）
     * @return 导入预览信息
     * @throws ImportException 预览失败
     */
    public ImportPreview previewImport(Path sourcePath, ImportMode mode) throws ImportException {
        try {
            // 读取源文件数据
            SourceData sourceData = readSourceFile(sourcePath);

            ImportPreview preview = new ImportPreview();
            preview.sourceFile = sourcePath.getFileName().toString();
            preview.categoryCount = sourceData.categories.size();

            if (mode == ImportMode.OVERWRITE) {
                // 覆盖模式：全部重新导入
                preview.newAccounts = sourceData.accounts.size();
                preview.updatedAccounts = 0;
                preview.skippedAccounts = 0;
            } else {
                // 追加模式：去重统计
                Map<String, AccountRecord> existingMap = loadExistingAccounts();

                for (AccountRecord account : sourceData.accounts) {
                    String key = account.categoryId + "_" + account.name;
                    if (existingMap.containsKey(key)) {
                        preview.updatedAccounts++;
                        preview.skippedAccounts++;
                    } else {
                        preview.newAccounts++;
                    }
                }
            }

            LOGGER.info("导入预览: {}", preview.getSummary());
            return preview;

        } catch (Exception e) {
            LOGGER.error("预览导入失败", e);
            throw new ImportException("预览导入失败: " + e.getMessage(), e);
        }
    }

    // ==================== 执行导入 ====================

    /**
     * 执行覆盖导入
     * <p>
     * 清空当前数据库，从源文件恢复全部数据。
     * 调用方: ImportExportController（用户确认覆盖导入后）
     * </p>
     *
     * @param sourcePath 源文件路径
     * @return 导入记录
     * @throws ImportException 导入失败
     */
    public ImportLog importOverwrite(Path sourcePath) throws ImportException {
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            Connection conn = dbManager.getConnection();

            LOGGER.info("开始覆盖导入: {}", sourcePath);

            // 1. 清空现有数据
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM account");
                stmt.execute("DELETE FROM category");
                stmt.execute("DELETE FROM import_log");
            }
            conn.commit();

            // 2. 读取源文件
            SourceData sourceData = readSourceFile(sourcePath);

            // 3. 恢复分类
            Map<Long, Long> categoryIdMapping = restoreCategories(conn, sourceData.categories);

            // 4. 恢复账户（使用源文件的 ID）
            int accountCount = restoreAccounts(conn, sourceData.accounts, categoryIdMapping);

            // 5. 记录导入日志
            ImportLog log = new ImportLog(
                    sourcePath.getFileName().toString(),
                    ImportMode.OVERWRITE.name(),
                    accountCount,
                    0
            );
            saveImportLog(conn, log);

            conn.commit();

            LOGGER.info("覆盖导入完成: {} 个账户", accountCount);
            return log;

        } catch (Exception e) {
            try {
                DatabaseManager.getInstance().getConnection().rollback();
            } catch (SQLException ex) {
                LOGGER.error("回滚失败", ex);
            }
            LOGGER.error("覆盖导入失败", e);
            throw new ImportException("覆盖导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行追加导入
     * <p>
     * 保留现有数据，导入新账户。
     * 按名称+分类去重：已存在的进行更新，新增的进行插入。
     * 调用方: ImportExportController（用户确认追加导入后）
     * </p>
     *
     * @param sourcePath 源文件路径
     * @return 导入记录
     * @throws ImportException 导入失败
     */
    public ImportLog importAppend(Path sourcePath) throws ImportException {
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            Connection conn = dbManager.getConnection();

            LOGGER.info("开始追加导入: {}", sourcePath);

            // 1. 读取源文件
            SourceData sourceData = readSourceFile(sourcePath);

            // 2. 加载现有账户映射（用于去重）
            Map<String, AccountRecord> existingMap = loadExistingAccounts();

            // 3. 分类映射（源分类 ID → 当前分类 ID）
            Map<Long, Long> categoryIdMapping = buildCategoryMapping(conn, sourceData.categories);

            conn.setAutoCommit(false);

            // 4. 导入账户
            int added = 0;
            int updated = 0;

            for (AccountRecord sourceAccount : sourceData.accounts) {
                String key = sourceAccount.categoryId + "_" + sourceAccount.name;
                AccountRecord existing = existingMap.get(key);

                if (existing != null) {
                    // 已存在 → 更新
                    updateAccount(conn, existing.localId, sourceAccount);
                    updated++;
                } else {
                    // 新增
                    Long mappedCategoryId = categoryIdMapping.get(sourceAccount.categoryId);
                    if (mappedCategoryId != null) {
                        insertAccount(conn, mappedCategoryId, sourceAccount);
                        added++;
                    }
                }
            }

            // 5. 记录导入日志
            ImportLog log = new ImportLog(
                    sourcePath.getFileName().toString(),
                    ImportMode.APPEND.name(),
                    added,
                    updated
            );
            saveImportLog(conn, log);

            conn.commit();

            LOGGER.info("追加导入完成: 新增 {} 个，更新 {} 个", added, updated);
            return log;

        } catch (Exception e) {
            try {
                DatabaseManager.getInstance().getConnection().rollback();
            } catch (SQLException ex) {
                LOGGER.error("回滚失败", ex);
            }
            LOGGER.error("追加导入失败", e);
            throw new ImportException("追加导入失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 读取源文件数据
     *
     * @param sourcePath 源 SQLite 文件路径
     * @return 解析后的分类与账户数据
     * @throws SQLException      数据库读取失败
     * @throws ImportException   源文件为空或格式不正确
     */
    private SourceData readSourceFile(Path sourcePath) throws SQLException, ImportException {
        SourceData data = new SourceData();

        String url = "jdbc:sqlite:" + sourcePath.toString();
        try (Connection conn = DriverManager.getConnection(url)) {
            // 读取分类
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name, icon, sort_order, created_at, updated_at FROM category")) {
                while (rs.next()) {
                    SourceCategory cat = new SourceCategory();
                    cat.id = rs.getLong("id");
                    cat.name = rs.getString("name");
                    cat.icon = rs.getString("icon");
                    cat.sortOrder = rs.getObject("sort_order", Integer.class);
                    cat.createdAt = rs.getString("created_at");
                    cat.updatedAt = rs.getString("updated_at");
                    data.categories.add(cat);
                }
            }

            // 读取账户
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT id, category_id, name, address, port, username, password, " +
                         "valid_from, valid_to, notes, created_at, updated_at FROM account")) {
                while (rs.next()) {
                    AccountRecord rec = new AccountRecord();
                    rec.id = rs.getLong("id");
                    rec.categoryId = rs.getLong("category_id");
                    rec.name = rs.getString("name");
                    rec.address = rs.getString("address");
                    rec.port = rs.getObject("port", Integer.class);
                    rec.username = rs.getString("username");
                    rec.password = rs.getBytes("password");
                    rec.validFrom = rs.getString("valid_from");
                    rec.validTo = rs.getString("valid_to");
                    rec.notes = rs.getString("notes");
                    rec.createdAt = rs.getString("created_at");
                    rec.updatedAt = rs.getString("updated_at");
                    data.accounts.add(rec);
                }
            }
        }

        if (data.categories.isEmpty() && data.accounts.isEmpty()) {
            throw new ImportException("源文件为空或格式不正确");
        }

        return data;
    }

    /**
     * 加载当前数据库中的账户映射
     */
    private Map<String, AccountRecord> loadExistingAccounts() throws SQLException {
        Map<String, AccountRecord> map = new HashMap<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, category_id, name FROM account")) {
            while (rs.next()) {
                AccountRecord rec = new AccountRecord();
                rec.localId = rs.getLong("id");
                rec.categoryId = rs.getLong("category_id");
                rec.name = rs.getString("name");
                String key = rec.categoryId + "_" + rec.name;
                map.put(key, rec);
            }
        }

        return map;
    }

    /**
     * 构建分类映射（源文件分类 ID → 当前数据库分类 ID）
     * 按名称匹配
     */
    private Map<Long, Long> buildCategoryMapping(Connection conn, List<SourceCategory> sourceCategories)
            throws SQLException {
        Map<Long, Long> mapping = new HashMap<>();

        // 读取当前所有分类（名称 → ID）
        Map<String, Long> currentCategoryMap = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name FROM category")) {
            while (rs.next()) {
                currentCategoryMap.put(rs.getString("name").toLowerCase(), rs.getLong("id"));
            }
        }

        // 匹配
        for (SourceCategory src : sourceCategories) {
            Long currentId = currentCategoryMap.get(src.name.toLowerCase());
            if (currentId != null) {
                mapping.put(src.id, currentId);
            }
        }

        return mapping;
    }

    /**
     * 恢复分类（覆盖导入）
     */
    private Map<Long, Long> restoreCategories(Connection conn, List<SourceCategory> categories)
            throws SQLException {
        Map<Long, Long> mapping = new HashMap<>();

        for (SourceCategory cat : categories) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO category (id, name, icon, sort_order, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, cat.id);
                ps.setString(2, cat.name);
                ps.setString(3, cat.icon);
                ps.setObject(4, cat.sortOrder);
                ps.setString(5, cat.createdAt);
                ps.setString(6, cat.updatedAt);
                ps.executeUpdate();
                mapping.put(cat.id, cat.id);
            }
        }

        return mapping;
    }

    /**
     * 恢复账户（覆盖导入）
     */
    private int restoreAccounts(Connection conn, List<AccountRecord> accounts,
                                Map<Long, Long> categoryMapping) throws SQLException {
        int count = 0;

        for (AccountRecord account : accounts) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO account (id, category_id, name, address, port, username, password, " +
                    "valid_from, valid_to, notes, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, account.id);
                Long mappedCategoryId = categoryMapping.get(account.categoryId);
                ps.setLong(2, mappedCategoryId != null ? mappedCategoryId : account.categoryId);
                ps.setString(3, account.name);
                ps.setString(4, account.address);
                ps.setObject(5, account.port);
                ps.setString(6, account.username);
                ps.setBytes(7, account.password);
                ps.setString(8, account.validFrom);
                ps.setString(9, account.validTo);
                ps.setString(10, account.notes);
                ps.setString(11, account.createdAt);
                ps.setString(12, account.updatedAt);
                ps.executeUpdate();
                count++;
            }
        }

        return count;
    }

    /**
     * 插入新账户（追加导入）
     */
    private void insertAccount(Connection conn, Long categoryId, AccountRecord sourceAccount)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO account (category_id, name, address, port, username, password, " +
                "valid_from, valid_to, notes, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, categoryId);
            ps.setString(2, sourceAccount.name);
            ps.setString(3, sourceAccount.address);
            ps.setObject(4, sourceAccount.port);
            ps.setString(5, sourceAccount.username);
            ps.setBytes(6, sourceAccount.password);
            ps.setString(7, sourceAccount.validFrom);
            ps.setString(8, sourceAccount.validTo);
            ps.setString(9, sourceAccount.notes);
            ps.setString(10, sourceAccount.createdAt);
            ps.setString(11, sourceAccount.updatedAt);
            ps.executeUpdate();
        }
    }

    /**
     * 更新已有账户（追加导入）
     */
    private void updateAccount(Connection conn, Long localId, AccountRecord sourceAccount)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET address = ?, port = ?, username = ?, password = ?, " +
                "valid_from = ?, valid_to = ?, notes = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, sourceAccount.address);
            ps.setObject(2, sourceAccount.port);
            ps.setString(3, sourceAccount.username);
            ps.setBytes(4, sourceAccount.password);
            ps.setString(5, sourceAccount.validFrom);
            ps.setString(6, sourceAccount.validTo);
            ps.setString(7, sourceAccount.notes);
            ps.setString(8, LocalDateTime.now().toString());
            ps.setLong(9, localId);
            ps.executeUpdate();
        }
    }

    /**
     * 保存导入记录
     */
    private void saveImportLog(Connection conn, ImportLog log) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO import_log (source_file, import_mode, accounts_added, accounts_updated, imported_at) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, log.getSourceFile());
            ps.setString(2, log.getImportMode());
            ps.setInt(3, log.getAccountsAdded());
            ps.setInt(4, log.getAccountsUpdated());
            ps.setString(5, log.getImportedAt() != null ? log.getImportedAt().toString() : LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    // ==================== 内部数据结构 ====================

    /**
     * 源文件数据
     */
    private static class SourceData {
        List<SourceCategory> categories = new ArrayList<>();
        List<AccountRecord> accounts = new ArrayList<>();
    }

    /**
     * 源文件分类记录
     */
    private static class SourceCategory {
        Long id;
        String name;
        String icon;
        Integer sortOrder;
        String createdAt;
        String updatedAt;
    }

    /**
     * 账户记录（源文件或当前数据库）
     */
    private static class AccountRecord {
        Long localId;       // 当前数据库中的 ID（追加模式用）
        Long id;            // 源文件 ID
        Long categoryId;
        String name;
        String address;
        Integer port;
        String username;
        byte[] password;
        String validFrom;
        String validTo;
        String notes;
        String createdAt;
        String updatedAt;
    }

    // ==================== 异常类 ====================

    /**
     * 导入操作异常
     */
    public static class ImportException extends Exception {
        public ImportException(String message) {
            super(message);
        }

        public ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
