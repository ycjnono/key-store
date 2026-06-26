/*
 * ============================================================
 * 主界面控制器
 * ============================================================
 * 功能 : 主界面 Dashboard 的完整交互逻辑，包括：
 *        - 左侧分类树展示与选择
 *        - 右侧账户列表展示与搜索
 *        - 新增/编辑/删除账户
 *        - 查看密码（明文展示）
 *        - 复制用户名/密码
 *        - 导出/导入操作
 *        - 分类管理
 *        - 锁定/退出
 *
 * 调用方 : DashboardView.fxml（视图绑定）
 * 依赖   : CategoryService、AccountService、ExportService、ImportService
 * ============================================================
 */
package com.changjiang.keystore.ui.controller;

import com.changjiang.keystore.model.Account;
import com.changjiang.keystore.model.Category;
import com.changjiang.keystore.service.*;
import com.changjiang.keystore.ui.App;
import com.changjiang.keystore.ui.util.AlertHelper;
import com.changjiang.keystore.ui.util.DialogStyleHelper;
import com.changjiang.keystore.ui.util.ViewFactory;
import com.changjiang.keystore.util.ClipboardUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 主界面控制器
 * <p>
 * 管理 Dashboard 的所有交互逻辑。
 * </p>
 */
public class DashboardController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardController.class);

    // ==================== FXML 注入 ====================

    @FXML
    private BorderPane rootPane;

    @FXML
    private Label welcomeLabel;

    /** 左侧分类列表 */
    @FXML
    private ListView<Category> categoryListView;

    /** 右侧账户列表 */
    @FXML
    private TableView<AccountTableRow> accountTableView;

    @FXML
    private TableColumn<AccountTableRow, String> nameColumn;

    @FXML
    private TableColumn<AccountTableRow, String> addressColumn;

    @FXML
    private TableColumn<AccountTableRow, String> usernameColumn;

    /** 搜索框 */
    @FXML
    private TextField searchField;

    /** 状态栏 */
    @FXML
    private Label statusLabel;

    // ==================== 服务层 ====================

    /** 分类服务 */
    private CategoryService categoryService;

    /** 账户服务 */
    private AccountService accountService;

    /** 导出服务 */
    private ExportService exportService;

    /** 导入服务 */
    private ImportService importService;

    /** 当前选中的分类 */
    private Category selectedCategory;

    /** 搜索模式标志 */
    private boolean isSearchMode = false;

    /** 搜索关键词 */
    private String searchKeyword;

    // ==================== FXML 初始化 ====================

    /**
     * FXML 加载后自动调用
     */
    @FXML
    private void initialize() {
        try {
            // 初始化服务（使用 App 全局 AuthService，需已解锁）
            App app = App.getInstance();
            if (app == null || app.getAuthService() == null) {
                throw new IllegalStateException("App 未初始化");
            }
            if (!app.getAuthService().isUnlocked()) {
                throw new IllegalStateException("客户端未解锁，无法加载主界面");
            }
            LOGGER.info("Dashboard 初始化 | authUnlocked=true");
            categoryService = new CategoryService();
            accountService = new AccountService(app.getAuthService());
            exportService = new ExportService(app.getAuthService());
            importService = new ImportService();

            // 加载分类列表
            loadCategories();

            // 分类列表仅显示名称
            setupCategoryListView();

            // 配置账户列表列
            setupAccountTableColumns();

            // 分类选择事件
            categoryListView.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldVal, newVal) -> {
                        selectedCategory = newVal;
                        isSearchMode = false;
                        loadAccountsForCategory(newVal);
                    }
            );

            // 账户双击事件（查看详情）
            accountTableView.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    AccountTableRow row = accountTableView.getSelectionModel().getSelectedItem();
                    if (row != null) {
                        viewAccountDetail(row);
                    }
                }
            });

            // 账户右键菜单
            accountTableView.setRowFactory(tv -> {
                TableRow<AccountTableRow> row = new TableRow<>();
                ContextMenu contextMenu = new ContextMenu();
                MenuItem copyUserItem = new MenuItem("复制用户名");
                copyUserItem.setOnAction(e -> {
                    AccountTableRow r = row.getItem();
                    if (r != null) {
                        ClipboardUtil.setText(r.getAccount().getUsername());
                        AlertHelper.showInfo(getStage(), "复制成功", "用户名已复制到剪贴板");
                    }
                });
                MenuItem copyPassItem = new MenuItem("复制密码");
                copyPassItem.setOnAction(e -> {
                    AccountTableRow r = row.getItem();
                    if (r != null) {
                        try {
                            String decrypted = accountService.decryptPassword(r.account);
                            ClipboardUtil.setText(decrypted);
                            AlertHelper.showInfo(getStage(), "复制成功", "密码已复制到剪贴板");
                        } catch (AccountService.ServiceException ex) {
                            AlertHelper.showError(getStage(), "错误", "密码解密失败: " + ex.getMessage());
                        }
                    }
                });
                MenuItem editItem = new MenuItem("编辑");
                editItem.setOnAction(e -> {
                    AccountTableRow r = row.getItem();
                    if (r != null) {
                        openAccountForm(r.account);
                    }
                });
                MenuItem deleteItem = new MenuItem("删除");
                deleteItem.setOnAction(e -> {
                    AccountTableRow r = row.getItem();
                    if (r != null) {
                        confirmDeleteAccount(r.account);
                    }
                });

                contextMenu.getItems().addAll(copyUserItem, copyPassItem, new SeparatorMenuItem(), editItem, deleteItem);
                row.contextMenuProperty().bind(
                        javafx.beans.binding.Bindings.when(row.emptyProperty())
                                .then((ContextMenu) null)
                                .otherwise(contextMenu)
                );
                return row;
            });

            // 搜索监听
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.trim().isEmpty()) {
                    isSearchMode = false;
                    searchKeyword = null;
                    if (selectedCategory != null) {
                        loadAccountsForCategory(selectedCategory);
                    } else {
                        loadAllAccounts();
                    }
                } else {
                    isSearchMode = true;
                    searchKeyword = newVal.trim();
                    searchAccounts(searchKeyword);
                }
            });

            // 默认选中第一个分类
            if (!categoryListView.getItems().isEmpty()) {
                categoryListView.getSelectionModel().select(0);
            }

            LOGGER.debug("Dashboard 初始化完成");

        } catch (Exception e) {
            LOGGER.error("Dashboard 初始化失败", e);
            AlertHelper.showError(getStage(), "错误", "初始化失败: " + e.getMessage());
        }
    }

    // ==================== 分类管理 ====================

    /**
     * 加载分类列表
     */
    private void loadCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();
            categoryListView.setItems(FXCollections.observableArrayList(categories));
            updateStatusBar(categories.size());
        } catch (CategoryService.ServiceException e) {
            LOGGER.error("加载分类失败", e);
            AlertHelper.showError(getStage(), "错误", "加载分类失败: " + e.getMessage());
        }
    }

    /**
     * 刷新分类列表
     * <p>
     * 调用方: CategoryManagerController（分类变更后）
     * </p>
     */
    public void refreshCategories() {
        loadCategories();
    }

    /**
     * 打开分类管理弹窗
     * <p>
     * 调用方: 分类管理按钮
     * </p>
     */
    @FXML
    private void onManageCategories() {
        try {
            // 加载分类管理弹窗
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("分类管理");
            DialogStyleHelper.configureContentOnly(dialog, getStage());

            // 加载 FXML
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    Objects.requireNonNull(getClass().getResource("/fxml/CategoryManagerDialog.fxml")));
            dialog.getDialogPane().setContent(loader.load());

            // 设置控制器引用
            CategoryManagerController controller = loader.getController();
            controller.setParentController(this);
            controller.setDialog(dialog);

            dialog.showAndWait();

            // 刷新
            loadCategories();
            if (selectedCategory != null && !isSearchMode) {
                loadAccountsForCategory(selectedCategory);
            }
        } catch (Exception e) {
            LOGGER.error("打开分类管理失败", e);
            AlertHelper.showError(getStage(), "错误", "打开分类管理失败: " + e.getMessage());
        }
    }

    // ==================== 账户管理 ====================

    /**
     * 根据分类加载账户列表
     *
     * @param category 分类（null 则加载全部）
     */
    private void loadAccountsForCategory(Category category) {
        try {
            List<Account> accounts;
            if (category == null) {
                accounts = accountService.getAllAccounts();
            } else {
                accounts = accountService.getAccountsByCategory(category.getId());
            }
            displayAccounts(accounts);
        } catch (AccountService.ServiceException e) {
            LOGGER.error("加载账户失败", e);
            AlertHelper.showError(getStage(), "错误", "加载账户失败: " + e.getMessage());
        }
    }

    /**
     * 加载全部账户
     */
    private void loadAllAccounts() {
        try {
            List<Account> accounts = accountService.getAllAccounts();
            displayAccounts(accounts);
        } catch (AccountService.ServiceException e) {
            LOGGER.error("加载全部账户失败", e);
        }
    }

    /**
     * 搜索账户
     */
    private void searchAccounts(String keyword) {
        try {
            List<Account> accounts = accountService.searchAccounts(keyword);
            displayAccounts(accounts);
        } catch (AccountService.ServiceException e) {
            LOGGER.error("搜索账户失败", e);
        }
    }

    /**
     * 将账户列表渲染到表格
     */
    private void displayAccounts(List<Account> accounts) {
        List<AccountTableRow> rows = new ArrayList<>();
        for (Account account : accounts) {
            rows.add(new AccountTableRow(account));
        }
        accountTableView.setItems(FXCollections.observableArrayList(rows));

        // 更新状态栏
        if (isSearchMode) {
            statusLabel.setText(String.format("搜索 \"%s\": 找到 %d 个账户", searchKeyword, accounts.size()));
        } else if (selectedCategory != null) {
            statusLabel.setText(String.format("分类 \"%s\": %d 个账户", selectedCategory.getName(), accounts.size()));
        } else {
            statusLabel.setText(String.format("共 %d 个账户", accounts.size()));
        }
    }

    /**
     * 配置左侧分类列表单元格，仅展示分类名称
     */
    private void setupCategoryListView() {
        categoryListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
    }

    /**
     * 配置账户表格列
     */
    private void setupAccountTableColumns() {
        nameColumn.setCellValueFactory(data -> data.getValue().nameProperty());
        addressColumn.setCellValueFactory(data -> data.getValue().addressProperty());
        usernameColumn.setCellValueFactory(data -> data.getValue().usernameProperty());
    }

    /**
     * 查看账户详情（弹出详情弹窗）
     */
    private void viewAccountDetail(AccountTableRow row) {
        Account account = row.account;
        try {
            String password = accountService.decryptPassword(account);

            Alert detail = new Alert(Alert.AlertType.INFORMATION);
            detail.setTitle("账户详情");
            detail.setHeaderText(account.getName());

            StringBuilder content = new StringBuilder();
            content.append("分类: ").append(getCategoryName(account.getCategoryId())).append("\n");
            content.append("地址: ").append(account.getDisplayAddress()).append("\n");
            if (account.getPort() != null) {
                content.append("端口: ").append(account.getPort()).append("\n");
            }
            content.append("账号: ").append(account.getUsername() != null ? account.getUsername() : "(空)").append("\n");
            content.append("密码: ").append(password != null ? password : "(空)").append("\n");
            if (account.getValidFrom() != null) {
                content.append("有效期从: ").append(account.getValidFrom()).append("\n");
            }
            if (account.getValidTo() != null) {
                content.append("有效期至: ").append(account.getValidTo()).append("\n");
            }
            if (account.getNotes() != null && !account.getNotes().isEmpty()) {
                content.append("备注: ").append(account.getNotes()).append("\n");
            }

            detail.setContentText(content.toString());
            detail.showAndWait();

        } catch (AccountService.ServiceException e) {
            AlertHelper.showError(getStage(), "错误", "查看密码失败: " + e.getMessage());
        }
    }

    /**
     * 获取分类名称
     */
    private String getCategoryName(Long categoryId) {
        try {
            Category category = categoryService.getCategoryById(categoryId);
            return category.getDisplayName();
        } catch (Exception e) {
            return "未知分类";
        }
    }

    // ==================== 新增/编辑/删除 ====================

    /**
     * 打开新增账户表单
     * <p>
     * 调用方: 新增账户按钮
     * </p>
     */
    @FXML
    private void onAddAccount() {
        openAccountForm(null);
    }

    /**
     * 打开账户编辑/新建表单弹窗
     *
     * @param account 要编辑的账户（null 表示新建）
     */
    private void openAccountForm(Account account) {
        try {
            Dialog<Account> dialog = new Dialog<>();
            dialog.setTitle(account == null ? "新增账户" : "编辑账户");
            DialogStyleHelper.configureContentOnly(dialog, getStage());

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    Objects.requireNonNull(getClass().getResource("/fxml/AccountFormDialog.fxml")));
            dialog.getDialogPane().setContent(loader.load());

            AccountFormController controller = loader.getController();
            controller.setDialog(dialog);
            controller.setAccountService(accountService);
            controller.setCategoryService(categoryService);
            controller.setAccount(account);

            dialog.showAndWait().ifPresent(result -> {
                // 刷新列表
                if (selectedCategory != null && !isSearchMode) {
                    loadAccountsForCategory(selectedCategory);
                } else if (isSearchMode) {
                    searchAccounts(searchKeyword);
                } else {
                    loadAllAccounts();
                }
            });

        } catch (Exception e) {
            LOGGER.error("打开账户表单失败", e);
            AlertHelper.showError(getStage(), "错误", "打开表单失败: " + e.getMessage());
        }
    }

    /**
     * 确认删除账户
     */
    private void confirmDeleteAccount(Account account) {
        boolean confirmed = AlertHelper.showConfirm(
                getStage(),
                "确认删除",
                "确定要删除账户 \"" + account.getName() + "\" 吗？此操作不可恢复。"
        );

        if (confirmed) {
            try {
                accountService.deleteAccount(account.getId());

                // 刷新列表
                if (selectedCategory != null && !isSearchMode) {
                    loadAccountsForCategory(selectedCategory);
                } else {
                    loadAllAccounts();
                }

                AlertHelper.showInfo(getStage(), "删除成功", "账户已删除");

            } catch (AccountService.ServiceException e) {
                AlertHelper.showError(getStage(), "错误", "删除失败: " + e.getMessage());
            }
        }
    }

    // ==================== 导入导出 ====================

    /**
     * 打开导入导出弹窗
     * <p>
     * 调用方: 导入/导出按钮
     * </p>
     */
    @FXML
    private void onImportExport() {
        try {
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("导入 / 导出");
            DialogStyleHelper.configureContentOnly(dialog, getStage());

            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    Objects.requireNonNull(getClass().getResource("/fxml/ImportExportDialog.fxml")));
            dialog.getDialogPane().setContent(loader.load());

            ImportExportController controller = loader.getController();
            controller.setParentController(this);
            controller.setExportService(exportService);
            controller.setImportService(importService);
            controller.setDialog(dialog);

            dialog.showAndWait();

            // 刷新数据
            loadCategories();
            if (selectedCategory != null && !isSearchMode) {
                loadAccountsForCategory(selectedCategory);
            }

        } catch (Exception e) {
            LOGGER.error("打开导入导出弹窗失败", e);
            AlertHelper.showError(getStage(), "错误", "打开导入导出失败: " + e.getMessage());
        }
    }

    // ==================== 锁定与退出 ====================

    /**
     * 锁定客户端
     * <p>
     * 调用方: 锁定按钮
     * </p>
     */
    @FXML
    private void onLock() {
        App app = App.getInstance();
        app.getAuthService().lock();

        try {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            ViewFactory.switchView(stage, "/fxml/LoginView.fxml", "解锁");
            LOGGER.info("客户端已锁定");
        } catch (Exception e) {
            LOGGER.error("切换登录界面失败", e);
        }
    }

    /**
     * 退出应用
     * <p>
     * 调用方: 退出按钮
     * </p>
     */
    @FXML
    private void onExit() {
        boolean confirmed = AlertHelper.showConfirm(
                getStage(),
                "确认退出",
                "确定要退出 Key-Store 吗？"
        );

        if (confirmed) {
            Platform.exit();
        }
    }

    // ==================== 状态栏 ====================

    /**
     * 更新状态栏信息
     */
    private void updateStatusBar(int categoryCount) {
        try {
            int accountCount = accountService.getAllAccounts().size();
            statusLabel.setText(String.format("共 %d 个分类 / %d 个账户",
                    categoryCount, accountCount));
        } catch (Exception e) {
            statusLabel.setText(String.format("共 %d 个分类", categoryCount));
        }
    }

    // ==================== 内部数据结构 ====================

    /**
     * 账户表格行数据包装类
     * <p>
     * JavaFX TableView 需要可观察属性，此类包装 Account 并提供属性。
     * </p>
     */
    public static class AccountTableRow {
        private final Account account;
        private final javafx.beans.property.SimpleStringProperty nameProperty;
        private final javafx.beans.property.SimpleStringProperty addressProperty;
        private final javafx.beans.property.SimpleStringProperty usernameProperty;

        public AccountTableRow(Account account) {
            this.account = account;
            this.nameProperty = new javafx.beans.property.SimpleStringProperty(account.getName());
            this.addressProperty = new javafx.beans.property.SimpleStringProperty(
                    account.getDisplayAddress());
            this.usernameProperty = new javafx.beans.property.SimpleStringProperty(
                    account.getUsername() != null ? account.getUsername() : "");
        }

        public Account getAccount() {
            return account;
        }

        public javafx.beans.property.StringProperty nameProperty() {
            return nameProperty;
        }

        public javafx.beans.property.StringProperty addressProperty() {
            return addressProperty;
        }

        public javafx.beans.property.StringProperty usernameProperty() {
            return usernameProperty;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前窗口 Stage
     */
    private Stage getStage() {
        return (Stage) rootPane.getScene().getWindow();
    }
}
