/*
 * ============================================================
 * 导入导出对话框控制器
 * ============================================================
 * 功能 : 处理导入/导出交互逻辑，包括：
 *        - 选择导出范围（全部/按分类/选账户）
 *        - 选择导出文件路径
 *        - 执行导出
 *        - 选择导入文件
 *        - 选择导入模式（覆盖/追加）
 *        - 预览导入结果
 *        - 执行导入
 *
 * 调用方 : ImportExportDialog.fxml（视图绑定）、DashboardController
 * ============================================================
 */
package com.changjiang.keystore.ui.controller;

import com.changjiang.keystore.model.Category;
import com.changjiang.keystore.model.ImportLog;
import com.changjiang.keystore.model.enums.ImportMode;
import com.changjiang.keystore.service.*;
import com.changjiang.keystore.ui.util.AlertHelper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 导入导出对话框控制器
 */
public class ImportExportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportController.class);

    // ==================== FXML 注入 ====================

    /** 根布局 */
    @FXML
    private StackPane rootPane;

    /** 导出选项卡 */
    @FXML
    private Tab exportTab;

    /** 导入选项卡 */
    @FXML
    private Tab importTab;

    // --- 导出相关 ---

    /** 导出范围选择 */
    @FXML
    private ComboBox<String> exportScopeComboBox;

    /** 分类选择 */
    @FXML
    private ListView<Category> exportCategoryList;

    /** 导出文件路径 */
    @FXML
    private TextField exportPathField;

    /** 导出状态标签 */
    @FXML
    private Label exportStatusLabel;

    // --- 导入相关 ---

    /** 导入文件路径 */
    @FXML
    private TextField importPathField;

    /** 导入模式选择 */
    @FXML
    private ComboBox<String> importModeComboBox;

    /** 导入预览信息 */
    @FXML
    private Label importPreviewLabel;

    // ==================== 依赖 ====================

    private ExportService exportService;
    private ImportService importService;

    /** 父控制器（DashboardController） */
    private DashboardController parentController;

    /** 父对话框 */
    private Dialog<?> dialog;

    /** 分类列表 */
    private List<Category> allCategories;

    // ==================== FXML 初始化 ====================

    @FXML
    private void initialize() {
        // 初始化导出范围选项
        exportScopeComboBox.setItems(FXCollections.observableArrayList(
                "全部账户",
                "按分类导出",
                "选择账户导出"
        ));
        exportScopeComboBox.getSelectionModel().select(0);

        // 导出范围切换
        exportScopeComboBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if ("按分类导出".equals(newVal)) {
                        exportCategoryList.setVisible(true);
                        loadExportCategories();
                    } else {
                        exportCategoryList.setVisible(false);
                    }
                }
        );

        // 初始化导入模式
        importModeComboBox.setItems(FXCollections.observableArrayList(
                "追加导入（保留现有数据）",
                "覆盖导入（清空后恢复）"
        ));
        importModeComboBox.getSelectionModel().select(0);
    }

    /**
     * 设置服务依赖
     */
    public void setServices(ExportService exportService, ImportService importService) {
        this.exportService = exportService;
        this.importService = importService;
    }

    /**
     * 设置导出服务
     *
     * @param exportService 导出服务实例
     */
    public void setExportService(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * 设置导入服务
     *
     * @param importService 导入服务实例
     */
    public void setImportService(ImportService importService) {
        this.importService = importService;
    }

    /**
     * 设置父控制器
     */
    public void setParentController(DashboardController parent) {
        this.parentController = parent;
    }

    /**
     * 设置对话框
     */
    public void setDialog(Dialog<?> dialog) {
        this.dialog = dialog;
    }

    // ==================== 导出操作 ====================

    /**
     * 加载导出分类列表
     */
    private void loadExportCategories() {
        try {
            if (allCategories == null) {
                allCategories = new ArrayList<>();
                try {
                    com.changjiang.keystore.service.CategoryService cs = new com.changjiang.keystore.service.CategoryService();
                    allCategories = cs.getAllCategories();
                } catch (Exception e) {
                    LOGGER.error("加载分类失败", e);
                }
            }

            MultipleSelectionModel<Category> selectionModel = exportCategoryList.getSelectionModel();
            selectionModel.clearSelection();
            exportCategoryList.setItems(FXCollections.observableArrayList(allCategories));

        } catch (Exception e) {
            LOGGER.error("加载分类列表失败", e);
        }
    }

    /**
     * 选择导出路径
     */
    @FXML
    private void onChooseExportPath() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择导出文件位置");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQLite 数据库文件 (*.db)", "*.db")
        );
        fileChooser.setInitialFileName("keystore_export.db");

        File file = fileChooser.showSaveDialog(getWindow());
        if (file != null) {
            exportPathField.setText(file.getAbsolutePath());
        }
    }

    /**
     * 执行导出
     */
    @FXML
    private void onExport() {
        String pathStr = exportPathField.getText();
        if (pathStr == null || pathStr.trim().isEmpty()) {
            AlertHelper.showWarning(getWindow(), "提示", "请选择导出文件路径");
            return;
        }

        Path targetPath = Path.of(pathStr);

        try {
            ExportService.ExportSummary summary;

            String scope = exportScopeComboBox.getSelectionModel().getSelectedItem();
            if ("全部账户".equals(scope)) {
                summary = exportService.exportAll(targetPath);
            } else if ("按分类导出".equals(scope)) {
                List<Category> selected = exportCategoryList.getSelectionModel().getSelectedItems();
                if (selected.isEmpty()) {
                    AlertHelper.showWarning(getWindow(), "提示", "请选择要导出的分类");
                    return;
                }
                List<Long> ids = new ArrayList<>();
                for (Category c : selected) {
                    ids.add(c.getId());
                }
                summary = exportService.exportByCategories(targetPath, ids);
            } else {
                summary = exportService.exportAll(targetPath);
            }

            exportStatusLabel.setText(summary.getDescription());
            exportStatusLabel.setStyle("-fx-text-fill: #27ae60;");
            AlertHelper.showInfo(getWindow(), "导出成功", summary.getDescription());

        } catch (ExportService.ExportException e) {
            LOGGER.error("导出失败", e);
            AlertHelper.showError(getWindow(), "错误", "导出失败: " + e.getMessage());
        }
    }

    // ==================== 导入操作 ====================

    /**
     * 选择导入文件
     */
    @FXML
    private void onChooseImportFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择导入文件");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQLite 数据库文件 (*.db)", "*.db")
        );

        File file = fileChooser.showOpenDialog(getWindow());
        if (file != null) {
            importPathField.setText(file.getAbsolutePath());
            previewImport();
        }
    }

    /**
     * 预览导入
     */
    private void previewImport() {
        String pathStr = importPathField.getText();
        if (pathStr == null || pathStr.trim().isEmpty()) {
            return;
        }

        try {
            String modeStr = importModeComboBox.getSelectionModel().getSelectedItem();
            ImportMode mode = "覆盖导入（清空后恢复）".equals(modeStr)
                    ? ImportMode.OVERWRITE : ImportMode.APPEND;

            ImportService.ImportPreview preview = importService.previewImport(
                    Path.of(pathStr), mode);

            importPreviewLabel.setText(preview.getSummary());
            importPreviewLabel.setStyle("-fx-text-fill: #333;");

        } catch (ImportService.ImportException e) {
            LOGGER.error("预览导入失败", e);
            importPreviewLabel.setText("预览失败: " + e.getMessage());
            importPreviewLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    /**
     * 导入模式切换时重新预览
     */
    @FXML
    private void onImportModeChanged() {
        previewImport();
    }

    /**
     * 执行导入
     */
    @FXML
    private void onImport() {
        String pathStr = importPathField.getText();
        if (pathStr == null || pathStr.trim().isEmpty()) {
            AlertHelper.showWarning(getWindow(), "提示", "请选择导入文件");
            return;
        }

        String modeStr = importModeComboBox.getSelectionModel().getSelectedItem();
        ImportMode mode = "覆盖导入（清空后恢复）".equals(modeStr)
                ? ImportMode.OVERWRITE : ImportMode.APPEND;

        // 确认
        String confirmMsg = mode == ImportMode.OVERWRITE
                ? "覆盖导入将清空当前所有数据并恢复导入文件中的数据，此操作不可撤销。确定继续吗？"
                : "追加导入将保留现有数据，导入新账户。确定继续吗？";

        boolean confirmed = AlertHelper.showConfirm(getWindow(), "确认导入", confirmMsg);
        if (!confirmed) return;

        try {
            ImportLog log;
            if (mode == ImportMode.OVERWRITE) {
                log = importService.importOverwrite(Path.of(pathStr));
            } else {
                log = importService.importAppend(Path.of(pathStr));
            }

            importPreviewLabel.setText("导入完成！" + log.getSummary());
            importPreviewLabel.setStyle("-fx-text-fill: #27ae60;");

            AlertHelper.showInfo(getWindow(), "导入成功", log.getSummary());

            // 刷新父界面
            if (parentController != null) {
                parentController.refreshCategories();
            }

        } catch (ImportService.ImportException e) {
            LOGGER.error("导入失败", e);
            AlertHelper.showError(getWindow(), "错误", "导入失败: " + e.getMessage());
        }
    }

    /**
     * 关闭对话框
     * <p>
     * 调用方: ImportExportDialog.fxml 底部「关闭」按钮
     * </p>
     */
    @FXML
    private void onClose() {
        if (dialog != null) {
            dialog.setResult(null);
            dialog.close();
        }
    }

    // ==================== 辅助方法 ====================

    private Window getWindow() {
        return dialog != null ? dialog.getDialogPane().getScene().getWindow() : null;
    }
}
