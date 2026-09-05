@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo.
echo   Aether - подготовка окружения и сборка
echo   ======================================
echo.

REM --- Проверка расположения ---
if not exist "%~dp0build.gradle.kts" (
    echo [!] Файл build.gradle.kts не найден рядом со скриптом.
    echo     Положите setup.bat в корень распакованного проекта.
    goto :fail
)
cd /d "%~dp0"

REM --- Предупреждение о пути ---
echo %~dp0 | findstr /r "[^\x20-\x7e]" > nul
if not errorlevel 1 (
    echo [!] В пути к проекту есть символы вне латиницы:
    echo     %~dp0
    echo     Это ломает установку Forge. Перенесите проект, например, в C:\Projects\aether
    echo.
    pause
)

REM --- winget ---
where winget > nul 2>&1
if errorlevel 1 (
    echo [!] Не найден winget. Установите вручную:
    echo       JDK 21  - https://adoptium.net
    echo       Gradle  - https://gradle.org/install/
    goto :fail
)

REM --- JDK 21 ---
set NEED_RESTART=0
where java > nul 2>&1
if errorlevel 1 (
    echo [1/4] Устанавливаю JDK 21 ...
    winget install --id EclipseAdoptium.Temurin.21.JDK --accept-source-agreements --accept-package-agreements
    set NEED_RESTART=1
) else (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JV=%%v
    set JV=!JV:"=!
    for /f "delims=. tokens=1" %%m in ("!JV!") do set JMAJOR=%%m
    if !JMAJOR! LSS 21 (
        echo [1/4] Найдена Java !JV!, нужна 21 или новее. Устанавливаю ...
        winget install --id EclipseAdoptium.Temurin.21.JDK --accept-source-agreements --accept-package-agreements
        set NEED_RESTART=1
    ) else (
        echo [1/4] Java !JV! - подходит.
    )
)

REM --- Gradle (нужен один раз, чтобы сгенерировать wrapper) ---
if not exist "gradlew.bat" (
    where gradle > nul 2>&1
    if errorlevel 1 (
        echo [2/4] Устанавливаю Gradle ...
        winget install --id Gradle.Gradle --accept-source-agreements --accept-package-agreements
        set NEED_RESTART=1
    ) else (
        echo [2/4] Gradle уже установлен.
    )
) else (
    echo [2/4] Gradle Wrapper уже есть.
)

if "!NEED_RESTART!"=="1" (
    echo.
    echo   Установка завершена, но PATH в этом окне устарел.
    echo   Закройте окно и запустите setup.bat ещё раз.
    echo.
    pause
    exit /b 0
)

REM --- Wrapper ---
if not exist "gradlew.bat" (
    echo [3/4] Генерирую Gradle Wrapper ...
    call gradle wrapper
    if errorlevel 1 goto :fail
) else (
    echo [3/4] Wrapper на месте.
)

REM --- Сборка ---
echo [4/4] Собираю установщик. Первый запуск занимает несколько минут ...
echo.
call gradlew.bat packageMsi
if errorlevel 1 goto :fail

echo.
echo   Готово. Установщик лежит в:
echo   %~dp0build\compose\binaries\main\msi\
echo.
explorer "%~dp0build\compose\binaries\main\msi"
pause
exit /b 0

:fail
echo.
echo   Сборка не завершена. Сообщение об ошибке выше.
echo.
pause
exit /b 1
