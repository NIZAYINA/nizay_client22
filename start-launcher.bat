@echo off
chcp 65001 >nul
cd /d "%~dp0launcher"
where node >nul 2>nul
if errorlevel 1 (
  echo [ОШИБКА] Node.js не найден. Установите LTS с https://nodejs.org и откройте это окно заново.
  pause
  exit /b 1
)
if not exist node_modules (
  echo Первый запуск: устанавливаю зависимости, подождите пару минут...
  call npm install
  if errorlevel 1 (
    echo.
    echo [ОШИБКА] npm install не удался. Сделайте скриншот текста выше.
    pause
    exit /b 1
  )
)
echo Запускаю лаунчер...
call npm start
if errorlevel 1 (
  echo.
  echo [ОШИБКА] Лаунчер завершился с ошибкой. Сделайте скриншот текста выше.
)
pause
