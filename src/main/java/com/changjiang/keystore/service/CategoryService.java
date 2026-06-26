/*
 * ============================================================
 * 分类业务服务类
 * ============================================================
 * 功能 : 封装分类相关的业务逻辑，包括：
 *        - 分类的创建、查询、修改、删除
 *        - 初始化默认分类
 *        - 分类下账户数量统计
 *
 * 调用方 : CategoryManagerController（分类管理界面）、
 *          AccountService（创建账户时验证分类存在性）
 * ============================================================
 */
package com.changjiang.keystore.service;

import com.changjiang.keystore.config.AppConfig;
import com.changjiang.keystore.model.Category;
import com.changjiang.keystore.repository.AccountRepository;
import com.changjiang.keystore.repository.CategoryRepository;
import com.changjiang.keystore.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 分类服务类
 * <p>
 * 提供分类的完整 CRUD 业务逻辑封装。
 * </p>
 */
public class CategoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryService.class);

    /**
     * 分类仓储
     */
    private final CategoryRepository categoryRepository;

    /**
     * 默认分类列表（首次使用时初始化）
     */
    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "网站", "APP", "服务器", "API", "邮箱", "项目", "银行卡", "门禁", "其他"
    );

    // ==================== 构造方法 ====================

    /**
     * 构造分类服务实例
     */
    public CategoryService() {
        this.categoryRepository = new CategoryRepository();
    }

    // ==================== 查询分类 ====================

    /**
     * 查询所有分类（按排序序号排列）
     * <p>
     * 调用方: DashboardController（加载分类列表）
     * </p>
     *
     * @return 分类列表
     * @throws ServiceException 查询失败
     */
    public List<Category> getAllCategories() throws ServiceException {
        try {
            DatabaseManager.getInstance().initDatabase();
            return categoryRepository.findAll();
        } catch (SQLException e) {
            LOGGER.error("查询所有分类失败", e);
            throw new ServiceException("查询分类失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 ID 查询分类
     *
     * @param id 分类 ID
     * @return Optional<Category>
     * @throws ServiceException 查询失败
     */
    public Category getCategoryById(Long id) throws ServiceException {
        try {
            return categoryRepository.findById(id)
                    .orElseThrow(() -> new ServiceException("分类不存在，ID=" + id));
        } catch (SQLException e) {
            LOGGER.error("查询分类失败: id={}", id, e);
            throw new ServiceException("查询分类失败", e);
        }
    }

    /**
     * 根据名称搜索分类
     *
     * @param keyword 关键词
     * @return 匹配的分类列表
     * @throws ServiceException 查询失败
     */
    public List<Category> searchCategories(String keyword) throws ServiceException {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return getAllCategories();
            }
            return categoryRepository.findByName(keyword.trim());
        } catch (SQLException e) {
            LOGGER.error("搜索分类失败: keyword={}", keyword, e);
            throw new ServiceException("搜索分类失败", e);
        }
    }

    // ==================== 创建分类 ====================

    /**
     * 创建新分类
     * <p>
     * 调用方: CategoryManagerController（新增分类）
     * </p>
     *
     * @param name       分类名称
     * @param icon       图标标识符（可为 null）
     * @param sortOrder  排序序号（可为 null，默认追加到最后）
     * @return 创建后的分类（含自增 ID）
     * @throws ServiceException 创建失败
     */
    public Category createCategory(String name, String icon, Integer sortOrder) throws ServiceException {
        try {
            DatabaseManager.getInstance().initDatabase();

            // 参数校验
            if (name == null || name.trim().isEmpty()) {
                throw new ServiceException("分类名称不能为空");
            }
            if (name.length() > 50) {
                throw new ServiceException("分类名称不能超过 50 个字符");
            }

            // 检查名称是否已存在
            List<Category> existing = categoryRepository.findByName(name.trim());
            if (!existing.isEmpty()) {
                throw new ServiceException("分类名称已存在: " + name);
            }

            Category category = new Category(name.trim(), icon, sortOrder);

            // 如果没有指定排序，追加到最后
            if (sortOrder == null) {
                int maxOrder = getMaxSortOrder();
                category.setSortOrder(maxOrder + 1);
            }

            return categoryRepository.insert(category);
        } catch (SQLException e) {
            LOGGER.error("创建分类失败: name={}", name, e);
            throw new ServiceException("创建分类失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建默认分类（首次初始化时调用）
     * <p>
     * 调用方: AuthService.setupPassword()
     * </p>
     */
    public void createDefaultCategories() throws ServiceException {
        try {
            DatabaseManager.getInstance().initDatabase();

            int maxOrder = getMaxSortOrder();

            for (int i = 0; i < DEFAULT_CATEGORIES.size(); i++) {
                String name = DEFAULT_CATEGORIES.get(i);
                try {
                    Category category = new Category(name, null, maxOrder + i + 1);
                    categoryRepository.insert(category);
                    LOGGER.debug("初始化默认分类: {}", name);
                } catch (SQLException e) {
                    LOGGER.warn("创建默认分类失败（可能已存在）: {}", name, e.getMessage());
                }
            }

            LOGGER.info("默认分类初始化完成，共 {} 个", DEFAULT_CATEGORIES.size());
        } catch (Exception e) {
            LOGGER.error("初始化默认分类失败", e);
            throw new ServiceException("初始化默认分类失败: " + e.getMessage(), e);
        }
    }

    // ==================== 更新分类 ====================

    /**
     * 更新分类信息
     * <p>
     * 调用方: CategoryManagerController（编辑分类）
     * </p>
     *
     * @param id        分类 ID
     * @param name      新名称
     * @param icon      新图标
     * @param sortOrder 新排序序号
     * @return 更新后的分类
     * @throws ServiceException 更新失败
     */
    public Category updateCategory(Long id, String name, String icon, Integer sortOrder) throws ServiceException {
        try {
            Category existing = categoryRepository.findById(id)
                    .orElseThrow(() -> new ServiceException("分类不存在，ID=" + id));

            // 如果名称变更，检查新名称是否与其他分类重复
            if (name != null && !name.equals(existing.getName())) {
                List<Category> duplicates = categoryRepository.findByName(name);
                if (!duplicates.isEmpty() && !duplicates.get(0).getId().equals(id)) {
                    throw new ServiceException("分类名称已存在: " + name);
                }
            }

            if (name != null) {
                existing.setName(name.trim());
            }
            if (icon != null) {
                existing.setIcon(icon);
            }
            if (sortOrder != null) {
                existing.setSortOrder(sortOrder);
            }

            return categoryRepository.update(existing);
        } catch (SQLException e) {
            LOGGER.error("更新分类失败: id={}", id, e);
            throw new ServiceException("更新分类失败", e);
        }
    }

    // ==================== 删除分类 ====================

    /**
     * 删除分类
     * <p>
     * 删除分类时会级联删除其下所有账户（数据库外键 ON DELETE CASCADE）。
     * </p>
     * <p>
     * 调用方: CategoryManagerController（删除分类）
     * </p>
     *
     * @param id 分类 ID
     * @throws ServiceException 删除失败
     */
    public void deleteCategory(Long id) throws ServiceException {
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new ServiceException("分类不存在，ID=" + id));

            // 检查是否有账户关联
            if (categoryRepository.hasAccounts(id)) {
                throw new ServiceException("该分类下有账户，请先删除或转移账户后再删除分类");
            }

            categoryRepository.deleteById(id);
            LOGGER.info("删除分类成功: id={}, name={}", id, category.getName());
        } catch (SQLException e) {
            LOGGER.error("删除分类失败: id={}", id, e);
            throw new ServiceException("删除分类失败: " + e.getMessage(), e);
        }
    }

    /**
     * 统计分类下的账户数量
     *
     * @param categoryId 分类 ID
     * @return 账户数量
     * @throws ServiceException 查询失败
     */
    public int countAccounts(Long categoryId) throws ServiceException {
        try {
            return new AccountRepository().countByCategoryId(categoryId);
        } catch (SQLException e) {
            LOGGER.error("统计账户数量失败: categoryId={}", categoryId, e);
            throw new ServiceException("统计账户数量失败", e);
        }
    }

    // ==================== 初始化 ====================

    /**
     * 获取当前最大排序序号
     *
     * @return 最大排序序号
     */
    private int getMaxSortOrder() {
        try {
            List<Category> categories = categoryRepository.findAll();
            return categories.stream()
                    .mapToInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0)
                    .max()
                    .orElse(0);
        } catch (SQLException e) {
            return 0;
        }
    }

    // ==================== 异常类 ====================

    /**
     * 业务操作异常
     */
    public static class ServiceException extends Exception {
        public ServiceException(String message) {
            super(message);
        }

        public ServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
