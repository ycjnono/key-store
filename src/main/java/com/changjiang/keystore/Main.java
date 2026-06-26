/*
 * ============================================================
 * Key-Store 程序入口
 * ============================================================
 * 功能 : JavaFX 应用程序启动入口
 * 调用方 : 系统启动 (java -jar / jpackage 生成的 exe)
 * ============================================================
 */
package com.changjiang.keystore;

import com.changjiang.keystore.config.AppConfig;
import com.changjiang.keystore.ui.App;
import javafx.application.Application;

public class Main {

    /**
     * 程序主入口方法
     *
     * @param args 命令行参数（暂未使用，预留扩展）
     */
    public static void main(String[] args) {
        // 初始化应用配置（解析安装目录、数据目录等）
        AppConfig.init();

        // 启动 JavaFX 应用
        Application.launch(App.class, args);
    }
}
