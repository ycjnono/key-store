/*
 * ============================================================
 * 应用配置类 - 单例模式
 * ============================================================
 * 功能 : 管理应用运行时的全局配置，包括：
 *        - 安装目录解析（注册表 / 程序所在目录）
 *        - 数据目录路径（安装目录/data/）
 *        - 数据库文件路径
 *        - 自动锁定超时时间等
 *
 * 调用方 : Main.java（启动时初始化）、各 Service 层（获取配置）
 * ============================================================
 */
package com.changjiang.keystore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用全局配置
 * <p>
 * 采用单例模式，程序启动时初始化一次，后续全局使用。
 * </p>
 */
public class AppConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);

    /**
     * 配置单例实例
     */
    private static volatile AppConfig instance;

    /**
     * 安装目录（用户选择或默认安装路径）
     */
    private final Path installDir;

    /**
     * 数据目录（安装目录下的 data/ 子目录）
     */
    private final Path dataDir;

    /**
     * 数据库文件路径（data/key_store.db）
     */
    private final Path dbPath;

    /**
     * 主密码自动锁定超时时间（单位：分钟），默认 5 分钟
     */
    private final int autoLockMinutes;

    /**
     * PBKDF2 默认迭代次数
     */
    public static final int DEFAULT_PBKDF2_ITERATIONS = 100_000;

    /**
     * 密码验证固定明文
     */
    public static final String VERIFICATION_PLAINTEXT = "KEYSTORE_VERIFY";

    /**
     * 私有构造方法，禁止外部实例化
     *
     * @param installDir 安装目录路径
     */
    private AppConfig(Path installDir) {
        this.installDir = installDir.normalize().toAbsolutePath();
        this.dataDir = this.installDir.resolve("data");
        this.dbPath = this.dataDir.resolve("key_store.db");
        this.autoLockMinutes = 5;

        // 确保 data 目录存在
        try {
            Files.createDirectories(this.dataDir);
        } catch (Exception e) {
            LOGGER.error("无法创建数据目录: {}", this.dataDir, e);
        }
    }

    /**
     * 初始化应用配置（必须在程序启动时最先调用）
     * <p>
     * 解析逻辑：优先从 Windows 注册表读取 jpackage 写入的安装路径，
     * 若读取失败则使用程序工作目录。
     * </p>
     */
    public static synchronized void init() {
        if (instance != null) {
            return; // 已初始化，无需重复
        }

        // 尝试从 Windows 注册表读取安装路径
        Path installDir = resolveInstallDirFromRegistry();

        if (installDir == null) {
            // 注册表读取失败，使用程序所在目录作为安装目录
            installDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            LOGGER.info("从注册表读取安装路径失败，使用程序所在目录: {}", installDir);
        } else {
            LOGGER.info("从注册表读取安装路径成功: {}", installDir);
        }

        instance = new AppConfig(installDir);

        // 确保日志目录存在（logback 写入 user.dir/logs/）
        try {
            Files.createDirectories(installDir.resolve("logs"));
            Files.createDirectories(
                    java.nio.file.Paths.get(System.getProperty("user.dir")).resolve("logs"));
        } catch (Exception e) {
            LOGGER.warn("创建日志目录失败", e);
        }

        System.setProperty("INSTALL_DIR", instance.installDir.toAbsolutePath().toString());
        LOGGER.info("应用配置初始化完成 | 安装目录={} | 数据目录={} | DB路径={} | 日志目录={}",
                instance.installDir, instance.dataDir, instance.dbPath,
                java.nio.file.Paths.get(System.getProperty("user.dir")).resolve("logs"));
    }

    /**
     * 获取配置单例实例
     *
     * @return AppConfig 实例
     * @throws IllegalStateException 如果未先调用 init() 初始化
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AppConfig 未初始化，请先调用 AppConfig.init()");
        }
        return instance;
    }

    /**
     * 从 Windows 注册表读取 jpackage 写入的安装路径
     * <p>
     * jpackage 在安装时会在注册表中写入安装信息，
     * 路径: HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\KeyStore
     * 值: InstallLocation
     * </p>
     *
     * @return 安装目录路径，读取失败返回 null
     */
    private static Path resolveInstallDirFromRegistry() {
        try {
            // 通过 reg query 命令读取注册表获取安装路径
            Process process = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\KeyStore",
                    "/v", "InstallLocation"
            ).redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            process.waitFor();

            if (process.exitValue() == 0 && output.length() > 0) {
                // 解析注册表输出，提取路径
                // 输出格式: InstallLocation    REG_SZ    C:\Program Files\KeyStore
                String result = output.toString();
                int lastSpace = result.lastIndexOf("  ");
                if (lastSpace > 0) {
                    String pathStr = result.substring(lastSpace).trim();
                    Path path = Paths.get(pathStr);
                    if (Files.isDirectory(path)) {
                        return path;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("从注册表读取安装路径失败（可能为开发模式运行）", e);
        }

        return null;
    }

    // ==================== Getter 方法 ====================

    /** @return 安装目录绝对路径 */
    public Path getInstallDir() {
        return installDir;
    }

    /** @return 数据目录绝对路径（安装目录/data/） */
    public Path getDataDir() {
        return dataDir;
    }

    /** @return 数据库文件绝对路径 */
    public Path getDbPath() {
        return dbPath;
    }

    /** @return 自动锁定超时分钟数 */
    public int getAutoLockMinutes() {
        return autoLockMinutes;
    }

    /** @return PBKDF2 默认迭代次数 */
    public int getPbkdf2Iterations() {
        return DEFAULT_PBKDF2_ITERATIONS;
    }
}
