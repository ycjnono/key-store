/*
 * ============================================================
 * 剪贴板工具类
 * ============================================================
 * 功能 : 提供剪贴板操作，包括：
 *        - 设置剪贴板文本内容
 *        - 读取剪贴板文本内容
 *        - 清空剪贴板
 *
 * 注意 : 已移除自动清空功能，由用户手动控制
 * ============================================================
 */
package com.changjiang.keystore.util;

import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 剪贴板工具类
 * <p>
 * 封装 JavaFX 剪贴板操作。
 * </p>
 */
public class ClipboardUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClipboardUtil.class);

    /**
     * 设置剪贴板文本内容
     *
     * @param text 要复制的文本
     */
    public static void setText(String text) {
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
            LOGGER.debug("已复制到剪贴板，长度={}", text != null ? text.length() : 0);
        } catch (Exception e) {
            LOGGER.error("复制到剪贴板失败", e);
        }
    }

    /**
     * 获取剪贴板文本内容
     *
     * @return 剪贴板中的文本，如果无文本内容则返回 null
     */
    public static String getText() {
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                return clipboard.getString();
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("读取剪贴板失败", e);
            return null;
        }
    }

    /**
     * 清空剪贴板
     * <p>
     * 通过设置空内容来清空剪贴板。
     * </p>
     */
    public static void clear() {
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString("");
            clipboard.setContent(content);
            LOGGER.debug("剪贴板已清空");
        } catch (Exception e) {
            LOGGER.error("清空剪贴板失败", e);
        }
    }
}
