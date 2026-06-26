/*
 * ============================================================
 * 账户实体类
 * ============================================================
 * 功能 : 表示一个账户/凭据（包含名称、地址、端口、账号、密码等）
 * 数据库表: account
 * ============================================================
 */
package com.changjiang.keystore.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 账户实体
 * <p>
 * 每个账户归属于一个分类（{@link Category}），
 * 密码字段以 AES-256-GCM 密文存储，不在此类中保存明文。
 * </p>
 */
public class Account {

    /**
     * 主键 ID（自增），数据库表中 {@code account.id}
     */
    private Long id;

    /**
     * 关联分类 ID，数据库表中 {@code account.category_id}
     * <p>
     * 非空，指向 {@code category.id}
     * </p>
     */
    private Long categoryId;

    /**
     * 账户名称（用户自定义标识），数据库表中 {@code account.name}
     * <p>
     * 非空，如 "Google 账号"、"GitHub"、"公司 VPN" 等
     * </p>
     */
    private String name;

    /**
     * 地址信息，数据库表中 {@code account.address}
     * <p>
     * 含义因分类而异：网站为 URL、服务器为 IP、门禁为小区楼号等。
     * 可为空。
     * </p>
     */
    private String address;

    /**
     * 端口号，数据库表中 {@code account.port}
     * <p>
     * 非必填，可为 null。如 3306、443、8080 等
     * </p>
     */
    private Integer port;

    /**
     * 登录账号，数据库表中 {@code account.username}
     * <p>
     * 可为空
     * </p>
     */
    private String username;

    /**
     * 密码密文，数据库表中 {@code account.password}
     * <p>
     * 存储格式: [IV(12字节)] [TAG(16字节)] [密文]
     * 由 {@link com.changjiang.keystore.service.CryptoService} 加密/解密
     * </p>
     */
    private byte[] password;

    /**
     * 有效期开始时间，数据库表中 {@code account.valid_from}
     * <p>
     * ISO-8601 格式，可为 null 表示无限制
     * </p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validFrom;

    /**
     * 有效期截止时间，数据库表中 {@code account.valid_to}
     * <p>
     * ISO-8601 格式，可为 null 表示无限制
     * </p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validTo;

    /**
     * 备注信息，数据库表中 {@code account.notes}
     * <p>
     * 可为空，用于记录附加说明
     * </p>
     */
    private String notes;

    /**
     * 创建时间，数据库表中 {@code account.created_at}
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间，数据库表中 {@code account.updated_at}
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // ==================== 无参构造方法 ====================

    public Account() {
    }

    // ==================== 有参构造方法 ====================

    /**
     * 构造一个账户实例（不含 ID，用于新建）
     *
     * @param categoryId 分类 ID
     * @param name       账户名称
     * @param address    地址
     * @param port       端口号
     */
    public Account(Long categoryId, String name, String address, Integer port) {
        this.categoryId = categoryId;
        this.name = name;
        this.address = address;
        this.port = port;
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取密码密文（内部使用）
     *
     * @return 密码密文字节数组
     */
    public byte[] getPassword() {
        return password;
    }

    /**
     * 设置密码密文（内部使用）
     *
     * @param password 密码密文字节数组
     */
    public void setPassword(byte[] password) {
        this.password = password;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
     * 获取端口号显示字符串
     *
     * @return 端口号字符串，如 "443" 或空字符串（如果为 null）
     */
    public String getPortDisplay() {
        return port != null ? String.valueOf(port) : "";
    }

    /**
     * 检查账户是否在有效期内
     *
     * @return true 如果在有效期内或无有效期限制
     */
    public boolean isInValidPeriod() {
        LocalDateTime now = LocalDateTime.now();
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        return true;
    }

    /**
     * 获取地址显示（优先 address，其次为空）
     *
     * @return 地址字符串
     */
    public String getDisplayAddress() {
        return address != null ? address : "";
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", categoryId=" + categoryId +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", validTo=" + validTo +
                '}';
    }
}
