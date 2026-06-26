/*
 * ============================================================
 * 导入记录实体类
 * ============================================================
 * 功能 : 记录每次数据导入操作的历史日志
 * 数据库表: import_log
 * ============================================================
 */
package com.changjiang.keystore.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 导入记录实体
 * <p>
 * 每次执行导入操作时创建一条记录，用于追溯导入历史。
 * </p>
 */
public class ImportLog {

    /**
     * 主键 ID（自增），数据库表中 {@code import_log.id}
     */
    private Long id;

    /**
     * 来源文件名，数据库表中 {@code import_log.source_file}
     * <p>
     * 导入文件的文件名（不含路径）
     * </p>
     */
    private String sourceFile;

    /**
     * 导入模式，数据库表中 {@code import_log.import_mode}
     * <p>
     * 值为 {@link com.changjiang.keystore.model.enums.ImportMode#OVERWRITE} 或 {@link com.changjiang.keystore.model.enums.ImportMode#APPEND}
     * </p>
     */
    private String importMode;

    /**
     * 新增账户数，数据库表中 {@code import_log.accounts_added}
     */
    private Integer accountsAdded;

    /**
     * 更新账户数，数据库表中 {@code import_log.accounts_updated}
     */
    private Integer accountsUpdated;

    /**
     * 导入时间，数据库表中 {@code import_log.imported_at}
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime importedAt;

    // ==================== 无参构造方法 ====================

    public ImportLog() {
    }

    // ==================== 有参构造方法 ====================

    /**
     * 构造导入记录
     *
     * @param sourceFile       来源文件名
     * @param importMode       导入模式（OVERWRITE / APPEND）
     * @param accountsAdded    新增账户数
     * @param accountsUpdated  更新账户数
     */
    public ImportLog(String sourceFile, String importMode, Integer accountsAdded, Integer accountsUpdated) {
        this.sourceFile = sourceFile;
        this.importMode = importMode;
        this.accountsAdded = accountsAdded;
        this.accountsUpdated = accountsUpdated;
        this.importedAt = LocalDateTime.now();
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getImportMode() {
        return importMode;
    }

    public void setImportMode(String importMode) {
        this.importMode = importMode;
    }

    public Integer getAccountsAdded() {
        return accountsAdded != null ? accountsAdded : 0;
    }

    public void setAccountsAdded(Integer accountsAdded) {
        this.accountsAdded = accountsAdded;
    }

    public Integer getAccountsUpdated() {
        return accountsUpdated != null ? accountsUpdated : 0;
    }

    public void setAccountsUpdated(Integer accountsUpdated) {
        this.accountsUpdated = accountsUpdated;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取导入模式显示名称
     *
     * @return 导入模式的中文显示名称
     */
    public String getImportModeDisplay() {
        if ("OVERWRITE".equals(importMode)) {
            return "覆盖导入";
        } else if ("APPEND".equals(importMode)) {
            return "追加导入";
        }
        return importMode;
    }

    /**
     * 获取导入摘要
     *
     * @return 如 "新增 5 个，更新 3 个"
     */
    public String getSummary() {
        return String.format("新增 %d 个，更新 %d 个",
                getAccountsAdded(), getAccountsUpdated());
    }

    @Override
    public String toString() {
        return "ImportLog{" +
                "id=" + id +
                ", sourceFile='" + sourceFile + '\'' +
                ", importMode='" + importMode + '\'' +
                ", accountsAdded=" + accountsAdded +
                ", accountsUpdated=" + accountsUpdated +
                ", importedAt=" + importedAt +
                '}';
    }
}
