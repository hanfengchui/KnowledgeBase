<template>
  <main class="page">
    <section class="hero">
      <div>
        <h1>企业知识库 + 业务查询 AI 助手</h1>
        <p>面试展示：RAG、Embedding、pgvector、Tool Calling、结构化输出与工程化落地。</p>
      </div>
      <el-tag size="large" type="success">Spring Boot 3 / Vue 3 / PostgreSQL + pgvector</el-tag>
    </section>

    <el-tabs v-model="activeTab" type="border-card" class="card">
      <el-tab-pane label="AI 问答" name="chat">
        <div class="chat-layout">
          <section>
            <div class="toolbar">
              <span class="muted">输入问题，后端会先向量召回，再调用模型生成答案。</span>
              <el-input-number v-model="topK" :min="1" :max="10" size="small" />
            </div>

            <el-input
              v-model="question"
              type="textarea"
              :rows="4"
              placeholder="例如：企业退款规则是什么？或 查询 ORD-2026-0001 的订单状态"
            />

            <div style="margin: 12px 0">
              <el-button
                v-for="item in demoQuestions"
                :key="item"
                class="demo-question"
                @click="question = item"
              >
                {{ item }}
              </el-button>
            </div>

            <el-button type="primary" :loading="asking" @click="handleAsk">发送问题</el-button>

            <el-divider />

            <h3>回答</h3>
            <div class="answer-box">{{ answer || '暂无回答。' }}</div>
          </section>

          <aside>
            <el-card shadow="never">
              <template #header>响应统计</template>
              <div class="stat-grid">
                <div class="stat-item">
                  <span class="muted">耗时</span>
                  <b>{{ stats.latencyMs ?? '-' }} ms</b>
                </div>
                <div class="stat-item">
                  <span class="muted">召回数</span>
                  <b>{{ stats.retrievedCount ?? '-' }}</b>
                </div>
                <div class="stat-item">
                  <span class="muted">Chat 模型</span>
                  <b>{{ stats.chatModel || '-' }}</b>
                </div>
                <div class="stat-item">
                  <span class="muted">Embedding</span>
                  <b>{{ stats.embeddingModel || '-' }}</b>
                </div>
              </div>
              <div style="margin-top: 12px">
                <el-tag :type="stats.usedRag ? 'success' : 'info'">RAG：{{ stats.usedRag ? '有召回' : '无召回' }}</el-tag>
                <el-tag :type="stats.usedTools ? 'warning' : 'info'" style="margin-left: 8px">
                  Tool：{{ stats.usedTools ? '已触发' : '未触发' }}
                </el-tag>
              </div>
            </el-card>

            <el-card shadow="never" style="margin-top: 14px">
              <template #header>工具调用</template>
              <el-empty v-if="toolCalls.length === 0" description="暂无工具调用" />
              <el-timeline v-else>
                <el-timeline-item v-for="(tool, index) in toolCalls" :key="index" :timestamp="tool.name">
                  <div class="muted">{{ tool.arguments }}</div>
                  <pre class="source-content">{{ tool.result }}</pre>
                </el-timeline-item>
              </el-timeline>
            </el-card>
          </aside>
        </div>

        <el-divider />

        <h3>召回来源</h3>
        <el-empty v-if="sources.length === 0" description="暂无召回来源" />
        <el-collapse v-else>
          <el-collapse-item v-for="(source, index) in sources" :key="index" :name="index">
            <template #title>
              来源 {{ index + 1 }}：{{ source.documentName }} / chunk {{ source.chunkIndex }}
              <el-tag size="small" style="margin-left: 8px">score {{ formatScore(source.score) }}</el-tag>
            </template>
            <pre class="source-content">{{ source.content }}</pre>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>

      <el-tab-pane label="文档上传" name="upload">
        <div class="toolbar">
          <span class="muted">支持 .txt / .md。上传后后端会切分 chunk、调用本地 bge-m3 生成 embedding，并写入 pgvector。</span>
          <div>
            <el-button :loading="reindexing" @click="handleReindex">重建向量索引</el-button>
            <el-button :loading="loadingDocuments" @click="refreshDocuments">刷新列表</el-button>
          </div>
        </div>

        <el-upload
          drag
          accept=".txt,.md"
          :show-file-list="false"
          :http-request="handleUpload"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到这里，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">建议上传公司制度、产品说明、FAQ 等文本材料。</div>
          </template>
        </el-upload>

        <el-divider />

        <h3>已入库文档</h3>
        <el-table :data="documents" stripe>
          <el-table-column prop="fileName" label="文件名" />
          <el-table-column prop="chunkCount" label="Chunk 数" width="120" />
          <el-table-column prop="createdAt" label="入库时间" width="220" />
          <el-table-column prop="contentHash" label="SHA-256" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { askQuestion, listDocuments, reindexDocuments, uploadDocument } from './api'

const activeTab = ref('chat')
const question = ref('')
const topK = ref(5)
const asking = ref(false)
const answer = ref('')
const sources = ref([])
const toolCalls = ref([])
const documents = ref([])
const loadingDocuments = ref(false)
const reindexing = ref(false)
const stats = reactive({
  latencyMs: null,
  retrievedCount: null,
  chatModel: '',
  embeddingModel: '',
  usedRag: false,
  usedTools: false
})

const demoQuestions = [
  '企业退款规则是什么？',
  '知识库里有没有提到加班餐补标准？',
  '查询 ORD-2026-0001 的订单状态'
]

async function handleAsk() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  asking.value = true
  try {
    const data = await askQuestion({ question: question.value, topK: topK.value })
    answer.value = data.answer
    sources.value = data.sources || []
    toolCalls.value = data.toolCalls || []
    Object.assign(stats, data.stats || {})
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    asking.value = false
  }
}

async function handleUpload(options) {
  try {
    const data = await uploadDocument(options.file)
    ElMessage.success(`入库成功：${data.fileName}，${data.chunkCount} 个 chunk`)
    await refreshDocuments()
    options.onSuccess(data)
  } catch (error) {
    const message = extractError(error)
    ElMessage.error(message)
    options.onError(error)
  }
}

async function refreshDocuments() {
  loadingDocuments.value = true
  try {
    documents.value = await listDocuments()
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    loadingDocuments.value = false
  }
}

async function handleReindex() {
  reindexing.value = true
  try {
    const data = await reindexDocuments()
    ElMessage.success(`向量索引已重建：${data.chunkCount} 个 chunk，表 ${data.vectorTableName}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    reindexing.value = false
  }
}

function formatScore(score) {
  return score == null ? 'n/a' : Number(score).toFixed(4)
}

function extractError(error) {
  return error?.response?.data?.error || error?.message || '请求失败'
}

onMounted(refreshDocuments)
</script>
