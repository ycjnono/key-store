/*
 * ============================================================
 * 分类数据访问类
 * ============================================================
 * 功能 : 对 category 表进行 CRUD 操作
 *
 * 调用方 : CategoryService（业务逻辑层）
 * ============================================================
 */
package com.changjiang.keystore.repository;

import com.changjiang.keystore.model.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 分类仓储类
 * <p>
 * 负责 {@code category} 表的增删改查操作。
 * </p>
 */
public class CategoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryRepository.class);

    // ==================== 新增分类 ====================

    /**
     * 插入新分类
     *
     * @param category 分类实体（id 为 null）
     * @return 插入后的分类（包含自增 ID）
     * @throws SQLException 数据库操作异常
     */
    public Category insert(Category category) throws SQLException {
        String sql = "INSERT INTO category (name, icon, sort_order, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            LocalDateTime now = LocalDateTime.now();

            ps.setString(1, category.getName());
            ps.setString(2, category.getIcon());
            ps.setObject(3, category.getSortOrder());
            ps.setString(4, now.toString());
            ps.setString(5, now.toString());

            ps.executeUpdate();

            // 获取自增主键
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    category.setId(rs.getLong(1));
                }
            }

            category.setCreatedAt(now);
            category.setUpdatedAt(now);

            LOGGER.debug("插入分类成功: id={}, name={}", category.getId(), category.getName());
            return category;
        }
    }

    // ==================== 查询分类 ====================

    /**
     * 根据 ID 查询分类
     *
     * @param id 分类 ID
     * @return Optional<Category>，可能为空
     * @throws SQLException 数据库操作异常
     */
    public Optional<Category> findById(Long id) throws SQLException {
        String sql = "SELECT id, name, icon, sort_order, created_at, updated_at " +
                     "FROM category WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToCategory(rs));
                }
            }

            return Optional.empty();
        }
    }

    /**
     * 查询所有分类（按 sort_order 排序）
     *
     * @return 分类列表
     * @throws SQLException 数据库操作异常
     */
    public List<Category> findAll() throws SQLException {
        String sql = "SELECT id, name, icon, sort_order, created_at, updated_at " +
                     "FROM category ORDER BY sort_order ASC, id ASC";

        List<Category> categories = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                categories.add(mapRowToCategory(rs));
            }
        }

        LOGGER.debug("查询所有分类: 共 {} 个", categories.size());
        return categories;
    }

    /**
     * 根据名称模糊查询分类
     *
     * @param keyword 关键词
     * @return 匹配的分类列表
     * @throws SQLException 数据库操作异常
     */
    public List<Category> findByName(String keyword) throws SQLException {
        String sql = "SELECT id, name, icon, sort_order, created_at, updated_at " +
                     "FROM category WHERE name LIKE ? ORDER BY sort_order ASC";

        List<Category> categories = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + keyword + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    categories.add(mapRowToCategory(rs));
                }
            }
        }

        return categories;
    }

    // ==================== 更新分类 ====================

    /**
     * 更新分类信息
     *
     * @param category 分类实体（id 必须非空）
     * @return 更新后的分类
     * @throws SQLException 数据库操作异常
     */
    public Category update(Category category) throws SQLException {
        String sql = "UPDATE category SET name = ?, icon = ?, sort_order = ?, updated_at = ? " +
                     "WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();

            ps.setString(1, category.getName());
            ps.setString(2, category.getIcon());
            ps.setObject(3, category.getSortOrder());
            ps.setString(4, now.toString());
            ps.setLong(5, category.getId());

            int affected = ps.executeUpdate();

            if (affected == 0) {
                throw new SQLException("更新分类失败，ID 不存在: " + category.getId());
            }

            category.setUpdatedAt(now);
            LOGGER.debug("更新分类成功: id={}", category.getId());
            return category;
        }
    }

    // ==================== 删除分类 ====================

    /**
     * 根据 ID 删除分类
     * <p>
     * 由于 account 表外键设置了 ON DELETE CASCADE，
     * 删除分类时会级联删除其下所有账户。
     * </p>
     *
     * @param id 分类 ID
     * @return 删除的行数
     * @throws SQLException 数据库操作异常
     */
    public int deleteById(Long id) throws SQLException {
        String sql = "DELETE FROM category WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            int affected = ps.executeUpdate();

            LOGGER.debug("删除分类: id={}, 影响行数={}", id, affected);
            return affected;
        }
    }

    /**
     * 检查分类下是否有账户
     *
     * @param categoryId 分类 ID
     * @return true 如果有账户关联该分类
     * @throws SQLException 数据库操作异常
     */
    public boolean hasAccounts(Long categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM account WHERE category_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, categoryId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }

        return false;
    }

    // ==================== ResultSet 映射 ====================

    /**
     * 将 ResultSet 当前行映射为 Category 对象
     *
     * @param rs ResultSet 结果集
     * @return Category 对象
     * @throws SQLException 数据库操作异常
     */
    private Category mapRowToCategory(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setId(rs.getLong("id"));
        category.setName(rs.getString("name"));

        String icon = rs.getString("icon");
        if (rs.wasNull()) {
            icon = null;
        }
        category.setIcon(icon);

        int sortOrder = rs.getInt("sort_order");
        if (rs.wasNull()) {
            category.setSortOrder(null);
        } else {
            category.setSortOrder(sortOrder);
        }

        String createdAt = rs.getString("created_at");
        category.setCreatedAt(LocalDateTime.parse(createdAt));

        String updatedAt = rs.getString("updated_at");
        category.setUpdatedAt(LocalDateTime.parse(updatedAt));

        return category;
    }
}
