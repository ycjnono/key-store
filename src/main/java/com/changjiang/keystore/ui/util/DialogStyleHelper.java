/*
 * ============================================================
 * 对话框样式辅助类
 * ============================================================
 * 功能 : 统一配置模态对话框行为与样式（标题栏关闭、按钮栏隐藏等）
 * ============================================================
 */
package com.changjiang.keystore.ui.util;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * 对话框样式与行为辅助工具
 */
public final class DialogStyleHelper {

    private DialogStyleHelper() {
    }

    /**
     * 配置仅使用内容区自定义按钮的对话框（底部无系统按钮栏）
     * <p>
     * 通过隐藏 ButtonBar 保留标题栏关闭能力，内容区自行提供保存/关闭等按钮。
     * </p>
     *
     * @param dialog 目标对话框
     * @param owner  父窗口
     */
    public static void configureContentOnly(Dialog<?> dialog, Window owner) {
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.setResultConverter(button -> null);
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        applyStylesheet(dialog.getDialogPane());
        dialog.setOnShown(event -> hideButtonBar(dialog.getDialogPane()));
    }

    /**
     * 配置带底部「关闭」按钮的对话框
     *
     * @param dialog 目标对话框
     * @param owner  父窗口
     */
    public static void configureWithCloseButton(Dialog<?> dialog, Window owner) {
        ButtonType closeButton = new ButtonType("关闭", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButton);
        dialog.setResultConverter(button -> null);
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        applyStylesheet(dialog.getDialogPane());
    }

    /**
     * 为对话框应用全局样式表
     *
     * @param dialogPane 对话框面板
     */
    public static void applyStylesheet(DialogPane dialogPane) {
        String css = DialogStyleHelper.class.getResource("/css/style.css").toExternalForm();
        if (!dialogPane.getStylesheets().contains(css)) {
            dialogPane.getStylesheets().add(css);
        }
    }

    /**
     * 隐藏 DialogPane 底部系统按钮栏
     *
     * @param dialogPane 对话框面板
     */
    private static void hideButtonBar(DialogPane dialogPane) {
        Node buttonBar = dialogPane.lookup(".button-bar");
        if (buttonBar != null) {
            buttonBar.setVisible(false);
            buttonBar.setManaged(false);
            buttonBar.getStyleClass().add("button-bar-hidden");
        }
    }
}
