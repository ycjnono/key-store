/*
 * ============================================================
 * JavaFX 应用入口类
 * ============================================================
 * 功能 : JavaFX Application 生命周期管理
 *        - 初始化应用主题
 *        - 加载初始视图（登录界面或主界面）
 *        - 处理应用退出时的资源清理
 *
 * 调用方 : Main.java（程序启动入口）
 * ============================================================
 */
package com.changjiang.keystore.ui;

import com.changjiang.keystore.service.AuthService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Key-Store 主应用类
 * <p>
 * 继承自 JavaFX Application，负责应用生命周期管理。
 * 启动时根据认证状态加载登录界面或主界面。
 * </p>
 */
public class App extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    private static volatile App instance;

    /**
     * 认证服务实例
     */
    private AuthService authService;

    // ==================== JavaFX 生命周期 ====================

    /**
     * JavaFX 应用启动入口
     * <p>
     * 调用方: JavaFX 框架（通过 Main.main() → Application.launch()）
     * </p>
     *
     * @param primaryStage 主舞台（主窗口）
     */
    @Override
    public void start(Stage primaryStage) {
        // 保存实例引用（供 getInstance() 使用）
        instance = this;

        LOGGER.info("===== Key-Store 应用启动 =====");

        try {
            // 初始化认证服务
            authService = new AuthService();

            // 设置窗口属性
            primaryStage.setTitle("Key-Store 账户密码管理工具");
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);

            // 加载应用图标（如果存在）
            try {
                Image icon = new Image(Objects.requireNonNull(
                        getClass().getResourceAsStream("/icon.png")));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                LOGGER.debug("应用图标加载失败（可忽略）", e);
            }

            // 判断加载哪个界面
            if (authService.isFirstUse()) {
                // 首次使用，加载设置主密码界面
                LOGGER.info("检测到首次使用，加载设置密码界面");
                loadView(primaryStage, "/fxml/LoginView.fxml", "设置主密码");
            } else {
                // 已有数据，加载登录界面
                LOGGER.info("检测到已有数据，加载登录界面");
                loadView(primaryStage, "/fxml/LoginView.fxml", "解锁");
            }

            primaryStage.show();

            // 注册窗口关闭事件（清理资源）
            primaryStage.setOnCloseRequest(event -> {
                LOGGER.info("应用正在退出...");
                shutdown();
            });

        } catch (Exception e) {
            LOGGER.error("应用启动失败", e);
            showFatalError("应用启动失败", e.getMessage());
        }
    }

    /**
     * 加载 FXML 视图并设置到舞台
     *
     * @param stage   目标舞台
     * @param fxmlPath FXML 文件路径（classpath 相对路径）
     * @param title   窗口标题
     * @throws Exception 加载失败
     */
    private void loadView(Stage stage, String fxmlPath, String title) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/style.css")).toExternalForm());

        stage.setTitle("Key-Store - " + title);
        stage.setScene(scene);
    }

    /**
     * 应用停止时的清理工作
     * <p>
     * 调用方: JavaFX 框架（窗口关闭时）、自身（shutdown()）
     * </p>
     */
    @Override
    public void stop() {
        LOGGER.info("应用停止中...");
        shutdown();
    }

    // ==================== 辅助方法 ====================

    /**
     * 资源清理
     */
    private void shutdown() {
        // 锁定并清除主密钥
        if (authService != null) {
            authService.lock();
        }

        // 关闭数据库连接
        try {
            com.changjiang.keystore.repository.DatabaseManager.getInstance().closeConnection();
        } catch (Exception e) {
            LOGGER.error("关闭数据库连接时出错", e);
        }

        LOGGER.info("===== Key-Store 应用已退出 =====");
    }

    /**
     * 显示致命错误弹窗
     *
     * @param title   错误标题
     * @param message 错误信息
     */
    private void showFatalError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
            Platform.exit();
        });
    }

    /**
     * 获取应用实例
     *
     * @return App 实例，如果应用未启动则返回 null
     */
    public static App getInstance() {
        return instance;
    }

    /**
     * 获取认证服务实例
     *
     * @return AuthService 实例
     */
    public AuthService getAuthService() {
        return authService;
    }
}
