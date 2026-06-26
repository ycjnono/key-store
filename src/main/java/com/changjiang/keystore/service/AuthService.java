/*
 * ============================================================
 * 认证服务类
 * ============================================================
 * 功能 : 处理用户认证相关的核心逻辑，包括：
 *        - 首次启动时设置主密码
 *        - 登录验证（比对验证串）
 *        - 锁定/解锁
 *        - 自动锁定定时器
 *
 * 调用方 : LoginController（登录界面）、DashboardController（锁屏）
 * ============================================================
 */
package com.changjiang.keystore.service;

import com.changjiang.keystore.config.AppConfig;
import com.changjiang.keystore.repository.CryptoMetaRepository;
import com.changjiang.keystore.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务类
 * <p>
 * 管理主密码的生命周期：
 * 设置 → 验证 → 派生主密钥 → 锁定 → 解锁 → 清空
 * </p>
 */
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    /**
     * 加密服务（用于密钥派生和加解密）
     */
    private final CryptoService cryptoService;

    /**
     * 加密元数据仓储
     */
    private final CryptoMetaRepository cryptoMetaRepository;

    /**
     * 自动锁定定时器（守护线程）
     */
    private ScheduledExecutorService lockTimer;

    /**
     * 数据库文件路径（用于检查文件是否存在）
     */
    private final java.nio.file.Path dbPath;

    /**
     * 用户上次活动时间戳
     */
    private volatile long lastActivityTime;

    /**
     * 自动锁定超时时间（毫秒）
     */
    private final long autoLockTimeoutMs;

    /**
     * 标记是否已解锁
     */
    private volatile boolean unlocked = false;

    // ==================== 构造方法 ====================

    /**
     * 构造认证服务实例
     */
    public AuthService() {
        this.cryptoService = new CryptoService();
        this.cryptoMetaRepository = new CryptoMetaRepository();

        AppConfig config = AppConfig.getInstance();
        this.dbPath = config.getDbPath();
        this.autoLockTimeoutMs = TimeUnit.MINUTES.toMillis(config.getAutoLockMinutes());
        this.lastActivityTime = System.currentTimeMillis();
    }

    // ==================== 初始化与状态查询 ====================

    /**
     * 检查是否为首次使用（数据库不存在或加密元数据为空）
     *
     * @return true 如果是首次使用
     */
    public boolean isFirstUse() {
        try {
            LOGGER.debug("检查首次使用状态 | dbPath={} | exists={}", dbPath, Files.exists(dbPath));

            // 检查数据库文件是否存在
            if (!Files.exists(dbPath)) {
                LOGGER.info("首次使用：数据库文件不存在 | dbPath={}", dbPath);
                return true;
            }

            // 检查数据库文件大小是否为 0
            long dbSize = Files.size(dbPath);
            if (dbSize == 0) {
                LOGGER.info("首次使用：数据库文件为空 | dbPath={}", dbPath);
                return true;
            }
            LOGGER.debug("数据库文件已存在 | dbPath={} | size={} bytes", dbPath, dbSize);

            // 确保数据库已初始化（首次连接时自动建表）
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initDatabase();
            LOGGER.debug("数据库表结构初始化完成");

            // 检查加密元数据是否存在
            boolean metaExists = cryptoMetaRepository.exists();
            LOGGER.info("首次使用检查结果 | cryptoMetaExists={} | isFirstUse={}",
                    metaExists, !metaExists);
            return !metaExists;
        } catch (Exception e) {
            LOGGER.error("检查首次使用状态失败 | dbPath={}", dbPath, e);
            return true;
        }
    }

    /**
     * 检查是否已解锁
     *
     * @return true 如果已解锁
     */
    public boolean isUnlocked() {
        return unlocked;
    }

    // ==================== 设置主密码（首次使用） ====================

    /**
     * 设置主密码（首次启动流程）
     * <p>
     * 调用方: LoginController.setupPassword()
     * </p>
     * <p>
     * 执行流程:
     * 1. 生成随机盐值
     * 2. 用 PBKDF2 从密码 + 盐派生主密钥
     * 3. 将主密钥注入 CryptoService
     * 4. 用主密钥加密验证串
     * 5. 保存盐值和验证串到 crypto_meta 表
     * 6. 进入解锁状态并启动自动锁定定时器
     * </p>
     *
     * @param password 用户输入的主密码
     * @throws AuthException 设置失败
     */
    public void setupPassword(char[] password) throws AuthException {
        try {
            LOGGER.info("开始设置主密码 | dbPath={}", dbPath);

            // 1. 确保数据库已初始化
            DatabaseManager.getInstance().initDatabase();
            LOGGER.debug("数据库初始化完成");

            // 2. 生成随机盐值
            byte[] salt = cryptoService.generateSalt();
            LOGGER.debug("盐值生成完成 | saltLength={} bytes", salt.length);

            // 3. 派生主密钥
            int iterations = AppConfig.getInstance().getPbkdf2Iterations();
            LOGGER.debug("开始 PBKDF2 派生主密钥 | iterations={}", iterations);
            SecretKey masterKey = cryptoService.deriveMasterKey(password, salt, iterations);
            LOGGER.debug("主密钥派生完成");

            // 4. 注入主密钥（加密验证串前必须先就绪）
            cryptoService.setMasterKey(masterKey);
            LOGGER.debug("主密钥已注入 CryptoService，开始加密验证串");

            // 5. 加密验证串
            String verificationPlaintext = AppConfig.VERIFICATION_PLAINTEXT;
            byte[] verification = cryptoService.encryptVerification(verificationPlaintext);
            LOGGER.debug("验证串加密完成 | verificationLength={} bytes", verification.length);

            // 6. 保存加密元数据
            cryptoMetaRepository.upsert(salt, verification, iterations);
            LOGGER.debug("加密元数据已写入 crypto_meta 表");

            unlocked = true;
            lastActivityTime = System.currentTimeMillis();

            // 7. 启动自动锁定定时器
            startLockTimer();

            LOGGER.info("主密码设置成功 | dbPath={}", dbPath);
        } catch (Exception e) {
            cryptoService.clearMasterKey();
            unlocked = false;
            LOGGER.error("设置主密码失败 | dbPath={} | cryptoUnlocked={}",
                    dbPath, cryptoService.isUnlocked(), e);
            throw new AuthException("设置主密码失败: " + e.getMessage(), e);
        }
    }

    // ==================== 密码验证（登录） ====================

    /**
     * 验证主密码（登录流程）
     * <p>
     * 调用方: LoginController.login()
     * </p>
     * <p>
     * 执行流程:
     * 1. 从 crypto_meta 表读取盐值和验证串
     * 2. 用输入的密码 + 盐派生主密钥
     * 3. 用主密钥解密验证串
     * 4. 比对解密结果与固定明文
     * </p>
     *
     * @param password 用户输入的主密码
     * @throws AuthException 验证失败（密码错误）
     */
    public void verifyPassword(char[] password) throws AuthException {
        try {
            LOGGER.info("开始验证主密码 | dbPath={}", dbPath);

            // 1. 确保数据库已初始化
            DatabaseManager.getInstance().initDatabase();
            LOGGER.debug("数据库初始化完成");

            // 2. 读取加密元数据
            CryptoMetaRepository.CryptoMeta meta = cryptoMetaRepository.find()
                    .orElseThrow(() -> new AuthException("数据库未初始化，请先设置主密码"));
            LOGGER.debug("已读取加密元数据 | iterations={} | saltLength={} | verificationLength={}",
                    meta.getIterations(),
                    meta.getSalt() != null ? meta.getSalt().length : 0,
                    meta.getVerification() != null ? meta.getVerification().length : 0);

            // 3. 派生主密钥
            SecretKey masterKey = cryptoService.deriveMasterKey(
                    password, meta.getSalt(), meta.getIterations());
            LOGGER.debug("主密钥派生完成，准备解密验证串");

            // 4. 注入主密钥后解密验证串
            cryptoService.setMasterKey(masterKey);
            String decrypted = cryptoService.decryptVerification(meta.getVerification());
            LOGGER.debug("验证串解密完成");

            // 5. 比对
            if (!AppConfig.VERIFICATION_PLAINTEXT.equals(decrypted)) {
                cryptoService.clearMasterKey();
                unlocked = false;
                LOGGER.warn("主密码验证失败：验证串比对不通过");
                throw new AuthException("密码错误");
            }

            // 6. 进入解锁状态
            unlocked = true;
            lastActivityTime = System.currentTimeMillis();

            // 7. 启动自动锁定定时器
            startLockTimer();

            LOGGER.info("主密码验证成功");
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            cryptoService.clearMasterKey();
            unlocked = false;
            LOGGER.error("验证主密码时发生异常 | cryptoUnlocked={}", cryptoService.isUnlocked(), e);
            throw new AuthException("验证失败: " + e.getMessage(), e);
        }
    }

    // ==================== 锁定与解锁 ====================

    /**
     * 锁定（清除主密钥，需重新输入密码解锁）
     * <p>
     * 调用方: DashboardController.lock()、自动锁定定时器
     * </p>
     */
    public void lock() {
        LOGGER.info("正在锁定...");
        cryptoService.clearMasterKey();
        unlocked = false;
        stopLockTimer();
        LOGGER.info("已锁定");
    }

    // ==================== 自动锁定 ====================

    /**
     * 记录用户活动（重置自动锁定计时器）
     * <p>
     * 调用方: UI 层的鼠标移动、键盘输入等事件处理器
     * </p>
     */
    public void recordActivity() {
        if (!unlocked) {
            return;
        }
        lastActivityTime = System.currentTimeMillis();
    }

    /**
     * 启动自动锁定定时器
     * <p>
     * 每隔 30 秒检查一次用户是否超时无操作，
     * 超时则自动锁定。
     * </p>
     */
    private synchronized void startLockTimer() {
        stopLockTimer();
        lockTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "keystore-lock-timer");
            t.setDaemon(true);
            return t;
        });

        lockTimer.scheduleWithFixedDelay(() -> {
            try {
                if (!unlocked) {
                    return;
                }
                long elapsed = System.currentTimeMillis() - lastActivityTime;
                if (elapsed >= autoLockTimeoutMs) {
                    LOGGER.info("自动锁定触发（超时 {} 分钟）",
                            TimeUnit.MILLISECONDS.toMinutes(elapsed));
                    lock();
                }
            } catch (Exception e) {
                LOGGER.error("自动锁定检查出错", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 停止自动锁定定时器
     */
    private synchronized void stopLockTimer() {
        if (lockTimer != null && !lockTimer.isShutdown()) {
            lockTimer.shutdownNow();
        }
        lockTimer = null;
    }

    // ==================== 获取加密服务 ====================

    /**
     * 获取加密服务实例（用于密码加解密）
     * <p>
     * 调用方: AccountService（需要加解密密码时）
     * </p>
     *
     * @return CryptoService 实例
     * @throws IllegalStateException 如果未解锁
     */
    public CryptoService getCryptoService() {
        if (!unlocked) {
            throw new IllegalStateException("客户端已锁定，请先输入主密码解锁");
        }
        return cryptoService;
    }

    // ==================== 异常类 ====================

    /**
     * 认证操作异常
     */
    public static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
