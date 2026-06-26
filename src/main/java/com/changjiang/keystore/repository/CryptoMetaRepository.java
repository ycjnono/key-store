/*
 * ============================================================
 * 加密元数据数据访问类
 * ============================================================
 * 功能 : 对 crypto_meta 表进行 CRUD 操作
 *        存储加密体系所需的公开信息（不含密钥本身）
 *
 * 调用方 : CryptoService、AuthService
 * ============================================================
 */
package com.changjiang.keystore.repository;

import com.changjiang.keystore.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;

/**
 * 加密元数据仓储类
 * <p>
 * 负责 {@code crypto_meta} 表的操作。
 * 该表仅存储加密体系的公开参数（Salt、验证串、迭代次数），
 * 密钥本身永远不落地。
 * </p>
 */
public class CryptoMetaRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoMetaRepository.class);

    /**
     * 插入或更新加密元数据
     * <p>
     * 由于 crypto_meta 只有一条记录（id=1），使用 INSERT OR REPLACE 策略。
     * </p>
     *
     * @param salt          PBKDF2 派生盐值（随机字节数组）
     * @param verification  验证串密文
     * @param iterations    PBKDF2 迭代次数
     * @throws SQLException 数据库操作异常
     */
    public void upsert(byte[] salt, byte[] verification, int iterations) throws SQLException {
        String sql = "INSERT OR REPLACE INTO crypto_meta (id, salt, verification, iterations) " +
                     "VALUES (1, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBytes(1, salt);
            ps.setBytes(2, verification);
            ps.setInt(3, iterations);

            ps.executeUpdate();

            LOGGER.debug("加密元数据已保存 | 迭代次数={}", iterations);
        }
    }

    /**
     * 查询加密元数据
     *
     * @return Optional<CryptoMeta>，如果数据库为空则返回空
     * @throws SQLException 数据库操作异常
     */
    public Optional<CryptoMeta> find() throws SQLException {
        String sql = "SELECT salt, verification, iterations FROM crypto_meta WHERE id = 1";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                CryptoMeta meta = new CryptoMeta();
                meta.setSalt(rs.getBytes("salt"));
                meta.setVerification(rs.getBytes("verification"));
                meta.setIterations(rs.getInt("iterations"));
                return Optional.of(meta);
            }

            return Optional.empty();
        }
    }

    /**
     * 检查加密元数据是否存在
     *
     * @return true 如果已设置主密码
     * @throws SQLException 数据库操作异常
     */
    public boolean exists() throws SQLException {
        String sql = "SELECT COUNT(*) FROM crypto_meta WHERE id = 1";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }

        return false;
    }

    /**
     * 删除加密元数据（用于完全重置）
     *
     * @throws SQLException 数据库操作异常
     */
    public void delete() throws SQLException {
        String sql = "DELETE FROM crypto_meta";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.executeUpdate();
            LOGGER.debug("加密元数据已删除");
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 加密元数据内部表示（不持久化为表，仅用于传递数据）
     */
    public static class CryptoMeta {
        /** PBKDF2 派生盐值 */
        private byte[] salt;

        /** 验证串密文 */
        private byte[] verification;

        /** PBKDF2 迭代次数 */
        private int iterations;

        public byte[] getSalt() {
            return salt;
        }

        public void setSalt(byte[] salt) {
            this.salt = salt;
        }

        public byte[] getVerification() {
            return verification;
        }

        public void setVerification(byte[] verification) {
            this.verification = verification;
        }

        public int getIterations() {
            return iterations;
        }

        public void setIterations(int iterations) {
            this.iterations = iterations;
        }
    }
}
