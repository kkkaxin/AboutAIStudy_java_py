@echo off
chcp 65001 >nul
echo === SmartApproval Python 版 启动脚本 ===
echo.

:: 检查虚拟环境是否存在
if not exist ".venv\Scripts\uvicorn.exe" (
    echo [1/3] 创建虚拟环境并安装依赖...
    python -m venv .venv
    .venv\Scripts\pip install -r requirements.txt -q
    if errorlevel 1 (
        echo [错误] 依赖安装失败，请检查 Python 环境
        pause
        exit /b 1
    )
    echo     依赖安装完成
) else (
    echo [1/3] 虚拟环境已存在，跳过安装
)

echo [2/3] 提示：确认 Ollama 已启动
echo    如未启动: ollama serve
echo    如未拉取模型: ollama pull llama3.2
echo    未启动 Ollama 不影响系统运行，只是 AI 分析会降级为默认结果
echo.

echo [3/3] 启动服务...
echo    访问地址: http://localhost:8081
echo    按 Ctrl+C 停止服务
echo.
.venv\Scripts\uvicorn.exe app.main:app --host 0.0.0.0 --port 8081 --reload
pause
