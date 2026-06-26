/*
 * ============================================================
 * 导入模式枚举
 * ============================================================
 * 功能 : 定义数据导入的两种模式
 * ============================================================
 */
package com.changjiang.keystore.model.enums;

/**
 * 数据导入模式枚举
 * <ul>
 *     <li>{@link #OVERWRITE} - 覆盖导入：清空当前数据后恢复</li>
 *     <li>{@link #APPEND} - 追加导入：保留现有数据，按名称+分类去重</li>
 * </ul>
 */
public enum ImportMode {

    /**
     * 覆盖导入模式
     * <p>
     * 清空当前数据库中的账户和分类数据，从导入文件恢复全部数据。
     * 适用于完整备份恢复场景。
     * </p>
     */
    OVERWRITE("覆盖导入"),

    /**
     * 追加导入模式
     * <p>
     * 保留现有数据，导入新账户。
     * 按 "名称 + 分类 ID" 去重：已存在的账户进行更新，新增的账户进行插入。
     * 适用于多设备合并场景。
     * </p>
     */
    APPEND("追加导入");

    /**
     * 枚举显示名称
     */
    private final String displayName;

    /**
     * 构造方法
     *
     * @param displayName 显示名称
     */
    ImportMode(String displayName) {
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

    /**
     * 根据字符串值解析枚举
     *
     * @param value 字符串值（如 "OVERWRITE"）
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果值不匹配任何枚举
     */
    public static ImportMode fromValue(String value) {
        for (ImportMode mode : values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知的导入模式: " + value);
    }
}
