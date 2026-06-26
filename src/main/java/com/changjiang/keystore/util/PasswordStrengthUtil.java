/*
 * ============================================================
 * 密码强度检测工具类
 * ============================================================
 * 功能 : 评估密码强度，返回等级和评分
 * ============================================================
 */
package com.changjiang.keystore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 密码强度工具类
 * <p>
 * 根据密码长度、字符种类（大小写、数字、特殊字符）评估密码强度。
 * </p>
 */
public class PasswordStrengthUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordStrengthUtil.class);

    /**
     * 密码强度等级枚举
     */
    public enum Strength {
        /** 极弱 */
        WEAK("极弱", 1),
        /** 弱 */
        FAIR("弱", 2),
        /** 中等 */
        GOOD("中等", 3),
        /** 强 */
        STRONG("强", 4),
        /** 极强 */
        VERY_STRONG("极强", 5);

        final String label;
        final int level;

        Strength(String label, int level) {
            this.label = label;
            this.level = level;
        }

        public String getLabel() {
            return label;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * 评估密码强度
     *
     * @param password 密码明文（不会被持久化）
     * @return 强度等级
     */
    public static Strength evaluate(String password) {
        if (password == null || password.isEmpty()) {
            return Strength.WEAK;
        }

        int score = 0;
        int length = password.length();

        // 长度评分
        if (length >= 8) score += 1;
        if (length >= 12) score += 1;
        if (length >= 16) score += 1;

        // 字符种类评分
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        if (hasLower) score += 1;
        if (hasUpper) score += 1;
        if (hasDigit) score += 1;
        if (hasSpecial) score += 1;

        // 映射到等级
        if (score <= 2) return Strength.WEAK;
        if (score <= 3) return Strength.FAIR;
        if (score <= 4) return Strength.GOOD;
        if (score <= 5) return Strength.STRONG;
        return Strength.VERY_STRONG;
    }

    /**
     * 获取强度等级对应的颜色
     * <p>
     * 用于 UI 展示。
     * </p>
     *
     * @param strength 强度等级
     * @return CSS 颜色值（十六进制）
     */
    public static String getColor(Strength strength) {
        return switch (strength) {
            case WEAK -> "#e74c3c";
            case FAIR -> "#e67e22";
            case GOOD -> "#f1c40f";
            case STRONG -> "#2ecc71";
            case VERY_STRONG -> "#27ae60";
        };
    }
}
