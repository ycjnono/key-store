/*
 * ============================================================
 * 账户分类实体类
 * ============================================================
 * 功能 : 表示账户分类（如网站、APP、服务器、银行卡等）
 * 数据库表: category
 * ============================================================
 */
package com.changjiang.keystore.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 账户分类实体
 * <p>
 * 用于对账户进行分类管理，每个账户归属于一个分类。
 * 分类支持自定义名称、图标和排序。
 * </p>
 */
public class Category {

    /**
     * 主键 ID（自增），数据库表中 {@code category.id}
     */
    private Long id;

    /**
     * 分类名称（如 "网站"、"服务器"、"银行卡"），数据库表中 {@code category.name}
     * <p>
     * 非空，长度建议不超过 50 字符
     * </p>
     */
    private String name;

    /**
     * 可选图标标识符（如 FontAwesome 图标名称或自定义标识），数据库表中 {@code category.icon}
     * <p>
     * 可为空，用于 UI 展示
     * </p>
     */
    private String icon;

    /**
     * 排序序号，数据库表中 {@code category.sort_order}
     * <p>
     * 数值越小越靠前
     * </p>
     */
    private Integer sortOrder;

    /**
     * 创建时间（ISO-8601 格式），数据库表中 {@code category.created_at}
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间（ISO-8601 格式），数据库表中 {@code category.updated_at}
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // ==================== 无参构造方法（反序列化需要） ====================

    public Category() {
    }

    // ==================== 有参构造方法 ====================

    /**
     * 构造一个分类实例（不含 ID，用于新建）
     *
     * @param name      分类名称
     * @param icon      图标标识符（可为 null）
     * @param sortOrder 排序序号（可为 null）
     */
    public Category(String name, String icon, Integer sortOrder) {
        this.name = name;
        this.icon = icon;
        this.sortOrder = sortOrder;
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取显示名称
     *
     * @return 分类名称，如果为 null 则返回空字符串
     */
    public String getDisplayName() {
        return name != null ? name : "";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
