/*
 * ============================================================
 * 数据库管理类 - 单例模式
 * ============================================================
 * 功能 : SQLite 数据库连接管理，包括：
 *        - 单例连接池
 *        - 数据库文件初始化（创建表结构）
 *        - 连接获取/释放
 *
 * 调用方 : 所有 Repository 类（通过 getConnection() 获取连接）
 * ============================================================
 */
package com.changjiang.keystore.repository;

import com.changjiang.keystore.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 数据库管理器
 * <p>
 * 采用单例模式管理数据库连接。
 * 首次连接时自动创建数据库文件和所有表结构。
 * </p>
 */
public class DatabaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);

    /**
     * 单例实例（双重检查锁定）
     */
    private static volatile DatabaseManager instance;

    /**
     * 数据库连接
     * <p>
     * SQLite 是单进程单文件数据库，共享连接即可。
     * 使用 WAL 模式提升并发性能。
     * </p>
     */
    private Connection connection;

    /**
     * 数据库是否已初始化（表结构已创建）
     */
    private boolean initialized = false;

    /**
     * 私有构造方法
     */
    private DatabaseManager() {
    }

    /**
     * 获取数据库管理器的单例实例
     *
     * @return DatabaseManager 实例
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取数据库连接
     * <p>
     * 首次调用时会初始化数据库（创建文件和表结构）。
     * 后续调用复用已有的连接。
     * </p>
     *
     * @return SQLite 数据库连接
     * @throws SQLException 如果连接失败
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || isConnectionClosed(connection)) {
            synchronized (DatabaseManager.class) {
                if (connection == null || isConnectionClosed(connection)) {
                    openConnection();
                }
            }
        }
        return connection;
    }

    /**
     * 关闭数据库连接
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("数据库连接已关闭");
            } catch (SQLException e) {
                LOGGER.error("关闭数据库连接时出错", e);
            } finally {
                connection = null;
            }
        }
    }

    /**
     * 打开数据库连接并初始化
     */
    private synchronized void openConnection() throws SQLException {
        if (connection != null && !isConnectionClosed(connection)) {
            return; // 防止并发重复打开
        }

        // 确保 data 目录存在
        AppConfig config = AppConfig.getInstance();
        try {
            java.nio.file.Files.createDirectories(config.getDataDir());
        } catch (Exception e) {
            LOGGER.error("无法创建数据目录: {}", config.getDataDir(), e);
        }

        // 构建 JDBC URL
        String dbUrl = "jdbc:sqlite:" + config.getDbPath();

        LOGGER.info("正在连接数据库: {}", dbUrl);

        // 建立连接
        connection = DriverManager.getConnection(dbUrl);

        // 启用 WAL 模式（提升并发性能）
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            // 启用外键约束
            stmt.execute("PRAGMA foreign_keys=ON");
            // 设置同步模式为 NORMAL（平衡性能与安全）
            stmt.execute("PRAGMA synchronous=NORMAL");
        } catch (SQLException e) {
            LOGGER.warn("设置 PRAGMA 失败（可能版本不支持）: {}", e.getMessage());
        }

        LOGGER.info("数据库连接成功: {}", dbUrl);
    }

    /**
     * 初始化数据库表结构（如果不存在则创建）
     * <p>
     * 在首次打开数据库后调用，创建所有必需的表。
     * </p>
     *
     * @throws SQLException 如果建表失败
     */
    public void initDatabase() throws SQLException {
        if (initialized) {
            return;
        }

        synchronized (this) {
            if (initialized) {
                return;
            }

            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            // 创建加密元数据表
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS crypto_meta (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        salt BLOB NOT NULL,
                        verification BLOB NOT NULL,
                        iterations INTEGER NOT NULL DEFAULT 100000
                    )
                    """);

            // 创建分类表
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

            // 创建账户表
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

            // 创建导入记录表
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

            // 创建索引（提升查询性能）
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_account_category ON account(category_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_account_name ON account(name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_account_valid_to ON account(valid_to)");

            stmt.close();

            initialized = true;
            LOGGER.info("数据库表结构初始化完成");
        }
    }

    /**
     * 检查连接是否已关闭
     *
     * @param conn 数据库连接
     * @return true 如果连接已关闭
     */
    private boolean isConnectionClosed(Connection conn) {
        try {
            return conn == null || conn.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }
}
