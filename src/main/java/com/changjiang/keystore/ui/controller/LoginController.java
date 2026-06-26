/*
 * ============================================================
 * 登录界面控制器
 * ============================================================
 * 功能 : 处理登录界面的交互逻辑，包括：
 *        - 首次使用时设置主密码
 *        - 已有数据时验证主密码
 *        - 界面状态切换（设置密码模式 / 解锁模式）
 *        - 密码强度提示
 *        - 验证成功后切换到主界面
 *
 * 调用方 : LoginView.fxml（视图绑定）
 * 依赖   : AuthService、PasswordStrengthUtil（util 包）、ViewFactory
 * ============================================================
 */
package com.changjiang.keystore.ui.controller;

import com.changjiang.keystore.service.AuthService;
import com.changjiang.keystore.service.AuthService.AuthException;
import com.changjiang.keystore.ui.App;
import com.changjiang.keystore.ui.util.AlertHelper;
import com.changjiang.keystore.util.PasswordStrengthUtil;
import com.changjiang.keystore.ui.util.ViewFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 登录界面控制器
 * <p>
 * 根据 {@code isSetupMode} 区分两种模式：
 * <ul>
 *   <li>首次使用（setupMode=true）：设置主密码界面</li>
 *   <li>已有数据（setupMode=false）：解锁登录界面</li>
 * </ul>
 * </p>
 */
