"""应用配置"""
import os

# 数据库配置
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATABASE_URL = f"sqlite:///{os.path.join(BASE_DIR, 'smart_approval.db')}"

# JWT 配置
JWT_SECRET = "smart-approval-secret-key-2024"  # 生产环境需改用环境变量
JWT_ALGORITHM = "HS256"
JWT_EXPIRATION_HOURS = 24

# Ollama 配置
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2")
OLLAMA_TEMPERATURE = 0.3

# 限流配置
RATE_LIMIT_PER_MINUTE = 10

# 服务配置
HOST = "0.0.0.0"
PORT = 8081
