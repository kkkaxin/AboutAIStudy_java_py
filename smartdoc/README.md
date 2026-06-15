# SmartDoc — 企业级 AI 知识库问答系统

> 基于 Spring AI + Ollama + ChromaDB 的私有化智能文档问答平台

## 项目简介

SmartDoc 是一个企业内部文档知识库智能问答系统，用户上传 PDF/TXT/MD 文档后，系统自动完成文本解析、分块、向量化入库，通过 RAG（检索增强生成）架构实现精准的文档问答，支持 SSE 流式打字机效果输出。**全部组件本地化部署，数据不出本地。**

## 技术栈

| 层次 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.3 | 核心框架 |
| AI 接入 | Spring AI 1.0 | 统一 AI 接口层 |
| 本地大模型 | Ollama + Qwen2.5:7b | 本地推理，零 API 费用 |
| Embedding | Ollama nomic-embed-text | 文本向量化 |
| 向量数据库 | ChromaDB | 语义相似度检索 |
| 关系数据库 | MySQL 8 | 用户、文档、会话持久化 |
| 安全 | Spring Security + JWT | 无状态鉴权 |
| 前端 | Vue 3 | 对话交互界面 |

## 核心功能

- **知识库管理**：创建/删除知识库，上传 PDF/TXT/MD 文档，自动解析入库
- **RAG 智能问答**：基于向量相似度检索相关文档片段，注入 Prompt 后由大模型生成回答
- **SSE 流式输出**：大模型回答以流的形式推送，前端实现打字机效果
- **多轮对话**：保留会话历史，支持上下文连贯对话
- **用户权限**：JWT 登录鉴权，知识库按用户隔离

## 快速启动

### 1. 环境准备

```bash
# 安装 Ollama 并拉取模型
ollama pull qwen2.5:7b           # 对话模型
ollama pull nomic-embed-text     # Embedding 模型

# 启动 ChromaDB（Docker）
docker run -d -p 8000:8000 chromadb/chroma

# 创建 MySQL 数据库
mysql -u root -p -e "CREATE DATABASE smartdoc CHARACTER SET utf8mb4;"
mysql -u root -p smartdoc < src/main/resources/schema.sql
```

### 2. 修改配置

编辑 `src/main/resources/application.yml`：
- 修改 MySQL 密码
- 确认 Ollama 地址（默认 localhost:11434）
- 确认 ChromaDB 地址（默认 localhost:8000）

### 3. 启动项目

```bash
mvn spring-boot:run
```

服务启动后访问：`http://localhost:8080`

## 接口文档

### 认证接口
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 注册 |
| POST | /api/auth/login | 登录（返回 JWT Token） |

### 知识库接口
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/knowledge-base | 获取知识库列表 |
| POST | /api/knowledge-base | 创建知识库 |
| DELETE | /api/knowledge-base/{id} | 删除知识库 |

### 文档接口
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/documents/upload | 上传文档（multipart） |
| GET | /api/documents/list/{kbId} | 获取知识库文档列表 |
| DELETE | /api/documents/{docId} | 删除文档 |

### 对话接口
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/chat/stream | RAG 问答（SSE 流式，Content-Type: text/event-stream） |
| GET | /api/chat/conversations | 获取会话列表 |
| GET | /api/chat/conversations/{id}/messages | 获取会话历史 |
| DELETE | /api/chat/conversations/{id} | 删除会话 |

## RAG 流程说明

```
用户提问
   ↓
向量化（Embedding 模型）
   ↓
ChromaDB 相似度检索（按 kb_id 过滤，top-k=5）
   ↓
检索到相关文档片段
   ↓
拼接 Prompt（系统提示词 + 检索内容 + 历史消息 + 用户问题）
   ↓
Ollama 本地大模型生成回答
   ↓
SSE 流式推送到前端
   ↓
持久化存储（含引用来源）
```

## 项目结构

```
smartdoc/
├── src/main/java/com/smartdoc/
│   ├── SmartDocApplication.java     # 启动类
│   ├── config/
│   │   ├── AppConfig.java           # Spring AI ChatClient 配置
│   │   ├── SecurityConfig.java      # Spring Security + CORS 配置
│   │   └── JwtAuthFilter.java       # JWT 过滤器
│   ├── controller/
│   │   ├── AuthController.java      # 登录/注册
│   │   ├── KnowledgeBaseController.java  # 知识库 CRUD
│   │   ├── DocumentController.java  # 文档上传/管理
│   │   └── ChatController.java      # 核心：RAG 问答（SSE）
│   ├── service/
│   │   ├── ChatService.java         # RAG 核心逻辑
│   │   └── DocumentService.java     # 文档解析 + 向量入库
│   ├── entity/                      # JPA 实体
│   ├── dto/                         # 请求/响应 DTO
│   ├── repository/                  # Spring Data JPA
│   └── utils/                       # JWT、Security 工具类
└── src/main/resources/
    ├── application.yml              # 配置文件
    └── schema.sql                   # 数据库初始化
```

## 简历描述参考

**项目名称**：SmartDoc — 基于 Spring AI + Ollama 的企业级知识库问答系统

**技术栈**：Spring AI + Ollama + ChromaDB + SpringBoot 3 + Spring Security + MySQL + JWT + Vue 3

**项目亮点**：
1. 实现完整 RAG 链路：文档解析（PDF/TXT/MD）→ TokenTextSplitter 分块 → nomic-embed-text Embedding → ChromaDB 向量检索 → Prompt 注入 → Qwen2.5:7b 本地推理
2. 全组件本地化部署（Ollama + ChromaDB），数据不出本地，具备企业级隐私保障能力
3. 基于 Spring WebFlux 的 SSE 流式推送，前端实现打字机效果，大模型首字节响应时间 < 1s
4. 按知识库 ID 隔离向量检索空间，支持多用户独立知识库管理
5. Spring Security + JWT 无状态鉴权，对话历史多轮上下文管理（最近 N 轮）
