@echo off
chcp 65001 >nul
cd /d "%~dp0"
call npm install
call node build-all.js
echo.
echo Готово: dist\user (для друзей) и dist\admin (для вас)
pause
