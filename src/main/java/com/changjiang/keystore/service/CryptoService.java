/*
 * ============================================================
 * 加密核心服务类
 * ============================================================
 * 功能 : 处理所有加密/解密操作，包括：
 *        - 主密码 → 主密钥的 PBKDF2 派生
 *        - 验证串的加密/解密（用于主密码验证）
 *        - 账户密码的 AES-256-GCM 加密/解密
 *        - 随机盐和 IV 的生成
 *
 * 调用方 : AuthService（密码验证）、AccountService（密码加解密）
 * ============================================================
 */
package com.changjiang.keystore.service;

import com.changjiang.keystore.config.AppConfig;
import com.changjiang.keystore.repository.CryptoMetaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * 加密服务核心类
 * <p>
 * 提供以下加密能力：
 * <ul>
 *   <li>PBKDF2WithHmacSHA256 — 从用户主密码派生主密钥</li>
 *   <li>AES-256-GCM — 数据加密/解密（认证加密，防篡改）</li>
 * </ul>
 * </p>
 * <p>
 * 密文格式: [IV(12字节)] [TAG(16字节)] [密文数据]
 * </p>
 */
public class CryptoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoService.class);

    /**
     * 加密算法名称
     */
    private static final String ALGORITHM = "AES";

    /**
     * 加密模式: AES/GCM/NoPadding
     */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * GCM 认证标签长度（位），128 位
     */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * GCM 初始化向量长度（字节），12 字节（96 位）
     */
    private static final int IV_LENGTH = 12;

    /**
     * AES 密钥长度（位），256 位
     */
    private static final int AES_KEY_LENGTH = 256;

    /**
     * 盐值长度（字节），16 字节（128 位）
     */
    private static final int SALT_LENGTH = 16;

    /**
     * 当前主密钥（内存持有，不落盘）
     * <p>
     * 使用 char[] 存储以便后续安全擦除。
     * 仅在用户解锁后存在，锁屏/关闭时清空。
     * </p>
     */
    private volatile SecretKey masterKey;

    /**
     * 标记主密钥是否已就绪
     */
    private volatile boolean unlocked = false;

    // ==================== 公共 API ====================

    /**
     * 派生主密钥（从用户主密码 + 盐值）
     * <p>
     * 调用方: AuthService.setupPassword(), AuthService.verifyPassword()
     * </p>
     *
     * @param password  用户主密码（明文，不会被持久化）
     * @param salt      PBKDF2 盐值（随机字节数组）
     * @param iterations PBKDF2 迭代次数
     * @return AES-256 密钥
     */
    public SecretKey deriveMasterKey(char[] password, byte[] salt, int iterations) {
        try {
            // 将 char[] 密码转换为 SecretKeySpec 供 PBKDF2 使用
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, AES_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();

            // 清除敏感数据
            spec.clearPassword();

            SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);
            LOGGER.debug("主密钥派生成功 | 迭代次数={} | 密钥长度={} bits", iterations, AES_KEY_LENGTH);
            return key;
        } catch (Exception e) {
            LOGGER.error("主密钥派生失败", e);
            throw new CryptoException("主密钥派生失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置主密钥（用户解锁后调用）
     * <p>
     * 调用方: AuthService.unlock()
     * </p>
     *
     * @param masterKey 派生后的主密钥
     */
    public void setMasterKey(SecretKey masterKey) {
        this.masterKey = masterKey;
        this.unlocked = true;
        LOGGER.debug("主密钥已设置");
    }

    /**
     * 检查是否已解锁
     *
     * @return true 如果主密钥已就绪
     */
    public boolean isUnlocked() {
        return unlocked;
    }

    /**
     * 清空主密钥（锁屏/关闭时调用，擦除内存中的敏感数据）
     * <p>
     * 调用方: AuthService.lock()
     * </p>
     */
    public void clearMasterKey() {
        this.masterKey = null;
        this.unlocked = false;
        LOGGER.debug("主密钥已清空");
    }

    /**
     * 生成随机盐值
     *
     * @return 随机字节数组（16 字节）
     * @throws CryptoException 如果随机数生成失败
     */
    public byte[] generateSalt() {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(salt);
            return salt;
        } catch (Exception e) {
            LOGGER.error("生成盐值失败", e);
            throw new CryptoException("生成盐值失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成随机 IV（初始化向量）
     *
     * @return 随机字节数组（12 字节）
     * @throws CryptoException 如果随机数生成失败
     */
    public byte[] generateIV() {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            return iv;
        } catch (Exception e) {
            LOGGER.error("生成 IV 失败", e);
            throw new CryptoException("生成 IV 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加密验证串（用于主密码验证）
     * <p>
     * 使用主密钥加密固定明文，密文存入 crypto_meta 表。
     * 调用方: AuthService.setupPassword()
     * </p>
     *
     * @param plaintext 明文验证串（固定值 "KEYSTORE_VERIFY"）
     * @return 加密后的密文字节数组
     * @throws CryptoException 加密失败
     */
    public byte[] encryptVerification(String plaintext) throws CryptoException {
        if (masterKey == null) {
            LOGGER.error("加密验证串失败：主密钥未就绪 | unlocked={}", unlocked);
            throw new CryptoException("主密钥未就绪，无法加密验证串");
        }

        try {
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 拼接 IV + TAG + 密文
            // 注意: GCM 模式下 ciphertext 末尾包含 TAG
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return result;
        } catch (Exception e) {
            LOGGER.error("加密验证串失败", e);
            throw new CryptoException("加密验证串失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密验证串（用于主密码验证）
     * <p>
     * 调用方: AuthService.verifyPassword()
     * </p>
     *
     * @param encryptedVerification 加密后的验证串密文
     * @return 解密后的明文
     * @throws CryptoException 解密失败（密码错误或数据被篡改）
     */
    public String decryptVerification(byte[] encryptedVerification) throws CryptoException {
        if (masterKey == null) {
            LOGGER.error("解密验证串失败：主密钥未就绪 | unlocked={}", unlocked);
            throw new CryptoException("主密钥未就绪，无法解密验证串");
        }

        try {
            // 拆分 IV 和密文
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[encryptedVerification.length - IV_LENGTH];
            System.arraycopy(encryptedVerification, 0, iv, 0, IV_LENGTH);
            System.arraycopy(encryptedVerification, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("解密验证串失败（密码可能错误）", e);
            throw new CryptoException("解密验证串失败", e);
        }
    }

    /**
     * 加密账户密码
     * <p>
     * 使用主密钥对密码进行 AES-256-GCM 加密。
     * 每条密码使用独立的随机 IV，密文格式: [IV(12B)] [TAG(16B)] [CIPHERTEXT]
     * 调用方: AccountService.saveAccount()
     * </p>
     *
     * @param plaintextPassword 明文密码
     * @return 加密后的密文字节数组
     * @throws CryptoException 加密失败
     */
    public byte[] encryptPassword(String plaintextPassword) throws CryptoException {
        if (masterKey == null) {
            throw new CryptoException("主密钥未就绪，无法加密密码");
        }
        if (plaintextPassword == null) {
            return null;
        }

        try {
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(
                    plaintextPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 拼接 IV + 密文（GCM 模式 ciphertext 已包含 TAG）
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return result;
        } catch (Exception e) {
            LOGGER.error("加密密码失败", e);
            throw new CryptoException("加密密码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密账户密码
     * <p>
     * 调用方: AccountService 展示密码时
     * </p>
     *
     * @param encryptedPassword 密文字节数组（[IV] [TAG] [CIPHERTEXT]）
     * @return 解密后的明文密码
     * @throws CryptoException 解密失败（密码错误或数据被篡改）
     */
    public String decryptPassword(byte[] encryptedPassword) throws CryptoException {
        if (masterKey == null) {
            throw new CryptoException("主密钥未就绪，无法解密密码");
        }
        if (encryptedPassword == null || encryptedPassword.length == 0) {
            return null;
        }

        try {
            // 拆分 IV 和密文
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[encryptedPassword.length - IV_LENGTH];
            System.arraycopy(encryptedPassword, 0, iv, 0, IV_LENGTH);
            System.arraycopy(encryptedPassword, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("解密密码失败", e);
            throw new CryptoException("解密密码失败", e);
        }
    }

    // ==================== 异常类 ====================

    /**
     * 加密操作异常
     */
    public static class CryptoException extends RuntimeException {
        public CryptoException(String message) {
            super(message);
        }

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
