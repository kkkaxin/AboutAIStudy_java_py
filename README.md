# AboutAIStudy_java_py

## 项目总览

本仓库包含两个独立的 Java Spring Boot 项目：

- `smart-approval`：企业级 AI 审批系统，基于 Spring AI Function Calling，支持 AI 自动审批建议、审批流程、JWT 鉴权、角色权限和限流。
- `smartdoc`：企业级 AI 知识库问答系统，基于 Spring AI + Ollama + ChromaDB，支持文档上传、RAG 检索增强生成、SSE 流式问答和多轮对话。

两个项目均使用 Java 17、Spring Boot 3.3、Spring Security、MySQL、JWT，并通过本地 Ollama 模型实现 AI 功能。

---

## 目录结构

```
AboutAIStudy_java_py/
├── README.md
├── smart-approval/           # AI 审批系统
├── smartdoc/                 # AI 知识库问答系统
└── uploads/                  # 示例文档上传存储（仅 smartdoc 可能使用）
```

---

## 公共准备

1. 安装 Java 17
2. 安装 Maven
3. 准备 MySQL 数据库
4. 安装并运行 Ollama（用于本地模型推理）

---

## smart-approval

### 项目简介

`smart-approval` 是一个企业智能审批系统，利用 Spring AI 和 Ollama 为审批申请生成 AI 分析建议，并支持常规审批流程管理。

### 技术栈

- Spring Boot 3.3
- Spring AI 1.0.7
- Spring Security + JWT
- MySQL
- Ollama 本地模型
- Spring Data JPA
- Lombok

### 核心功能

- 用户注册 / 登录
- 提交审批申请
- AI 审批建议与风险评估
- 审批历史记录查询
- 审批操作：同意 / 驳回 / 退回
- 限流：单用户每分钟最大审批请求数

### 关键配置

配置文件：`smart-approval/src/main/resources/application.yml`

- 服务端口：`8081`
- MySQL 数据源：`jdbc:mysql://localhost:3306/smart_approval`
- Ollama Base URL：`http://localhost:11434`
- AI 模型：`llama3.2`
- JWT 密钥与过期时间
- AI 审批超时与限流配置

### 主要接口

#### 认证
- `POST /api/auth/register` 注册
- `POST /api/auth/login` 登录

#### 审批
- `POST /api/approval/submit` 提交审批申请
- `POST /api/approval/action` 执行审批操作（同意/驳回/退回）
- `POST /api/approval/ai/analyze/{requestId}` AI 重新分析
- `GET /api/approval/pending` 获取待审批列表
- `GET /api/approval/my-requests` 我的申请列表
- `GET /api/approval/detail/{requestId}` 审批详情
- `GET /api/approval/records/{requestId}` 审批记录列表

### 运行方式

```bash
cd smart-approval
mvn spring-boot:run
```

服务启动后默认访问：`http://localhost:8081`

### 重要模块

- `com.smartapproval.controller`：REST 接口层
- `com.smartapproval.service`：审批业务逻辑和 AI 分析
- `com.smartapproval.function`：AI Function Calling 业务函数
- `com.smartapproval.security`：JWT 鉴权与安全过滤
- `com.smartapproval.config`：Spring Security、AI 客户端、限流拦截

---

## smartdoc

### 项目简介

`smartdoc` 是一个私有化 AI 文档知识库问答系统，支持 PDF/TXT/MD 文档上传、文本解析、向量化检索和本地模型问答。

### 技术栈

- Spring Boot 3.3
- Spring AI 1.0.7
- Ollama 本地模型 + ChromaDB 向量数据库
- MySQL
- Spring Security + JWT
- Spring Data JPA
- WebFlux SSE 流式问答

### 核心功能

- 用户注册 / 登录
- 知识库管理
- 文档上传与解析
- 文档向量化入库
- RAG 检索增强生成问答
- SSE 流式输出
- 会话历史管理

### 关键配置

配置文件：`smartdoc/src/main/resources/application.yml`

- 服务端口：`8080`（默认）
- MySQL 数据源：`jdbc:mysql://localhost:3306/smartdoc`
- Ollama Base URL：`http://localhost:11434`
- ChromaDB 地址：`http://localhost:8000`

### 主要接口

#### 认证
- `POST /api/auth/register` 注册
- `POST /api/auth/login` 登录

#### 知识库
- `POST /api/knowledge-base` 创建知识库
- `GET /api/knowledge-base` 列出知识库
- `GET /api/knowledge-base/{id}` 查询知识库详情
- `PUT /api/knowledge-base/{id}` 修改知识库
- `DELETE /api/knowledge-base/{id}` 删除知识库

#### 文档管理
- `POST /api/documents/upload` 上传文档
- `GET /api/documents` 获取文档列表
- `DELETE /api/documents/{docId}` 删除文档

#### 问答对话
- `POST /api/chat/stream` RAG 问答（SSE 流式）
- `GET /api/chat/conversations` 获取会话列表
- `GET /api/chat/conversations/{id}/messages` 获取会话消息
- `DELETE /api/chat/conversations/{id}` 删除会话

### 运行方式

```bash
cd smartdoc
mvn spring-boot:run
```

服务启动后默认访问：`http://localhost:8080`

### 重要模块

- `com.smartdoc.controller`：REST 接口层
- `com.smartdoc.service`：RAG 问答、文档解析、向量库管理
- `com.smartdoc.config`：Spring AI、JWT 安全与 WebFlux 配置
- `com.smartdoc.repository`：数据库实体与持久化
- `com.smartdoc.utils`：JWT 与辅助工具

---

## 运行提示

1. 如果需要启动本地 Ollama，请先安装并拉取模型。
2. smartdoc 还需要 ChromaDB 作为向量数据库，可用 Docker 启动。
3. 两个项目的 MySQL 数据库需要分别初始化，并确保用户名密码与 `application.yml` 中配置一致。
4. 若需要自定义端口或数据库连接，可编辑各项目的 `src/main/resources/application.yml`。

---

## 备注

本 README 仅作为项目概览与快速启动参考，具体接口实现与业务逻辑请参阅各项目源码。
