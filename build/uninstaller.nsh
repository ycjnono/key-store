; Key-Store 卸载扩展 — 可选完全清除用户数据（密码库、配置）
; 覆盖安装/升级时跳过提示，保留数据

!macro customUnInstall
  ; 升级覆盖安装时不询问，保留用户数据
  ${if} ${isUpdated}
    Goto unInstallDone
  ${endIf}

  MessageBox MB_YESNO|MB_ICONQUESTION \
    "是否完全清除 Key-Store 本地数据？$\n$\n将删除：$\n  - 密码库 (key_store.db)$\n  - 应用配置与缓存$\n  目录：%APPDATA%\keystore$\n$\n【是】完全清除，重装后需重新设置主密码$\n【否】仅卸载程序，保留数据以便恢复" \
    /SD IDNO IDNO unInstallSkip IDYES unInstallPurge

  unInstallPurge:
    SetShellVarContext current
    ; Electron userData（package.json name = keystore）
    RMDir /r "$APPDATA\keystore"
    RMDir /r "$LOCALAPPDATA\keystore"
    ; 兼容 productName 目录（若存在）
    RMDir /r "$APPDATA\Key-Store"
    RMDir /r "$LOCALAPPDATA\Key-Store"
    Goto unInstallDone

  unInstallSkip:
  unInstallDone:
!macroend