public class LoginController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginController.class);

    // ==================== FXML 注入 ====================

    /**
     * 根布局容器
     */
    @FXML
    private StackPane rootPane;

    /**
     * 窗口标题标签
     */
    @FXML
    private Label titleLabel;

    /**
     * 主密码输入框
     */
    @FXML
    private PasswordField passwordField;

    /**
     * 密码确认输入框（首次设置时显示）
     */
    @FXML
    private PasswordField confirmPasswordField;

    /**
     * 密码确认区域（首次设置时显示）
     */
    @FXML
    private StackPane confirmPane;

    /**
     * 密码强度提示标签
     */
    @FXML
    private Label strengthLabel;

    /**
     * 主操作按钮（设置密码 / 解锁）
     */
    @FXML
    private Button actionButton;

    /**
     * 提示信息标签
     */
    @FXML
    private Label messageLabel;

    // ==================== 状态 ====================

    /**
     * 认证服务
     */
    private AuthService authService;

    /**
     * 是否为设置密码模式
     * <p>
     * true = 首次使用，设置主密码
     * false = 已有数据，验证主密码
     * </p>
     */
    private boolean setupMode;

    // ==================== FXML 初始化 ====================

    /**
     * FXML 加载后自动调用，初始化界面状态
     * <p>
     * 调用方: JavaFX 框架（FXML 加载完成时）
     * </p>
     */
    @FXML
    private void initialize() {
        // 使用 App 全局认证服务，确保解锁状态与 Dashboard 共享
        App app = App.getInstance();
        if (app == null || app.getAuthService() == null) {
            LOGGER.error("App 或 AuthService 未初始化，无法加载登录界面");
            throw new IllegalStateException("应用未正确初始化");
        }
        authService = app.getAuthService();
        setupMode = authService.isFirstUse();
        LOGGER.info("登录界面初始化 | setupMode={} | authUnlocked={}",
                setupMode, authService.isUnlocked());

        // 根据模式设置界面
        if (setupMode) {
            setupMode();
        } else {
            loginMode();
        }

        // 密码输入监听（用于强度提示）
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (setupMode && newVal != null && !newVal.isEmpty()) {
                PasswordStrengthUtil.Strength strength = PasswordStrengthUtil.evaluate(newVal);
                strengthLabel.setText("密码强度: " + strength.getLabel());
                strengthLabel.setStyle("-fx-text-fill: " + PasswordStrengthUtil.getColor(strength));
            } else {
                strengthLabel.setText("");
            }
        });

        LOGGER.debug("登录界面初始化完成，模式={}", setupMode ? "设置密码" : "登录验证");
    }

    // ==================== 模式切换 ====================

    /**
     * 设置密码模式（首次使用）
     */
    private void setupMode() {
        titleLabel.setText("设置主密码");
        confirmPane.setVisible(true);
        actionButton.setText("设置密码");
        messageLabel.setText("请设置一个主密码，用于保护您的账户数据。");
    }

    /**
     * 登录模式（解锁）
     */
    private void loginMode() {
        titleLabel.setText("解锁 Key-Store");
        confirmPane.setVisible(false);
        actionButton.setText("解锁");
        messageLabel.setText("请输入主密码解锁客户端。");
    }

    // ==================== 事件处理 ====================

    /**
     * 主操作按钮点击事件（设置密码 / 解锁）
     * <p>
     * 调用方: LoginView.fxml 中 actionButton 的 onAction
     * </p>
     */
    @FXML
    private void onActionButtonClicked() {
        if (setupMode) {
            handleSetupPassword();
        } else {
            handleLogin();
        }
    }

    /**
     * 处理设置密码
     */
    private void handleSetupPassword() {
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        // 校验
        if (password == null || password.isEmpty()) {
            showError("请输入主密码");
            return;
        }
        if (password.length() < 6) {
            showError("密码长度不能少于 6 位");
            return;
        }
        if (!password.equals(confirm)) {
            showError("两次输入的密码不一致");
            return;
        }

        // 禁用按钮，防止重复点击
        actionButton.setDisable(true);
        actionButton.setText("设置中...");

        // 在后台线程执行（PBKDF2 较耗时）
        new Thread(() -> {
            try {
                LOGGER.info("用户触发设置主密码");
                authService.setupPassword(password.toCharArray());

                Platform.runLater(() -> {
                    LOGGER.info("主密码设置完成，准备进入主界面");
                    showInfo("设置成功", "主密码设置成功！");
                    navigateToDashboard();
                });

            } catch (AuthException e) {
                LOGGER.error("设置主密码失败 | message={}", e.getMessage(), e);
                Platform.runLater(() -> {
                    showError("设置失败: " + e.getMessage());
                    actionButton.setDisable(false);
                    actionButton.setText("设置密码");
                });
            } catch (Exception e) {
                LOGGER.error("设置主密码发生未预期异常", e);
                Platform.runLater(() -> {
                    showError("设置失败: " + e.getMessage());
                    actionButton.setDisable(false);
                    actionButton.setText("设置密码");
                });
            }
        }, "setup-password-thread").start();
    }

    /**
     * 处理登录验证
     */
    private void handleLogin() {
        String password = passwordField.getText();

        // 校验
        if (password == null || password.isEmpty()) {
            showError("请输入主密码");
            return;
        }

        // 禁用按钮
        actionButton.setDisable(true);
        actionButton.setText("验证中...");

        // 在后台线程执行
        new Thread(() -> {
            try {
                LOGGER.info("用户触发主密码验证");
                authService.verifyPassword(password.toCharArray());

                Platform.runLater(() -> {
                    LOGGER.info("主密码验证通过，准备进入主界面");
                    navigateToDashboard();
                });

            } catch (AuthException e) {
                LOGGER.warn("主密码验证失败 | message={}", e.getMessage());
                Platform.runLater(() -> {
                    showError("密码错误，请重试");
                    passwordField.clear();
                    actionButton.setDisable(false);
                    actionButton.setText("解锁");
                    passwordField.requestFocus();
                });
            } catch (Exception e) {
                LOGGER.error("主密码验证发生未预期异常", e);
                Platform.runLater(() -> {
                    showError("验证失败: " + e.getMessage());
                    passwordField.clear();
                    actionButton.setDisable(false);
                    actionButton.setText("解锁");
                    passwordField.requestFocus();
                });
            }
        }, "login-thread").start();
    }

    /**
     * 切换到主界面
     * <p>
     * 调用方: 本类（登录成功后）
     * </p>
     */
    private void navigateToDashboard() {
        try {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            LOGGER.info("切换到主界面 | stage={}", stage);
            ViewFactory.switchView(stage, "/fxml/DashboardView.fxml", "主界面");
            LOGGER.info("主界面加载成功");
        } catch (Exception e) {
            LOGGER.error("切换主界面失败", e);
            showError("加载主界面失败: " + resolveRootCauseMessage(e));
            actionButton.setDisable(false);
            actionButton.setText(setupMode ? "设置密码" : "解锁");
        }
    }

    /**
     * 提取异常链最底层错误信息，便于界面展示
     *
     * @param throwable 原始异常
     * @return 根因消息
     */
    private String resolveRootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    // ==================== 辅助方法 ====================

    /**
     * 显示错误提示
     *
     * @param message 错误消息
     */
    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.setStyle("-fx-text-fill: #e74c3c;");
    }

    /**
     * 显示信息弹窗
     *
     * @param title   标题
     * @param message 消息
     */
    private void showInfo(String title, String message) {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        AlertHelper.showInfo(stage, title, message);
    }

    /**
     * 显示错误弹窗
     *
     * @param message 错误消息
     */
    private void showError(String title, String message) {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        AlertHelper.showError(stage, title, message);
    }
}
