/*
 * ============================================================
 * 弹窗辅助工具类
 * ============================================================
 * 功能 : 统一管理应用弹窗（信息、警告、错误、确认）
 *       避免各 Controller 中重复编写弹窗代码
 * ============================================================
 */
package com.changjiang.keystore.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * 弹窗辅助工具类
 * <p>
 * 提供统一的弹窗样式和 API。确认按钮为蓝色，取消按钮为红色。
 * </p>
 */
public class AlertHelper {

    private static final ButtonType BTN_CONFIRM = new ButtonType("确认", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType BTN_CANCEL = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

    /**
     * 显示信息弹窗
     *
     * @param owner   父窗口
     * @param title   标题
     * @param message 消息内容
     */
    public static void showInfo(Window owner, String title, String message) {
        Alert alert = createAlert(Alert.AlertType.INFORMATION, owner, title, message);
        alert.getButtonTypes().setAll(BTN_CONFIRM);
        alert.showAndWait();
    }

    /**
     * 显示警告弹窗
     *
     * @param owner   父窗口
     * @param title   标题
     * @param message 消息内容
     */
    public static void showWarning(Window owner, String title, String message) {
        Alert alert = createAlert(Alert.AlertType.WARNING, owner, title, message);
        alert.getButtonTypes().setAll(BTN_CONFIRM);
        alert.showAndWait();
    }

    /**
     * 显示错误弹窗
     *
     * @param owner   父窗口
     * @param title   标题
     * @param message 消息内容
     */
    public static void showError(Window owner, String title, String message) {
        Alert alert = createAlert(Alert.AlertType.ERROR, owner, title, message);
        alert.getButtonTypes().setAll(BTN_CONFIRM);
        alert.showAndWait();
    }

    /**
     * 显示确认弹窗
     *
     * @param owner   父窗口
     * @param title   标题
     * @param message 消息内容
     * @return true 如果用户点击「确认」
     */
    public static boolean showConfirm(Window owner, String title, String message) {
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, owner, title, message);
        alert.getButtonTypes().setAll(BTN_CONFIRM, BTN_CANCEL);

        return alert.showAndWait()
                .filter(response -> response == BTN_CONFIRM)
                .isPresent();
    }

    /**
     * 创建统一样式的 Alert
     */
    private static Alert createAlert(Alert.AlertType type, Window owner, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(owner);
        DialogStyleHelper.applyStylesheet(alert.getDialogPane());
        return alert;
    }
}
