/*
 * ============================================================
 * 账户业务服务类
 * ============================================================
 * 功能 : 封装账户相关的业务逻辑，包括：
 *        - 账户的创建、查询、修改、删除
 *        - 密码的加密保存与解密展示
 *        - 账户搜索
 *        - 过期账户查询
 *        - 按分类查询账户
 *
 * 调用方 : DashboardController、AccountFormController
 * ============================================================
 */
package com.changjiang.keystore.service;

import com.changjiang.keystore.model.Account;
import com.changjiang.keystore.repository.AccountRepository;
import com.changjiang.keystore.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 账户服务类
 * <p>
 * 提供账户的完整 CRUD 业务逻辑封装。
 * 密码字段的加密/解密通过 {@link CryptoService} 处理。
 * </p>
 */
public class AccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountService.class);

    /**
     * 加密服务（用于密码加解密）
     */
    private final CryptoService cryptoService;

    /**
     * 账户仓储
     */
    private final AccountRepository accountRepository;

    // ==================== 构造方法 ====================

    /**
     * 构造账户服务实例
     *
     * @param authService 认证服务（用于获取加密服务实例）
     */
    public AccountService(AuthService authService) {
        this.cryptoService = authService.getCryptoService();
        this.accountRepository = new AccountRepository();
    }

    // ==================== 创建账户 ====================

    /**
     * 创建新账户
     * <p>
     * 调用方: AccountFormController（保存账户表单）
     * </p>
     *
     * @param categoryId  分类 ID
     * @param name        账户名称
     * @param address     地址
     * @param port        端口号（可为 null）
     * @param username    登录账号（可为 null）
     * @param password    明文密码（可为 null）
     * @param validFrom   有效期开始（可为 null）
     * @param validTo     有效期截止（可为 null）
     * @param notes       备注（可为 null）
     * @return 创建后的账户（含自增 ID 和加密密码）
     * @throws ServiceException 创建失败
     */
    public Account createAccount(Long categoryId, String name, String address, Integer port,
                                 String username, String password, LocalDateTime validFrom,
                                 LocalDateTime validTo, String notes) throws ServiceException {
        try {
            DatabaseManager.getInstance().initDatabase();

            // 参数校验
            if (categoryId == null) {
                throw new ServiceException("请选择账户分类");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new ServiceException("账户名称不能为空");
            }

            // 加密密码
            byte[] encryptedPassword = null;
            if (password != null) {
                encryptedPassword = cryptoService.encryptPassword(password);
            }

            Account account = new Account(categoryId, name.trim(), address, port);
            account.setUsername(username);
            account.setPassword(encryptedPassword);
            account.setValidFrom(validFrom);
            account.setValidTo(validTo);
            account.setNotes(notes);

            Account saved = accountRepository.insert(account);
            LOGGER.info("创建账户成功: id={}, name={}", saved.getId(), saved.getName());

            return saved;
        } catch (CryptoService.CryptoException e) {
            LOGGER.error("加密密码失败", e);
            throw new ServiceException("加密密码失败: " + e.getMessage(), e);
        } catch (SQLException e) {
            LOGGER.error("创建账户失败", e);
            throw new ServiceException("创建账户失败: " + e.getMessage(), e);
        }
    }

    // ==================== 查询账户 ====================

    /**
     * 根据 ID 查询账户（含密码解密）
     * <p>
     * 调用方: AccountFormController（编辑时加载数据）
     * </p>
     *
     * @param id 账户 ID
     * @return 账户实体（密码已解密为 null，需外部重新设置）
     * @throws ServiceException 查询失败
     */
    public Account getAccountById(Long id) throws ServiceException {
        try {
            Account account = accountRepository.findById(id)
                    .orElseThrow(() -> new ServiceException("账户不存在，ID=" + id));
            return account;
        } catch (SQLException e) {
            LOGGER.error("查询账户失败: id={}", id, e);
            throw new ServiceException("查询账户失败", e);
        }
    }

    /**
     * 查询指定分类下的所有账户
     * <p>
     * 调用方: DashboardController（显示分类下账户列表）
     * </p>
     *
     * @param categoryId 分类 ID
     * @return 账户列表（密码为密文，不解密）
     * @throws ServiceException 查询失败
     */
    public List<Account> getAccountsByCategory(Long categoryId) throws ServiceException {
        try {
            return accountRepository.findByCategoryId(categoryId);
        } catch (SQLException e) {
            LOGGER.error("查询分类下账户失败: categoryId={}", categoryId, e);
            throw new ServiceException("查询账户失败", e);
        }
    }

    /**
     * 查询所有账户
     * <p>
     * 调用方: ExportService（导出时获取全部数据）
     * </p>
     *
     * @return 所有账户列表
     * @throws ServiceException 查询失败
     */
    public List<Account> getAllAccounts() throws ServiceException {
        try {
            return accountRepository.findAll();
        } catch (SQLException e) {
            LOGGER.error("查询所有账户失败", e);
            throw new ServiceException("查询所有账户失败", e);
        }
    }

    /**
     * 搜索账户（按名称或账号模糊匹配）
     * <p>
     * 调用方: DashboardController（搜索功能）
     * </p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的账户列表
     * @throws ServiceException 查询失败
     */
    public List<Account> searchAccounts(String keyword) throws ServiceException {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return getAllAccounts();
            }
            return accountRepository.searchByName(keyword.trim());
        } catch (SQLException e) {
            LOGGER.error("搜索账户失败: keyword={}", keyword, e);
            throw new ServiceException("搜索账户失败", e);
        }
    }

    /**
     * 获取即将过期的账户
     *
     * @param withinDays 在指定天数内过期
     * @return 即将过期的账户列表
     * @throws ServiceException 查询失败
     */
    public List<Account> getExpiringAccounts(int withinDays) throws ServiceException {
        try {
            return accountRepository.findExpiringWithin(withinDays);
        } catch (SQLException e) {
            LOGGER.error("查询即将过期账户失败", e);
            throw new ServiceException("查询即将过期账户失败", e);
        }
    }

    /**
     * 获取已过期的账户
     *
     * @return 已过期的账户列表
     * @throws ServiceException 查询失败
     */
    public List<Account> getExpiredAccounts() throws ServiceException {
        try {
            return accountRepository.findExpired();
        } catch (SQLException e) {
            LOGGER.error("查询已过期账户失败", e);
            throw new ServiceException("查询已过期账户失败", e);
        }
    }

    // ==================== 解密密码 ====================

    /**
     * 解密账户密码（用于 UI 展示）
     * <p>
     * 调用方: AccountFormController（查看/编辑时展示明文密码）
     * </p>
     *
     * @param account 账户实体（密码为密文）
     * @return 明文密码，如果密文为空则返回 null
     * @throws ServiceException 解密失败
     */
    public String decryptPassword(Account account) throws ServiceException {
        if (account == null || account.getPassword() == null) {
            return null;
        }

        try {
            return cryptoService.decryptPassword(account.getPassword());
        } catch (CryptoService.CryptoException e) {
            LOGGER.error("解密密码失败: accountId={}", account.getId(), e);
            throw new ServiceException("密码解密失败", e);
        }
    }

    // ==================== 更新账户 ====================

    /**
     * 更新账户信息
     * <p>
     * 调用方: AccountFormController（保存编辑后的账户）
     * </p>
     *
     * @param id           账户 ID
     * @param categoryId   分类 ID
     * @param name         账户名称
     * @param address      地址
     * @param port         端口号
     * @param username     登录账号
     * @param password     明文密码（为空表示不修改密码）
     * @param validFrom    有效期开始
     * @param validTo      有效期截止
     * @param notes        备注
     * @return 更新后的账户
     * @throws ServiceException 更新失败
     */
    public Account updateAccount(Long id, Long categoryId, String name, String address,
                                 Integer port, String username, String password,
                                 LocalDateTime validFrom, LocalDateTime validTo, String notes)
            throws ServiceException {
        try {
            Account existing = accountRepository.findById(id)
                    .orElseThrow(() -> new ServiceException("账户不存在，ID=" + id));

            // 更新字段
            existing.setCategoryId(categoryId);
            existing.setName(name.trim());
            existing.setAddress(address);
            existing.setPort(port);
            existing.setUsername(username);
            existing.setValidFrom(validFrom);
            existing.setValidTo(validTo);
            existing.setNotes(notes);

            // 密码处理：如果传入新密码则加密更新，否则保持原密文
            if (password != null) {
                byte[] encryptedPassword = cryptoService.encryptPassword(password);
                existing.setPassword(encryptedPassword);
            }

            Account updated = accountRepository.update(existing);
            LOGGER.info("更新账户成功: id={}, name={}", updated.getId(), updated.getName());

            return updated;
        } catch (CryptoService.CryptoException e) {
            LOGGER.error("加密密码失败", e);
            throw new ServiceException("加密密码失败: " + e.getMessage(), e);
        } catch (SQLException e) {
            LOGGER.error("更新账户失败: id={}", id, e);
            throw new ServiceException("更新账户失败", e);
        }
    }

    // ==================== 删除账户 ====================

    /**
     * 删除账户
     * <p>
     * 调用方: AccountFormController（删除按钮）
     * </p>
     *
     * @param id 账户 ID
     * @throws ServiceException 删除失败
     */
    public void deleteAccount(Long id) throws ServiceException {
        try {
            Account account = accountRepository.findById(id)
                    .orElseThrow(() -> new ServiceException("账户不存在，ID=" + id));

            accountRepository.deleteById(id);
            LOGGER.info("删除账户成功: id={}, name={}", id, account.getName());
        } catch (SQLException e) {
            LOGGER.error("删除账户失败: id={}", id, e);
            throw new ServiceException("删除账户失败: " + e.getMessage(), e);
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
