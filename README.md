# KnowledgeHub AI

一个可本地运行的 ToB v1 企业知识库与业务助手平台示例，覆盖：

- 本地账号密码登录
- JWT 鉴权
- 基础多租户隔离
- 固定角色与知识库授权
- 审计留痕
- 知识库问答、文档入库、订单查询工具调用

## 当前版本能力

- 认证与会话
  - `username / password -> JWT access token`
  - `GET /api/auth/me` 返回当前用户、当前租户、角色、权限和知识库访问范围
  - `POST /api/auth/logout` 记录审计并吊销当前 token
  - 平台管理员支持切换当前租户上下文

- 多租户与权限
  - 业务数据按 `tenant_id` 强制隔离
  - 知识库可见性按当前用户权限过滤
  - 问答、文档上传、重建索引、知识库成员授权全部走后端权限校验
  - 向量 metadata 同时写入 `tenant_id` 和 `knowledge_base_id`

- 管理后台
  - 租户管理
  - 用户管理
  - 角色分配
  - 知识库成员授权
  - 审计日志查询

- RAG 与工具能力
  - 文档解析支持 `.txt / .md / .pdf / .docx`
  - 优先向量检索，失败时自动回退关键词检索
  - 无依据时统一返回“知识库未提供足够依据”
  - 订单类问题可触发示例工具 `queryOrder`
  - 无工具权限时仍可提问，但不会执行工具调用

## 技术栈

- 后端：Spring Boot 3、Spring Security、Spring AI、PostgreSQL 17、pgvector、Ollama
- 前端：Vue 3、Vite、Element Plus
- 默认模型：`qwen3:8b`、`bge-m3`
- Java 包名：`com.example.knowledgeassistant`

## 本地启动

### 1. 切换到 Java 17

当前机器若默认 `java` 仍指向 Java 8，需要先切换：

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/postgresql@17/bin:$PATH"
```

如本机缺少依赖，可执行：

```bash
brew install openjdk@17 postgresql@17 pgvector
brew install ollama
brew services start postgresql@17
brew services start ollama
ollama pull qwen3:8b
ollama pull bge-m3
```

### 2. 初始化数据库

```bash
createdb knowledge_assistant
psql -d knowledge_assistant -c 'CREATE EXTENSION IF NOT EXISTS vector;'
```

默认数据库账号为 `postgres/postgres`。

### 3. 启动后端

```bash
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

后端启动时会自动初始化：

- 平台管理员账号
- 演示租户
- 演示租户管理员账号
- 固定角色、权限映射
- 演示租户默认知识库

默认配置项见：

- [application.yml](/Users/df/Desktop/ai-interview-assistant/backend/src/main/resources/application.yml)
- [application-local.yml.example](/Users/df/Desktop/ai-interview-assistant/backend/src/main/resources/application-local.yml.example)

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问地址：`http://localhost:5173`

## 默认账号

- 平台管理员：`platform-admin / ChangeMe123!`
- 演示租户管理员：`tenant-admin / TenantAdmin123!`

`platform-admin` 登录后可切换到任意已启用租户。

## 主要接口

### 认证

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `POST /api/auth/switch-tenant`

### 业务

- `GET /api/knowledge-bases`
- `POST /api/knowledge-bases`
- `GET /api/documents?knowledgeBaseId=...`
- `POST /api/documents`
- `POST /api/documents/reindex?knowledgeBaseId=...`
- `POST /api/chat/ask`

### 管理后台

- `GET /api/admin/tenants`
- `POST /api/admin/tenants`
- `PATCH /api/admin/tenants/{id}`
- `GET /api/admin/users`
- `POST /api/admin/users`
- `PATCH /api/admin/users/{id}`
- `GET /api/admin/roles`
- `POST /api/admin/users/{id}/roles`
- `GET /api/admin/knowledge-bases/{id}/members`
- `POST /api/admin/knowledge-bases/{id}/members`
- `DELETE /api/admin/knowledge-bases/{id}/members?userId=...`
- `GET /api/admin/audit-logs`

## 核心表

- `tenants`
- `users`
- `roles`
- `permissions`
- `role_permissions`
- `user_roles`
- `knowledge_bases`
- `knowledge_base_members`
- `kb_documents`
- `kb_chunks`
- `revoked_tokens`
- `audit_logs`
- `local_vector_store`

库表脚本见 [schema.sql](/Users/df/Desktop/ai-interview-assistant/backend/src/main/resources/schema.sql)。

## 推荐演示路径

1. 使用 `platform-admin` 登录，查看租户管理和审计日志。
2. 切到 `demo` 租户，进入用户管理或直接退出。
3. 使用 `tenant-admin` 登录，创建一个普通用户并分配租户角色。
4. 新建知识库或进入默认知识库，上传 `samples/` 下的文档。
5. 在工作台提问：
   - `企业标准退款规则是什么？`
   - `客户要求接入 Oracle 和 Elasticsearch 时需要走什么流程？`
   - `查询 ORD-2026-0001 当前状态`
6. 在角色授权页给某个用户分配 `kb_viewer / kb_editor / kb_tool_operator`，再重新登录验证不同菜单和操作差异。

## 已验证

- 后端：`mvn test`
- 前端：`npm run build`
