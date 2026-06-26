/*
 * ============================================================
 * 视图切换工厂
 * ============================================================
 * 功能 : 管理 JavaFX 界面切换逻辑，统一处理场景切换
 * ============================================================
 */
package com.changjiang.keystore.ui.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * 视图工厂类
 * <p>
 * 封装场景切换逻辑，各 Controller 通过此类切换视图。
 * </p>
 */
public class ViewFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewFactory.class);

    /**
     * 切换到指定 FXML 视图
     *
     * @param stage   目标舞台
     * @param fxmlPath FXML 文件路径（classpath 相对路径，如 "/fxml/DashboardView.fxml"）
     * @param title   窗口标题
     * @throws IOException 加载 FXML 失败
     */
    public static void switchView(Stage stage, String fxmlPath, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(
                        ViewFactory.class.getResource(fxmlPath),
                        "FXML 文件未找到: " + fxmlPath
                )
        );
        try {
            Parent root = loader.load();
            LOGGER.debug("FXML 加载成功 | path={}", fxmlPath);

            Scene scene = new Scene(root, 900, 600);

            // 加载样式表
            try {
                scene.getStylesheets().add(Objects.requireNonNull(
                        ViewFactory.class.getResource("/css/style.css")).toExternalForm());
            } catch (Exception e) {
                LOGGER.debug("样式表加载失败（可忽略）", e);
            }

            stage.setTitle("Key-Store - " + title);
            stage.setScene(scene);
            stage.sizeToScene();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            LOGGER.error("FXML 加载失败 | path={} | rootCause={}", fxmlPath, root.getMessage(), e);
            throw new IOException("加载界面失败: " + root.getMessage(), e);
        }
    }
}
