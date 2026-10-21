@echo off
echo ====================================
echo FeuerwerkAgent Vue前端启动脚本
echo ====================================
echo.

cd frontend

echo 检查依赖是否已安装...
if not exist "node_modules" (
    echo 依赖未安装，正在安装...
    npm install
    echo.
)

echo.
echo 启动Vue开发服务器...
echo.
echo 前端地址: http://localhost:5173
echo 后端地址: http://localhost:8080
echo.
echo 按Ctrl+C停止服务器
echo.

npm run dev

pause
