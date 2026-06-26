/*
 * ============================================================
 * 账户类型枚举
 * ============================================================
 * 功能 : 预定义的账户分类类型常量
 * ============================================================
 */
package com.changjiang.keystore.model.enums;

/**
 * 账户类型枚举
 * <p>
 * 预定义常用分类，在首次初始化时创建对应的 {@link com.changjiang.keystore.model.Category} 记录。
 * 用户后续可自由增删改分类。
 * </p>
 */
public enum AccountType {

    /**
     * 网站账户
     */
    WEBSITE("网站"),

    /**
     * 手机应用账户
     */
    APP("APP"),

    /**
     * 服务器账户
     */
    SERVER("服务器"),

    /**
     * API 密钥
     */
    API("API"),

    /**
     * 邮箱账户
     */
    EMAIL("邮箱"),

    /**
     * 项目相关账户
     */
    PROJECT("项目"),

    /**
     * 银行卡
     */
    BANK_CARD("银行卡"),

    /**
     * 门禁
     */
    ACCESS("门禁"),

    /**
     * 其他
     */
    OTHER("其他");

    /**
     * 显示名称
     */
    private final String displayName;

    /**
     * 构造方法
     *
     * @param displayName 显示名称
     */
    AccountType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取显示名称
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
}
