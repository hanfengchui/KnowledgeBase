# AI Interview Assistant

一个用于 Java AI 面试展示的本地项目：企业知识库 + 业务查询 AI 助手。

技术栈：

- 后端：Spring Boot 3、Spring AI、Ollama、本地大模型、PostgreSQL 17、pgvector
- 前端：Vue 3、Vite、Element Plus
- 默认模型：`qwen3:8b`、`bge-m3`

## 目录

```text
ai-interview-assistant/
  backend/   Spring Boot 后端
  frontend/  Vue 3 前端
  samples/   演示知识库文档
  docs/      面试讲解材料
```

## 本机环境

当前机器默认 Java 是 8；本项目需要 Java 17+ 编译运行。建议安装：

```bash
brew install openjdk@17 postgresql@17 pgvector
brew install ollama
brew services start postgresql@17
brew services start ollama
ollama pull qwen3:8b
ollama pull bge-m3
```

如果默认 `java` 仍是 Java 8，启动后端前手动设置 Homebrew 安装路径：

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="/opt/homebrew/opt/openjdk@17/bin:/opt/homebrew/opt/postgresql@17/bin:$PATH"
```

创建数据库：

```bash
createdb ai_interview
psql -d ai_interview -c 'CREATE EXTENSION IF NOT EXISTS vector;'
```

本项目默认数据库账号是 `postgres/postgres`。如果本机 Homebrew PostgreSQL 还没有该账号，可以执行：

```bash
psql -d postgres -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'postgres') THEN CREATE ROLE postgres WITH LOGIN SUPERUSER PASSWORD 'postgres'; ELSE ALTER ROLE postgres WITH LOGIN PASSWORD 'postgres'; END IF; END \$\$;"
psql -d postgres -c "ALTER DATABASE ai_interview OWNER TO postgres;"
```

如果之前已经执行过本机环境初始化，直接确认即可：

```bash
ollama list
psql ai_interview -c 'SELECT name, installed_version FROM pg_available_extensions WHERE name = '\''vector'\'';'
```

## 后端配置

复制本地配置文件：

```bash
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

本地配置默认连接 Ollama：

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen3:8b
      embedding:
        options:
          model: bge-m3
    vectorstore:
      pgvector:
        table-name: local_vector_store
        dimensions: 1024
```

说明：

- `qwen3:8b` 用于回答。
- `bge-m3` 用于 embedding，向量维度是 `1024`。
- `local_vector_store` 是本地模型专用向量表，避免和之前 OpenAI 1536 维表冲突。
- 如果之前已经上传过文档但没有启用向量库，启动项目后点击“重建向量索引”即可补齐 embedding。

启动后端：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

主要接口：

- `POST /api/documents`：上传 `.txt` / `.md` 文档并入库
- `GET /api/documents`：查看最近入库文档
- `POST /api/documents/reindex`：重建 pgvector 向量索引
- `POST /api/chat/ask`：提问，返回回答、来源、统计和工具调用记录

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:5173`

## 演示流程

1. 启动 PostgreSQL、后端、前端。
2. 在“文档上传”页上传 `samples/company_knowledge.md`。
3. 在“AI 问答”页依次测试：
   - `企业退款规则是什么？`
   - `知识库里有没有提到加班餐补标准？`
   - `查询 ORD-2026-0001 的订单状态`

前两个问题展示 RAG 命中/未命中；第三个问题展示本地业务工具查询。

## 面试讲解要点

- RAG 链路：上传文档 → chunk → embedding → pgvector → topK 召回 → 拼接上下文 → LLM 生成。
- 降低幻觉：系统提示词要求“无依据则说明不足”，接口返回召回来源，前端展示 score 和原文片段。
- 工具调用：订单类问题由后端工具 `queryOrder(orderNo)` 查询模拟业务系统，再把工具结果交给模型总结。
- 工程化：模型名、topK、阈值、向量表、数据库连接都放配置；本地模型不依赖外部 API Key；前后端分层明确。
