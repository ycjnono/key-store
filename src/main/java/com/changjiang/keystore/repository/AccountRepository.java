/*
 * ============================================================
 * 账户数据访问类
 * ============================================================
 * 功能 : 对 account 表进行 CRUD 操作
 *
 * 调用方 : AccountService（业务逻辑层）
 * ============================================================
 */
package com.changjiang.keystore.repository;

import com.changjiang.keystore.model.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 账户仓储类
 * <p>
 * 负责 {@code account} 表的增删改查操作。
 * 密码字段以密文存储，解密由 {@link com.changjiang.keystore.service.CryptoService} 处理。
 * </p>
 */
public class AccountRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountRepository.class);

    // ==================== 新增账户 ====================

    /**
     * 插入新账户
     *
     * @param account 账户实体（id 为 null）
     * @return 插入后的账户（包含自增 ID）
     * @throws SQLException 数据库操作异常
     */
    public Account insert(Account account) throws SQLException {
        String sql = "INSERT INTO account (category_id, name, address, port, username, password, " +
                     "valid_from, valid_to, notes, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            LocalDateTime now = LocalDateTime.now();

            ps.setLong(1, account.getCategoryId());
            ps.setString(2, account.getName());
            ps.setString(3, account.getAddress());
            ps.setObject(4, account.getPort());
            ps.setString(5, account.getUsername());
            ps.setBytes(6, account.getPassword());
            ps.setString(7, account.getValidFrom() != null ? account.getValidFrom().toString() : null);
            ps.setString(8, account.getValidTo() != null ? account.getValidTo().toString() : null);
            ps.setString(9, account.getNotes());
            ps.setString(10, now.toString());
            ps.setString(11, now.toString());

            ps.executeUpdate();

            // 获取自增主键
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    account.setId(rs.getLong(1));
                }
            }

            account.setCreatedAt(now);
            account.setUpdatedAt(now);

            LOGGER.debug("插入账户成功: id={}, name={}", account.getId(), account.getName());
            return account;
        }
    }

    // ==================== 查询账户 ====================

    /**
     * 根据 ID 查询账户
     *
     * @param id 账户 ID
     * @return Optional<Account>，可能为空
     * @throws SQLException 数据库操作异常
     */
    public Optional<Account> findById(Long id) throws SQLException {
        String sql = "SELECT id, category_id, name, address, port, username, password, " +
                     "valid_from, valid_to, notes, created_at, updated_at " +
                     "FROM account WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToAccount(rs));
                }
            }

            return Optional.empty();
        }
    }

    /**
     * 查询指定分类下的所有账户（按名称排序）
     *
     * @param categoryId 分类 ID
     * @return 账户列表
     * @throws SQLException 数据库操作异常
     */
    public List<Account> findByCategoryId(Long categoryId) throws SQLException {
        String sql = "SELECT id, category_id, name, address, port, username, password, " +
                     "valid_from, valid_to, notes, created_at, updated_at " +
                     "FROM account WHERE category_id = ? ORDER BY name COLLATE NOCASE ASC";

        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, categoryId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(mapRowToAccount(rs));
                }
            }
        }

        LOGGER.debug("查询分类下账户: categoryId={}, 共 {} 个", categoryId, accounts.size());
        return accounts;
    }

    /**
     * 查询所有账户
     *
     * @return 所有账户列表
     * @throws SQLException 数据库操作异常
     */
    public List<Account> findAll() throws SQLException {
        String sql = "SELECT id, category_id, name, address, port, username, password, " +
                     "valid_from, valid_to, notes, created_at, updated_at " +
                     "FROM account ORDER BY category_id ASC, name COLLATE NOCASE ASC";

        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                accounts.add(mapRowToAccount(rs));
            }
        }

        return accounts;
    }

    /**
     * 根据名称模糊搜索账户
     *
     * @param keyword 搜索关键词
     * @return 匹配的账户列表
     * @throws SQLException 数据库操作异常
     */
    public List<Account> searchByName(String keyword) throws SQLException {
        String sql = "SELECT id, category_id, name, address, port, username, password, " +
                     "valid_from, valid_to, notes, created_at, updated_at " +
                     "FROM account WHERE name LIKE ? OR username LIKE ? " +
                     "ORDER BY name COLLATE NOCASE ASC";

        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(mapRowToAccount(rs));
                }
            }
        }

        return accounts;
    }

    /**
     * 查询即将过期的账户
     *
     * @param withinDays 在指定天数内过期
     * @return 即将过期的账户列表
     * @throws SQLException 数据库操作异常
     */
    public List<Account> findExpiringWithin(int withinDays) throws SQLException {
        // SQLite 的 date() 函数返回 YYYY-MM-DD 格式
        String sql = "SELECT id, category_id, name, address, port, username, password, " +
                     "valid_from, valid_to, notes, created_at, updated_at " +
                     "FROM account WHERE valid_to IS NOT NULL " +
                     "AND valid_to <= date('now', '+" + withinDays + " days') " +
                     "AND valid_to >= date('now') " +
                     "ORDER BY valid_to ASC";

        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                accounts.add(mapRowToAccount(rs));
            }
        }

        return accounts;
    }

    /**
     * 查询已过期的账户
     *
     * @return 已过期的账户列表
     * @throws SQLException 数据库操作异常
     */
    public List<Account> findExpired() throws SQLException {
        String sql = "SELECT id, category_id, name, address, port, username, password, " +
                     "valid_from, valid_to, notes, created_at, updated_at " +
                     "FROM account WHERE valid_to IS NOT NULL AND valid_to < date('now') " +
                     "ORDER BY valid_to ASC";

        List<Account> accounts = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                accounts.add(mapRowToAccount(rs));
            }
        }

        return accounts;
    }

    // ==================== 更新账户 ====================

    /**
     * 更新账户信息
     *
     * @param account 账户实体（id 必须非空）
     * @return 更新后的账户
     * @throws SQLException 数据库操作异常
     */
    public Account update(Account account) throws SQLException {
        String sql = "UPDATE account SET category_id = ?, name = ?, address = ?, port = ?, " +
                     "username = ?, password = ?, valid_from = ?, valid_to = ?, notes = ?, updated_at = ? " +
                     "WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();

            ps.setLong(1, account.getCategoryId());
            ps.setString(2, account.getName());
            ps.setString(3, account.getAddress());
            ps.setObject(4, account.getPort());
            ps.setString(5, account.getUsername());
            ps.setBytes(6, account.getPassword());
            ps.setString(7, account.getValidFrom() != null ? account.getValidFrom().toString() : null);
            ps.setString(8, account.getValidTo() != null ? account.getValidTo().toString() : null);
            ps.setString(9, account.getNotes());
            ps.setString(10, now.toString());
            ps.setLong(11, account.getId());

            int affected = ps.executeUpdate();

            if (affected == 0) {
                throw new SQLException("更新账户失败，ID 不存在: " + account.getId());
            }

            account.setUpdatedAt(now);
            LOGGER.debug("更新账户成功: id={}", account.getId());
            return account;
        }
    }

    // ==================== 删除账户 ====================

    /**
     * 根据 ID 删除账户
     *
     * @param id 账户 ID
     * @return 删除的行数（0 或 1）
     * @throws SQLException 数据库操作异常
     */
    public int deleteById(Long id) throws SQLException {
        String sql = "DELETE FROM account WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            int affected = ps.executeUpdate();

            LOGGER.debug("删除账户: id={}, 影响行数={}", id, affected);
            return affected;
        }
    }

    /**
     * 根据分类 ID 删除所有账户
     *
     * @param categoryId 分类 ID
     * @return 删除的行数
     * @throws SQLException 数据库操作异常
     */
    public int deleteByCategoryId(Long categoryId) throws SQLException {
        String sql = "DELETE FROM account WHERE category_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, categoryId);
            int affected = ps.executeUpdate();

            LOGGER.debug("删除分类下所有账户: categoryId={}, 影响行数={}", categoryId, affected);
            return affected;
        }
    }

    /**
     * 统计指定分类下的账户数量
     *
     * @param categoryId 分类 ID
     * @return 账户数量
     * @throws SQLException 数据库操作异常
     */
    public int countByCategoryId(Long categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM account WHERE category_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, categoryId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    // ==================== ResultSet 映射 ====================

    /**
     * 将 ResultSet 当前行映射为 Account 对象
     *
     * @param rs ResultSet 结果集
     * @return Account 对象
     * @throws SQLException 数据库操作异常
     */
    private Account mapRowToAccount(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setId(rs.getLong("id"));
        account.setCategoryId(rs.getLong("category_id"));
        account.setName(rs.getString("name"));

        String address = rs.getString("address");
        if (rs.wasNull()) {
            address = null;
        }
        account.setAddress(address);

        int port = rs.getInt("port");
        if (rs.wasNull()) {
            account.setPort(null);
        } else {
            account.setPort(port);
        }

        String username = rs.getString("username");
        if (rs.wasNull()) {
            username = null;
        }
        account.setUsername(username);

        byte[] password = rs.getBytes("password");
        account.setPassword(password);

        String validFrom = rs.getString("valid_from");
        if (validFrom != null) {
            account.setValidFrom(LocalDateTime.parse(validFrom));
        }

        String validTo = rs.getString("valid_to");
        if (validTo != null) {
            account.setValidTo(LocalDateTime.parse(validTo));
        }

        String notes = rs.getString("notes");
        if (rs.wasNull()) {
            notes = null;
        }
        account.setNotes(notes);

        String createdAt = rs.getString("created_at");
        account.setCreatedAt(LocalDateTime.parse(createdAt));

        String updatedAt = rs.getString("updated_at");
        account.setUpdatedAt(LocalDateTime.parse(updatedAt));

        return account;
    }
}
