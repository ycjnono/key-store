/*
 * ============================================================
 * 分类管理对话框控制器
 * ============================================================
 * 功能 : 处理分类管理的交互逻辑，包括：
 *        - 分类列表展示
 *        - 新增分类
 *        - 编辑分类名称/排序
 *        - 删除分类（含账户数量校验）
 *        - 分类拖拽排序（可选）
 *
 * 调用方 : CategoryManagerDialog.fxml（视图绑定）、DashboardController
 * ============================================================
 */
package com.changjiang.keystore.ui.controller;

import com.changjiang.keystore.model.Category;
import com.changjiang.keystore.service.CategoryService;
import com.changjiang.keystore.ui.util.AlertHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 分类管理对话框控制器
 */
public class CategoryManagerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryManagerController.class);

    // ==================== FXML 注入 ====================

    @FXML
    private TableView<CategoryTableRow> categoryTable;

    @FXML
    private TableColumn<CategoryTableRow, String> nameColumn;

    @FXML
    private TableColumn<CategoryTableRow, String> accountCountColumn;

    @FXML
    private TextField newCategoryField;

    // ==================== 依赖 ====================

    private CategoryService categoryService;

    /** 父控制器引用（DashboardController） */
    private DashboardController parentController;

    /** 父对话框 */
    private Dialog<?> dialog;

    // ==================== FXML 初始化 ====================

    @FXML
    private void initialize() {
        categoryService = new CategoryService();

        // 配置列
        nameColumn.setCellValueFactory(data -> data.getValue().nameProperty());
        accountCountColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getAccountCountDisplay()));

        // 右键菜单
        categoryTable.setRowFactory(tv -> {
            TableRow<CategoryTableRow> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();

            MenuItem editItem = new MenuItem("编辑");
            editItem.setOnAction(e -> editSelectedCategory());
            MenuItem deleteItem = new MenuItem("删除");
            deleteItem.setOnAction(e -> deleteSelectedCategory());

            menu.getItems().addAll(editItem, deleteItem);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            return row;
        });

        loadCategories();
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

    /**
     * 关闭对话框
     * <p>
     * 调用方: CategoryManagerDialog.fxml 底部「关闭」按钮
     * </p>
     */
    @FXML
    private void onClose() {
        if (dialog != null) {
            dialog.setResult(null);
            dialog.close();
        }
    }

    // ==================== 加载数据 ====================

    /**
     * 加载分类列表
     */
    private void loadCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();

            ObservableList<CategoryTableRow> rows = FXCollections.observableArrayList();
            for (Category category : categories) {
                CategoryTableRow row = new CategoryTableRow(category);
                row.setAccountCount(categoryService.countAccounts(category.getId()));
                rows.add(row);
            }
            categoryTable.setItems(rows);

        } catch (CategoryService.ServiceException e) {
            LOGGER.error("加载分类失败", e);
            AlertHelper.showError(getWindow(), "错误", "加载分类失败: " + e.getMessage());
        }
    }

    // ==================== 新增分类 ====================

    /**
     * 新增分类按钮点击
     */
    @FXML
    private void onAddCategory() {
        String name = newCategoryField.getText();
        if (name == null || name.trim().isEmpty()) {
            AlertHelper.showWarning(getWindow(), "提示", "请输入分类名称");
            return;
        }

        try {
            categoryService.createCategory(name.trim(), null, null);
            newCategoryField.clear();
            loadCategories();
        } catch (CategoryService.ServiceException e) {
            AlertHelper.showError(getWindow(), "错误", e.getMessage());
        }
    }

    // ==================== 编辑分类 ====================

    /**
     * 编辑选中的分类
     */
    private void editSelectedCategory() {
        CategoryTableRow row = categoryTable.getSelectionModel().getSelectedItem();
        if (row == null) return;

        TextInputDialog dialog = new TextInputDialog(row.getCategory().getName());
        dialog.setTitle("编辑分类");
        dialog.setHeaderText("修改分类名称");
        dialog.setContentText("新名称:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName == null || newName.trim().isEmpty()) {
                return;
            }

            try {
                categoryService.updateCategory(
                        row.getCategory().getId(),
                        newName.trim(),
                        row.getCategory().getIcon(),
                        row.getCategory().getSortOrder()
                );
                loadCategories();
            } catch (CategoryService.ServiceException e) {
                AlertHelper.showError(getWindow(), "错误", "编辑失败: " + e.getMessage());
            }
        });
    }

    // ==================== 删除分类 ====================

    /**
     * 删除选中的分类
     */
    private void deleteSelectedCategory() {
        CategoryTableRow row = categoryTable.getSelectionModel().getSelectedItem();
        if (row == null) return;

        Category category = row.getCategory();

        boolean confirmed = AlertHelper.showConfirm(
                getWindow(),
                "确认删除",
                String.format("确定要删除分类 \"%s\" 吗？\n%s",
                        category.getName(),
                        row.getAccountCount() > 0
                                ? "⚠ 该分类下有账户，删除将同时删除所有账户！"
                                : "该分类下没有账户。"
                )
        );

        if (confirmed) {
            try {
                categoryService.deleteCategory(category.getId());
                loadCategories();
                AlertHelper.showInfo(getWindow(), "删除成功", "分类已删除");
            } catch (CategoryService.ServiceException e) {
                AlertHelper.showError(getWindow(), "错误", "删除失败: " + e.getMessage());
            }
        }
    }

    // ==================== 辅助方法 ====================

    private Window getWindow() {
        return dialog != null ? dialog.getDialogPane().getScene().getWindow() : null;
    }

    // ==================== 内部数据结构 ====================

    /**
     * 分类表格行数据包装类
     */
    public static class CategoryTableRow {
        private final Category category;
        private final javafx.beans.property.SimpleStringProperty nameProperty;
        private int accountCount = -1; // -1 表示未加载

        public CategoryTableRow(Category category) {
            this.category = category;
            this.nameProperty = new javafx.beans.property.SimpleStringProperty(category.getName());
        }

        public Category getCategory() {
            return category;
        }

        public javafx.beans.property.StringProperty nameProperty() {
            return nameProperty;
        }

        public int getAccountCount() {
            return accountCount;
        }

        public void setAccountCount(int count) {
            this.accountCount = count;
        }

        public String getAccountCountDisplay() {
            if (accountCount < 0) {
                return "加载中...";
            }
            return accountCount + " 个账户";
        }
    }
}
