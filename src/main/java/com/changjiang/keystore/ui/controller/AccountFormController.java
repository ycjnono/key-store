/*
 * ============================================================
 * 账户表单对话框控制器
 * ============================================================
 * 功能 : 处理新增/编辑账户的表单交互，包括：
 *        - 表单字段填充（新建/编辑两种模式）
 *        - 分类下拉选择
 *        - 密码明文输入与显示
 *        - 有效期选择
 *        - 表单验证
 *        - 保存/取消
 *
 * 调用方 : AccountFormDialog.fxml（视图绑定）、DashboardController
 * ============================================================
 */
package com.changjiang.keystore.ui.controller;

import com.changjiang.keystore.model.Account;
import com.changjiang.keystore.model.Category;
import com.changjiang.keystore.service.AccountService;
import com.changjiang.keystore.service.CategoryService;
import com.changjiang.keystore.ui.util.AlertHelper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户表单对话框控制器
 * <p>
 * 用于新增和编辑账户的表单界面。
 * </p>
 */
public class AccountFormController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountFormController.class);

    // ==================== FXML 注入 ====================

    @FXML
    private Label titleLabel;

    @FXML
    private ComboBox<Category> categoryComboBox;

    @FXML
    private TextField nameField;

    @FXML
    private TextField addressField;

    @FXML
    private TextField portField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox showPasswordCheckBox;

    @FXML
    private TextField passwordVisibleField;

    @FXML
    private DatePicker validFromPicker;

    @FXML
    private DatePicker validToPicker;

    @FXML
    private TextArea notesArea;

    // ==================== 依赖 ====================

    /** 分类服务 */
    private CategoryService categoryService;

    /** 账户服务 */
    private AccountService accountService;

    /** 父对话框 */
    private Dialog<Account> dialog;

    /** 正在编辑的账户（null 表示新建） */
    private Account editingAccount;

    /** 是否为编辑模式 */
    private boolean editMode = false;

    /** 保存成功后的账户结果（供 Dialog resultConverter 读取） */
    private Account savedAccount;

    // ==================== 初始化 ====================

    /**
     * FXML 加载后自动调用，初始化分类下拉框
     */
    @FXML
    private void initialize() {
        // 密码显示/隐藏切换
        showPasswordCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                passwordVisibleField.setText(passwordField.getText());
                passwordVisibleField.setVisible(true);
                passwordField.setVisible(false);
            } else {
                passwordField.setText(passwordVisibleField.getText());
                passwordField.setVisible(true);
                passwordVisibleField.setVisible(false);
            }
        });

        // 同步两个密码框内容
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!showPasswordCheckBox.isSelected()) {
                passwordVisibleField.setText(newVal);
            }
        });
        passwordVisibleField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (showPasswordCheckBox.isSelected()) {
                passwordField.setText(newVal);
            }
        });

        setupCategoryComboBox();
    }

    /**
     * 配置分类下拉框，列表项与选中项均显示分类名称
     */
    private void setupCategoryComboBox() {
        ListCell<Category> nameCell = new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        };
        categoryComboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        categoryComboBox.setButtonCell(nameCell);
    }

    /**
     * 设置服务依赖
     *
     * @param categoryService 分类服务
     * @param accountService  账户服务
     */
    public void setServices(CategoryService categoryService, AccountService accountService) {
        this.categoryService = categoryService;
        this.accountService = accountService;
    }

    /**
     * 设置分类服务
     *
     * @param categoryService 分类服务实例
     */
    public void setCategoryService(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 设置账户服务
     *
     * @param accountService 账户服务实例
     */
    public void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 获取保存后的账户结果
     *
     * @return 保存成功的账户，未保存时返回 null
     */
    public Account getResult() {
        return savedAccount;
    }

    /**
     * 设置正在编辑的账户
     *
     * @param account 账户实体（null 表示新建模式）
     */
    public void setAccount(Account account) {
        this.editingAccount = account;

        if (account != null) {
            // 编辑模式
            editMode = true;
            titleLabel.setText("编辑账户");

            // 加载分类列表
            loadCategories();

            // 填充表单
            nameField.setText(account.getName());
            addressField.setText(account.getAddress());
            portField.setText(account.getPort() != null ? String.valueOf(account.getPort()) : "");
            usernameField.setText(account.getUsername() != null ? account.getUsername() : "");
            notesArea.setText(account.getNotes() != null ? account.getNotes() : "");

            if (account.getValidFrom() != null) {
                validFromPicker.setValue(account.getValidFrom().toLocalDate());
            }
            if (account.getValidTo() != null) {
                validToPicker.setValue(account.getValidTo().toLocalDate());
            }

            // 密码框不预填（安全考虑），留空表示不修改
            passwordField.setPromptText("留空表示不修改密码");
            passwordVisibleField.setPromptText("留空表示不修改密码");

            // 选中当前分类
            for (Category cat : categoryComboBox.getItems()) {
                if (cat.getId().equals(account.getCategoryId())) {
                    categoryComboBox.getSelectionModel().select(cat);
                    break;
                }
            }
        } else {
            // 新建模式
            editMode = false;
            titleLabel.setText("新增账户");
            loadCategories();
        }
    }

    /**
     * 设置父对话框引用
     *
     * @param dialog 对话框实例
     */
    public void setDialog(Dialog<Account> dialog) {
        this.dialog = dialog;
    }

    /**
     * 加载分类列表到下拉框
     */
    private void loadCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();
            categoryComboBox.setItems(FXCollections.observableArrayList(categories));

            if (!categories.isEmpty()) {
                categoryComboBox.getSelectionModel().selectFirst();
            }
        } catch (CategoryService.ServiceException e) {
            LOGGER.error("加载分类列表失败", e);
            AlertHelper.showError(getWindow(), "错误", "加载分类列表失败");
        }
    }

    // ==================== 事件处理 ====================

    /**
     * 保存按钮点击事件
     * <p>
     * 调用方: AccountFormDialog.fxml 中确认按钮
     * </p>
     */
    @FXML
    private void onSave() {
        // 获取分类
        Category selectedCategory = categoryComboBox.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) {
            AlertHelper.showWarning(getWindow(), "提示", "请选择账户分类");
            return;
        }

        // 获取名称
        String name = nameField.getText();
        if (name == null || name.trim().isEmpty()) {
            AlertHelper.showWarning(getWindow(), "提示", "请输入账户名称");
            return;
        }

        // 获取其他字段
        String address = addressField.getText();
        Integer port = null;
        String portText = portField.getText();
        if (portText != null && !portText.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portText.trim());
            } catch (NumberFormatException e) {
                AlertHelper.showWarning(getWindow(), "提示", "端口号必须是数字");
                return;
            }
        }

        String username = usernameField.getText();
        String password = showPasswordCheckBox.isSelected()
                ? passwordVisibleField.getText()
                : passwordField.getText();

        // 新建模式下密码必填
        if (!editMode && (password == null || password.isEmpty())) {
            AlertHelper.showWarning(getWindow(), "提示", "请输入密码");
            return;
        }

        // 有效期
        LocalDateTime validFrom = validFromPicker.getValue() != null
                ? validFromPicker.getValue().atStartOfDay() : null;
        LocalDateTime validTo = validToPicker.getValue() != null
                ? validToPicker.getValue().atStartOfDay() : null;

        String notes = notesArea.getText();

        try {
            if (editMode && editingAccount != null) {
                // 编辑模式
                if (password == null || password.isEmpty()) {
                    savedAccount = accountService.updateAccount(
                            editingAccount.getId(),
                            selectedCategory.getId(),
                            name, address, port, username, null,
                            validFrom, validTo, notes
                    );
                } else {
                    savedAccount = accountService.updateAccount(
                            editingAccount.getId(),
                            selectedCategory.getId(),
                            name, address, port, username, password,
                            validFrom, validTo, notes
                    );
                }
            } else {
                // 新建模式
                savedAccount = accountService.createAccount(
                        selectedCategory.getId(),
                        name, address, port, username, password,
                        validFrom, validTo, notes
                );
            }

            // 关闭对话框并返回保存结果
            if (dialog != null) {
                dialog.setResult(savedAccount);
                dialog.close();
            }

        } catch (AccountService.ServiceException e) {
            LOGGER.error("保存账户失败", e);
            AlertHelper.showError(getWindow(), "错误", "保存失败: " + e.getMessage());
        }
    }

    /**
     * 取消按钮点击事件
     */
    @FXML
    private void onCancel() {
        if (dialog != null) {
            dialog.setResult(null);
            dialog.close();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取对话框窗口
     */
    private Window getWindow() {
        return dialog != null ? dialog.getDialogPane().getScene().getWindow() : null;
    }
}
