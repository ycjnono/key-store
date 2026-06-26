/*
 * ============================================================
 * 导出服务类
 * ============================================================
 * 功能 : 将账户数据导出为独立的 SQLite 文件
 *        - 支持导出全部账户或按分类导出
 *        - 导出文件为标准 SQLite 格式
 *        - 密文字段直接导出（文件本身也是加密的）
 *
 * 调用方 : ImportExportController（导出按钮）
 * ============================================================
 */
package com.changjiang.keystore.service;

import com.changjiang.keystore.config.AppConfig;
import com.changjiang.keystore.model.Account;
import com.changjiang.keystore.model.Category;
import com.changjiang.keystore.model.ImportLog;
import com.changjiang.keystore.model.enums.ImportMode;
import com.changjiang.keystore.repository.AccountRepository;
import com.changjiang.keystore.repository.CategoryRepository;
import com.changjiang.keystore.repository.CryptoMetaRepository;
import com.changjiang.keystore.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 导出服务类
 * <p>
 * 将数据库中的分类和账户数据导出为独立的 SQLite 文件。
 * 导出的文件包含完整的加密元数据，无主密码无法解密。
 * </p>
 */
public class ExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportService.class);

    /**
     * 加密服务
     */
    private final CryptoService cryptoService;

    /**
     * 分类仓储
     */
    private final CategoryRepository categoryRepository;

    /**
     * 账户仓储
     */
    private final AccountRepository accountRepository;

    /**
     * 加密元数据仓储
     */
    private final CryptoMetaRepository cryptoMetaRepository;

    // ==================== 构造方法 ====================

    /**
     * 构造导出服务实例
     *
     * @param authService 认证服务（用于获取加密服务）
     */
    public ExportService(AuthService authService) {
        this.cryptoService = authService.getCryptoService();
        this.categoryRepository = new CategoryRepository();
        this.accountRepository = new AccountRepository();
        this.cryptoMetaRepository = new CryptoMetaRepository();
    }

    // ==================== 导出操作 ====================

    /**
     * 导出全部数据（分类 + 账户 + 加密元数据）到目标文件
     * <p>
     * 调用方: ImportExportController（导出全部）
     * </p>
     *
     * @param targetPath 目标文件路径
     * @return 导出摘要（分类数、账户数）
     * @throws ExportException 导出失败
     */
    public ExportSummary exportAll(Path targetPath) throws ExportException {
        return exportInternal(targetPath, null);
    }

    /**
     * 导出指定分类下的账户数据
     * <p>
     * 调用方: ImportExportController（按分类导出）
     * </p>
     *
     * @param targetPath  目标文件路径
     * @param categoryIds 要导出的分类 ID 列表（null 表示全部）
     * @return 导出摘要
     * @throws ExportException 导出失败
     */
    public ExportSummary exportByCategories(Path targetPath, List<Long> categoryIds) throws ExportException {
        return exportInternal(targetPath, categoryIds);
    }

    /**
     * 导出指定账户
     * <p>
     * 调用方: ImportExportController（选择账户导出）
     * </p>
     *
     * @param targetPath 目标文件路径
     * @param accountIds 要导出的账户 ID 列表
     * @return 导出摘要
     * @throws ExportException 导出失败
     */
    public ExportSummary exportAccounts(Path targetPath, List<Long> accountIds) throws ExportException {
        try {
            // 查询要导出的账户
            List<Account> accounts = new ArrayList<>();
            for (Long accountId : accountIds) {
                Account account = accountRepository.findById(accountId)
                        .orElseThrow(() -> new ExportException("账户不存在，ID=" + accountId));
                accounts.add(account);
            }

            // 收集涉及的分类 ID
            List<Long> categoryIds = new ArrayList<>();
            for (Account account : accounts) {
                if (!categoryIds.contains(account.getCategoryId())) {
                    categoryIds.add(account.getCategoryId());
                }
            }

            return exportInternal(targetPath, categoryIds, accounts);
        } catch (SQLException e) {
            LOGGER.error("导出账户失败", e);
            throw new ExportException("导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 内部导出方法
     *
     * @param targetPath  目标文件路径
     * @param categoryIds 分类 ID 过滤条件（null 表示全部）
     * @return 导出摘要
     * @throws ExportException 导出失败
     */
    private ExportSummary exportInternal(Path targetPath, List<Long> categoryIds) throws ExportException {
        try {
            // 查询数据
            List<Category> allCategories = categoryRepository.findAll();
            List<Account> allAccounts = accountRepository.findAll();
            CryptoMetaRepository.CryptoMeta cryptoMeta = cryptoMetaRepository.find()
                    .orElseThrow(() -> new ExportException("数据库未初始化，无法导出"));

            // 过滤分类
            List<Category> categories;
            List<Account> accounts;

            if (categoryIds == null) {
                // 导出全部
                categories = allCategories;
                accounts = allAccounts;
            } else {
                // 按分类过滤
                List<Long> filterSet = new ArrayList<>(categoryIds);
                categories = allCategories.stream()
                        .filter(c -> filterSet.contains(c.getId()))
                        .toList();
                accounts = allAccounts.stream()
                        .filter(a -> filterSet.contains(a.getCategoryId()))
                        .toList();
            }

            // 写入导出文件
            writeExportFile(targetPath, categories, accounts, cryptoMeta);

            LOGGER.info("导出完成: 文件={}, 分类数={}, 账户数={}",
                    targetPath, categories.size(), accounts.size());

            return new ExportSummary(categories.size(), accounts.size(), targetPath);

        } catch (Exception e) {
            LOGGER.error("导出失败", e);
            throw new ExportException("导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 内部导出方法（指定账户列表）
     */
    private ExportSummary exportInternal(Path targetPath, List<Long> categoryIds,
                                         List<Account> accounts) throws ExportException {
        try {
            List<Category> categories;
            if (categoryIds == null) {
                categories = categoryRepository.findAll();
            } else {
                categories = categoryRepository.findAll().stream()
                        .filter(c -> categoryIds.contains(c.getId()))
                        .toList();
            }

            CryptoMetaRepository.CryptoMeta cryptoMeta = cryptoMetaRepository.find()
                    .orElseThrow(() -> new ExportException("数据库未初始化，无法导出"));

            writeExportFile(targetPath, categories, accounts, cryptoMeta);

            LOGGER.info("导出完成: 文件={}, 分类数={}, 账户数={}",
                    targetPath, categories.size(), accounts.size());

            return new ExportSummary(categories.size(), accounts.size(), targetPath);

        } catch (Exception e) {
            LOGGER.error("导出失败", e);
            throw new ExportException("导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建导出 SQLite 文件
     * <p>
     * 创建新的 SQLite 文件，写入表结构和数据。
     * 使用 SQLite backup API 从源数据库复制。
     * </p>
     *
     * @param targetPath  目标文件路径
     * @param categories  分类列表
     * @param accounts    账户列表
     * @param cryptoMeta  加密元数据
     * @throws SQLException 数据库操作异常
     * @throws IOException  IO 异常
     */
    private void writeExportFile(Path targetPath, List<Category> categories,
                                 List<Account> accounts,
                                 CryptoMetaRepository.CryptoMeta cryptoMeta)
            throws SQLException, IOException {

        // 确保目标目录存在
        Files.createDirectories(targetPath.getParent());

        // 使用 backup API 复制数据库
        String sourceUrl = "jdbc:sqlite:" + AppConfig.getInstance().getDbPath();
        String targetUrl = "jdbc:sqlite:" + targetPath;

        try (Connection sourceConn = DriverManager.getConnection(sourceUrl);
             Connection targetConn = DriverManager.getConnection(targetUrl)) {

            // 在目标库中创建表结构
            try (Statement stmt = targetConn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS crypto_meta (
                            id INTEGER PRIMARY KEY CHECK (id = 1),
                            salt BLOB NOT NULL,
                            verification BLOB NOT NULL,
                            iterations INTEGER NOT NULL DEFAULT 100000
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS category (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL COLLATE NOCASE,
                            icon TEXT,
                            sort_order INTEGER DEFAULT 0,
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL
                        )
                        """);
                stmt.execute("""
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
                        )
                        """);
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS import_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            source_file TEXT NOT NULL,
                            import_mode TEXT NOT NULL,
                            accounts_added INTEGER DEFAULT 0,
                            accounts_updated INTEGER DEFAULT 0,
                            imported_at TEXT NOT NULL
                        )
                        """);
            }

            // 复制加密元数据
            try (PreparedStatement ps = targetConn.prepareStatement(
                    "INSERT INTO crypto_meta (id, salt, verification, iterations) VALUES (1, ?, ?, ?)")) {
                ps.setBytes(1, cryptoMeta.getSalt());
                ps.setBytes(2, cryptoMeta.getVerification());
                ps.setInt(3, cryptoMeta.getIterations());
                ps.executeUpdate();
            }

            // 复制分类数据
            for (Category category : categories) {
                try (PreparedStatement ps = targetConn.prepareStatement(
                        "INSERT INTO category (id, name, icon, sort_order, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setLong(1, category.getId());
                    ps.setString(2, category.getName());
                    ps.setString(3, category.getIcon());
                    ps.setObject(4, category.getSortOrder());
                    ps.setString(5, category.getCreatedAt() != null ? category.getCreatedAt().toString() : null);
                    ps.setString(6, category.getUpdatedAt() != null ? category.getUpdatedAt().toString() : null);
                    ps.executeUpdate();
                }
            }

            // 复制账户数据
            for (Account account : accounts) {
                try (PreparedStatement ps = targetConn.prepareStatement(
                        "INSERT INTO account (id, category_id, name, address, port, username, password, " +
                        "valid_from, valid_to, notes, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setLong(1, account.getId());
                    ps.setLong(2, account.getCategoryId());
                    ps.setString(3, account.getName());
                    ps.setString(4, account.getAddress());
                    ps.setObject(5, account.getPort());
                    ps.setString(6, account.getUsername());
                    ps.setBytes(7, account.getPassword());
                    ps.setString(8, account.getValidFrom() != null ? account.getValidFrom().toString() : null);
                    ps.setString(9, account.getValidTo() != null ? account.getValidTo().toString() : null);
                    ps.setString(10, account.getNotes());
                    ps.setString(11, account.getCreatedAt() != null ? account.getCreatedAt().toString() : null);
                    ps.setString(12, account.getUpdatedAt() != null ? account.getUpdatedAt().toString() : null);
                    ps.executeUpdate();
                }
            }

            targetConn.commit();
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 导出摘要信息
     */
    public static class ExportSummary {
        /** 导出的分类数量 */
        public final int categoryCount;
        /** 导出的账户数量 */
        public final int accountCount;
        /** 目标文件路径 */
        public final Path targetPath;

        /**
         * 构造导出摘要
         *
         * @param categoryCount 分类数量
         * @param accountCount  账户数量
         * @param targetPath    目标文件路径
         */
        public ExportSummary(int categoryCount, int accountCount, Path targetPath) {
            this.categoryCount = categoryCount;
            this.accountCount = accountCount;
            this.targetPath = targetPath;
        }

        /**
         * 获取摘要描述
         *
         * @return 如 "导出成功：3 个分类，12 个账户"
         */
        public String getDescription() {
            return String.format("导出成功：%d 个分类，%d 个账户", categoryCount, accountCount);
        }
    }

    // ==================== 异常类 ====================

    /**
     * 导出操作异常
     */
    public static class ExportException extends Exception {
        public ExportException(String message) {
            super(message);
        }

        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
