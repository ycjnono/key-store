/** 登录页逻辑 */
(async function () {
  const titleLabel = document.getElementById('titleLabel');
  const hintLabel = document.getElementById('hintLabel');
  const passwordEl = document.getElementById('password');
  const confirmWrap = document.getElementById('confirmWrap');
  const confirmEl = document.getElementById('confirmPassword');
  const strengthLabel = document.getElementById('strengthLabel');
  const messageEl = document.getElementById('message');
  const actionBtn = document.getElementById('actionBtn');

  const first = await window.keystore.auth.isFirstUse();
  const setupMode = first.ok && first.data;

  if (setupMode) {
    titleLabel.textContent = '设置主密码';
    hintLabel.textContent = '请设置一个主密码，用于保护您的账户数据。';
    confirmWrap.classList.remove('hidden');
    actionBtn.textContent = '设置密码';
    document.title = 'Key-Store - 设置主密码';

    passwordEl.addEventListener('input', async () => {
      const res = await window.keystore.auth.passwordStrength(passwordEl.value);
      if (res.ok && passwordEl.value) {
        strengthLabel.textContent = `密码强度: ${res.data.label}`;
        strengthLabel.style.color = res.data.color;
      } else {
        strengthLabel.textContent = '';
      }
    });
  }

  actionBtn.addEventListener('click', async () => {
    messageEl.textContent = '';
    const pwd = passwordEl.value;
    if (!pwd) {
      messageEl.textContent = '请输入主密码';
      return;
    }
    if (setupMode) {
      if (pwd.length < 6) {
        messageEl.textContent = '密码长度不能少于 6 位';
        return;
      }
      if (pwd !== confirmEl.value) {
        messageEl.textContent = '两次输入的密码不一致';
        return;
      }
    }

    actionBtn.disabled = true;
    actionBtn.textContent = setupMode ? '设置中...' : '验证中...';

    const res = setupMode
      ? await window.keystore.auth.setup(pwd)
      : await window.keystore.auth.verify(pwd);

    if (res.ok) {
      await window.keystore.nav.dashboard();
    } else {
      messageEl.textContent = setupMode ? `设置失败: ${res.error}` : '密码错误，请重试';
      passwordEl.value = '';
      actionBtn.disabled = false;
      actionBtn.textContent = setupMode ? '设置密码' : '解锁';
    }
  });

  passwordEl.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') actionBtn.click();
  });
})();
