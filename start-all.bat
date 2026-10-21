@echo off
echo ====================================
echo FeuerwerkAgent 完整启动脚本
echo ====================================
echo.

echo 1. 启动后端服务 (Spring Boot)
echo.
start "FeuerwerkAgent Backend" cmd /k "cd /d %~dp0 && mvn spring-boot:run"

timeout /t 10 /nobreak >nul

echo.
echo 2. 启动前端服务 (Vue)
echo.
start "FeuerwerkAgent Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo ====================================
echo 服务启动完成！
echo ====================================
echo.
echo 🌐 前端地址: http://localhost:5173
echo 🔧 后端地址: http://localhost:8080
echo 📖 API文档: http://localhost:8080/api/ai/health
echo.
echo 按任意键关闭此窗口...
pause >nul
