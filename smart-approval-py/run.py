#!/usr/bin/env python3
"""
SmartApproval Python 版 —— 项目入口
直接运行此文件即可启动：python run.py
"""
import subprocess
import sys
import os

os.chdir(os.path.dirname(os.path.abspath(__file__)))

subprocess.run(
    [sys.executable, "-m", "uvicorn", "app.main:app",
     "--host", "0.0.0.0", "--port", "8081", "--reload"],
    check=True
)
