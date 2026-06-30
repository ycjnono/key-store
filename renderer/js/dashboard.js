/**
 * 主界面逻辑
 */
(function () {
  let categories = [];
  let selectedCategoryId = null;
  let searchKeyword = '';
  let editingAccountId = null;
  let contextAccountId = null;

  const $ = (id) => document.getElementById(id);

  async function api(promise) {
    const res = await promise;
    if (!res.ok) throw new Error(res.error);
    return res.data;
  }

  function openModal(id) { $(id).classList.add('open'); }
  function closeModal(id) { $(id).classList.remove('open'); }

  function displayAddress(a) {
    if (!a.address) return '';
    return a.port ? `${a.address}:${a.port}` : a.address;
  }

  async function loadCategories() {
    categories = await api(window.keystore.category.list());
    const ul = $('categoryList');
    ul.innerHTML = '';
    categories.forEach((c) => {
      const li = document.createElement('li');
      li.textContent = c.name;
      li.dataset.id = c.id;
      if (c.id === selectedCategoryId) li.classList.add('active');
      li.onclick = () => {
        selectedCategoryId = c.id;
        searchKeyword = '';
        $('searchInput').value = '';
        loadCategories();
        loadAccounts();
      };
      ul.appendChild(li);
    });
  }

  async function loadAccounts() {
    const keyword = $('searchInput').value.trim();
    searchKeyword = keyword;
    let accounts;
    if (keyword) {
      accounts = await api(window.keystore.account.list(null, keyword));
    } else if (selectedCategoryId) {
      accounts = await api(window.keystore.account.list(selectedCategoryId, null));
    } else {
      accounts = await api(window.keystore.account.list(null, null));
    }

    const tbody = $('accountTable');
    tbody.innerHTML = '';
    accounts.forEach((a) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${escapeHtml(a.name)}</td><td>${escapeHtml(displayAddress(a))}</td><td>${escapeHtml(a.username || '')}</td>`;
      tr.ondblclick = () => showAccountDetail(a.id);
      tr.oncontextmenu = (e) => {
        e.preventDefault();
        contextAccountId = a.id;
        showContextMenu(e, a);
      };
      tbody.appendChild(tr);
    });

    if (keyword) {
      $('statusBar').textContent = `搜索 "${keyword}": 找到 ${accounts.length} 个账户`;
    } else if (selectedCategoryId) {
      const cat = categories.find((c) => c.id === selectedCategoryId);
      $('statusBar').textContent = `分类 "${cat?.name || ''}": ${accounts.length} 个账户`;
    } else {
      $('statusBar').textContent = `共 ${accounts.length} 个账户`;
    }
  }

  function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  /** 账户表单密码明文（与输入框脱敏展示分离） */
  let accountPasswordPlain = '';

  /** 将明文转为 * 脱敏展示 */
  function maskPassword(plain) {
    return plain ? '*'.repeat(plain.length) : '';
  }

  /** 根据当前可见状态刷新密码输入框与按钮文案 */
  function applyPasswordFieldView() {
    const input = $('accPassword');
    const btn = $('accPasswordToggle');
    const visible = btn.dataset.visible === 'true';
    if (visible) {
      input.value = accountPasswordPlain;
      input.readOnly = false;
      btn.textContent = '隐藏';
    } else {
      input.value = maskPassword(accountPasswordPlain);
      // 已有密码时脱敏只读；无密码时允许点击「显示」后输入
      input.readOnly = !!accountPasswordPlain;
      btn.textContent = '显示';
    }
  }

  /**
   * 重置密码框
   * @param {string} plain 明文密码
   * @param {boolean} [startVisible=false] 新增时 true，默认可直接输入明文
   */
  function resetPasswordField(plain = '', startVisible = false) {
    accountPasswordPlain = plain;
    const btn = $('accPasswordToggle');
    btn.dataset.visible = startVisible || !plain ? 'true' : 'false';
    applyPasswordFieldView();
  }

  /** 绑定密码显示/隐藏文字按钮 */
  function bindPasswordField() {
    const btn = $('accPasswordToggle');
    const input = $('accPassword');
    btn.addEventListener('click', () => {
      const visible = btn.dataset.visible === 'true';
      if (visible) {
        accountPasswordPlain = input.value;
        btn.dataset.visible = 'false';
      } else {
        btn.dataset.visible = 'true';
      }
      applyPasswordFieldView();
      if (btn.dataset.visible === 'true') input.focus();
    });
    input.addEventListener('input', () => {
      if (btn.dataset.visible === 'true') accountPasswordPlain = input.value;
    });
  }

  bindPasswordField();

  async function showAccountDetail(id) {
    try {
      const a = await api(window.keystore.account.get(id));
      const pwd = await api(window.keystore.account.decryptPassword(id));
      const cat = categories.find((c) => c.id === a.category_id);
      alert(
        `【${a.name}】\n分类: ${cat?.name || ''}\n地址: ${displayAddress(a)}\n账号: ${a.username || '(空)'}\n密码: ${pwd || '(空)'}\n备注: ${a.notes || ''}`
      );
    } catch (err) {
      alert(`无法读取账户详情：${err.message}\n若刚完成导入，请使用导出端的原主密码重新登录。`);
    }
  }

  function showContextMenu(e, account) {
    const menu = document.createElement('div');
    menu.style.cssText = 'position:fixed;background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:4px;z-index:2000;box-shadow:0 4px 12px rgba(0,0,0,.12)';
    menu.style.left = e.clientX + 'px';
    menu.style.top = e.clientY + 'px';
    const items = [
      ['复制用户名', async () => { await window.keystore.clipboard.write(account.username || ''); }],
      ['复制密码', async () => {
        try {
          const p = await api(window.keystore.account.decryptPassword(account.id));
          await window.keystore.clipboard.write(p || '');
        } catch (err) {
          alert(`复制密码失败：${err.message}`);
        }
      }],
      ['编辑', () => openAccountForm(account.id)],
      ['删除', () => deleteAccount(account.id)],
    ];
    items.forEach(([label, fn]) => {
      const b = document.createElement('button');
      b.textContent = label;
      b.className = 'btn-secondary';
      b.style.display = 'block';
      b.style.width = '100%';
      b.style.margin = '2px 0';
      b.onclick = () => { document.body.removeChild(menu); fn(); };
      menu.appendChild(b);
    });
    document.body.appendChild(menu);
    setTimeout(() => document.addEventListener('click', () => { if (menu.parentNode) menu.parentNode.removeChild(menu); }, { once: true }));
  }

  async function deleteAccount(id) {
    if (!confirm('确定删除该账户吗？')) return;
    await api(window.keystore.account.delete(id));
    loadAccounts();
  }

  async function openAccountForm(id) {
    editingAccountId = id || null;
    $('accountModalTitle').textContent = id ? '编辑账户' : '新增账户';
    const cats = await api(window.keystore.category.list());
    const sel = $('accCategory');
    sel.innerHTML = cats.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');

    $('accName').value = '';
    $('accAddress').value = '';
    $('accPort').value = '';
    $('accUsername').value = '';
    $('accPassword').placeholder = '必填';
    $('accValidFrom').value = '';
    $('accValidTo').value = '';
    $('accNotes').value = '';

    if (id) {
      const a = await api(window.keystore.account.get(id));
      sel.value = a.category_id;
      $('accName').value = a.name;
      $('accAddress').value = a.address || '';
      $('accPort').value = a.port || '';
      $('accUsername').value = a.username || '';
      $('accValidFrom').value = a.valid_from ? a.valid_from.slice(0, 10) : '';
      $('accValidTo').value = a.valid_to ? a.valid_to.slice(0, 10) : '';
      $('accNotes').value = a.notes || '';
      try {
        const pwd = await api(window.keystore.account.decryptPassword(id));
        resetPasswordField(pwd || '', false);
      } catch (err) {
        resetPasswordField('', true);
        alert(`无法读取账户密码：${err.message}`);
      }
    } else {
      resetPasswordField('', true);
    }
    openModal('accountModal');
  }

  async function saveAccount() {
    const data = {
      categoryId: Number($('accCategory').value),
      name: $('accName').value.trim(),
      address: $('accAddress').value.trim() || null,
      port: $('accPort').value ? Number($('accPort').value) : null,
      username: $('accUsername').value.trim() || null,
      password: accountPasswordPlain,
      validFrom: $('accValidFrom').value ? $('accValidFrom').value + 'T00:00:00' : null,
      validTo: $('accValidTo').value ? $('accValidTo').value + 'T00:00:00' : null,
      notes: $('accNotes').value.trim() || null,
    };
    if (!data.name) { alert('请输入账户名称'); return; }
    if (!editingAccountId && !data.password) { alert('请输入密码'); return; }

    if (editingAccountId) {
      if (!data.password) delete data.password;
      await api(window.keystore.account.update(editingAccountId, data));
    } else {
      await api(window.keystore.account.create(data));
    }
    closeModal('accountModal');
    loadAccounts();
  }

  async function openCategoryModal() {
    const tbody = $('categoryTable');
    tbody.innerHTML = '';
    for (const c of categories) {
      const count = await api(window.keystore.category.countAccounts(c.id));
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${escapeHtml(c.name)}</td><td>${count}</td><td>
        <button class="btn-secondary btn-edit-cat" data-id="${c.id}" data-name="${escapeHtml(c.name)}">编辑</button>
        <button class="btn-danger btn-del-cat" data-id="${c.id}">删除</button>
      </td>`;
      tbody.appendChild(tr);
    }
    tbody.querySelectorAll('.btn-edit-cat').forEach((btn) => {
      btn.onclick = async () => {
        const name = prompt('新名称', btn.dataset.name);
        if (!name?.trim()) return;
        const cat = categories.find((c) => c.id === Number(btn.dataset.id));
        await api(window.keystore.category.update(cat.id, name.trim(), cat.icon, cat.sort_order));
        categories = await api(window.keystore.category.list());
        openCategoryModal();
        loadCategories();
      };
    });
    tbody.querySelectorAll('.btn-del-cat').forEach((btn) => {
      btn.onclick = async () => {
        if (!confirm('确定删除该分类吗？其下账户将一并删除。')) return;
        await api(window.keystore.category.delete(Number(btn.dataset.id)));
        categories = await api(window.keystore.category.list());
        if (selectedCategoryId === Number(btn.dataset.id)) selectedCategoryId = null;
        openCategoryModal();
        loadCategories();
        loadAccounts();
      };
    });
    openModal('categoryModal');
  }

  // 事件绑定
  $('btnLock').onclick = async () => { await window.keystore.auth.lock(); await window.keystore.nav.login(); };
  $('btnExit').onclick = () => window.keystore.window.close();
  $('btnCategories').onclick = openCategoryModal;
  $('btnAddAccount').onclick = () => openAccountForm(null);
  $('searchInput').oninput = () => loadAccounts();
  $('accSave').onclick = saveAccount;
  $('accClose').onclick = () => closeModal('accountModal');
  $('categoryClose').onclick = () => { closeModal('categoryModal'); loadCategories(); loadAccounts(); };
  $('btnAddCategory').onclick = async () => {
    const name = $('newCategoryName').value.trim();
    if (!name) return;
    await api(window.keystore.category.create(name));
    $('newCategoryName').value = '';
    categories = await api(window.keystore.category.list());
    openCategoryModal();
    loadCategories();
  };

  // 导入导出
  $('btnImportExport').onclick = async () => {
    const cats = await api(window.keystore.category.list());
    $('exportCategories').innerHTML = cats.map((c) => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
    openModal('ioModal');
  };
  $('ioClose').onclick = () => closeModal('ioModal');

  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.onclick = () => {
      document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
      document.querySelectorAll('.tab-panel').forEach((p) => p.classList.remove('active'));
      btn.classList.add('active');
      $('tab-' + btn.dataset.tab).classList.add('active');
    };
  });

  $('exportScope').onchange = () => {
    $('exportCategories').classList.toggle('hidden', $('exportScope').value !== 'category');
  };

  $('btnExportBrowse').onclick = async () => {
    const p = await window.keystore.dialog.saveFile('keystore_export.db');
    if (p) $('exportPath').value = p;
  };

  $('btnDoExport').onclick = async () => {
    const path = $('exportPath').value;
    if (!path) { alert('请选择导出路径'); return; }
    let res;
    if ($('exportScope').value === 'category') {
      const ids = Array.from($('exportCategories').selectedOptions).map((o) => Number(o.value));
      if (!ids.length) { alert('请选择分类'); return; }
      res = await api(window.keystore.export.categories(path, ids));
    } else {
      res = await api(window.keystore.export.all(path));
    }
    $('exportStatus').textContent = res.description;
    alert('导出成功');
  };

  $('btnImportBrowse').onclick = async () => {
    const p = await window.keystore.dialog.openFile();
    if (p) { $('importPath').value = p; previewImport(); }
  };

  async function previewImport() {
    const path = $('importPath').value;
    if (!path) return;
    const mode = $('importMode').value;
    const res = await api(window.keystore.import.preview(path, mode));
    $('importPreview').textContent = res.summary;
  }

  $('btnPreviewImport').onclick = previewImport;
  $('importMode').onchange = previewImport;

  $('btnDoImport').onclick = async () => {
    const path = $('importPath').value;
    if (!path) { alert('请选择文件'); return; }
    const mode = $('importMode').value;
    const msg = mode === 'OVERWRITE'
      ? '覆盖导入将清空当前数据，确定继续吗？'
      : '追加导入将保留现有数据，确定继续吗？';
    if (!confirm(msg)) return;

    const btn = $('btnDoImport');
    const sourcePassword = $('importSourcePassword').value;
    btn.disabled = true;
    const prevText = btn.textContent;
    btn.textContent = '导入中...';

    try {
      const res = mode === 'OVERWRITE'
        ? await api(window.keystore.import.overwrite(path))
        : await api(window.keystore.import.append(path, sourcePassword));
      alert(res.summary);
      closeModal('ioModal');
      if (res.requireRelogin) {
        await window.keystore.nav.login();
        return;
      }
      await loadCategories();
      await loadAccounts();
    } catch (err) {
      alert(`导入失败：${err.message || err}`);
    } finally {
      btn.disabled = false;
      btn.textContent = prevText;
    }
  };

  // 初始化
  (async function init() {
    const unlocked = await window.keystore.auth.isUnlocked();
    if (!unlocked.ok || !unlocked.data) {
      await window.keystore.nav.login();
      return;
    }
    await loadCategories();
    if (categories.length) {
      selectedCategoryId = categories[0].id;
      await loadCategories();
    }
    await loadAccounts();
  })();
})();
