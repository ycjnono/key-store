@echo off
chcp 65001 >nul
echo ========================================
echo    Key-Store Windows EXE 打包工具
echo ========================================
echo.

:: 检查 Maven
mvn -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 Maven，请先安装 Maven 3.8+
    pause
    exit /b 1
)

:: 检查 JDK
javac -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未检测到 JDK，请确保 JAVA_HOME 已设置
    pause
    exit /b 1
)

echo [1/3] Maven 清理并打包...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [错误] Maven 打包失败
    pause
    exit /b 1
)

echo.
echo [2/3] jpackage 打包 Windows EXE...

:: 检查 jpackage 是否可用
where jpackage >nul 2>&1
if errorlevel 1 (
    echo [警告] jpackage 未找到，跳过 EXE 打包
    echo       请使用 JDK 14+ 并确保 JAVA_HOME/bin 在 PATH 中
    goto :done
)

:: 获取版本号
for /f "tokens=2 delims=:" %%a in ('findstr /r "<version>" pom.xml ^| findstr /v "project"') do (
    set VERSION=%%a
)
set VERSION=%VERSION: =%
set VERSION=%VERSION:<=%
set VERSION=%VERSION:>=%

if "%VERSION%"=="" set VERSION=1.0.0

jpackage ^
  --name KeyStore ^
  --input target ^
  --main-jar keystore-1.0.0.jar ^
  --main-class com.changjiang.keystore.Main ^
  --type exe ^
  --win-dir-chooser ^
  --win-shortcut ^
  --win-menu ^
  --win-per-user-install ^
  --vendor "Changjiang" ^
  --app-version %VERSION% ^
  --copyright "Copyright (c) 2025"

if errorlevel 1 (
    echo [错误] jpackage 打包失败
    pause
    exit /b 1
)

echo.
echo [3/3] 打包完成！
echo 安装包位置：target\KeyStore-%VERSION%.exe
goto :end

:done
echo [3/3] 仅完成 JAR 打包，未生成 EXE
echo JAR 文件位置：target\keystore-1.0.0.jar

:end
echo.
pause
