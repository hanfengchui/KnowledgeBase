<template>
  <section class="page-grid">
    <article class="card card--span-2">
      <div class="card-head">
        <div>
          <h3>知识问答</h3>
          <p>问题仅在当前知识库范围内召回。若无工具权限，订单类问题仍可提交但不会触发业务查询。</p>
        </div>
        <div class="inline-actions">
          <span class="field-label">Top K</span>
          <el-input-number v-model="topK" :min="1" :max="10" size="small" />
        </div>
      </div>

      <div class="question-chips">
        <el-button v-for="item in questionExamples" :key="item" class="chip" @click="question = item">
          {{ item }}
        </el-button>
      </div>

      <el-input
        v-model="question"
        type="textarea"
        :rows="5"
        placeholder="例如：企业标准退款规则是什么？或 查询 ORD-2026-0001 当前状态"
      />

      <div class="action-row">
        <span class="muted">
          当前权限：
          {{ canAskCurrentKnowledgeBase ? '可问答' : '不可问答' }}
          / {{ canUseToolInCurrentKnowledgeBase ? '可调工具' : '不可调工具' }}
        </span>
        <el-button type="primary" :loading="asking" :disabled="!canAskCurrentKnowledgeBase" @click="handleAsk">
          发送问题
        </el-button>
      </div>
    </article>

    <article class="card">
      <div class="card-head">
        <div>
          <h3>运行状态</h3>
          <p>当前回答的检索和模型信息。</p>
        </div>
      </div>
      <div class="metric-grid metric-grid--light">
        <div>
          <span>耗时</span>
          <strong>{{ stats.latencyMs ?? '-' }} ms</strong>
        </div>
        <div>
          <span>召回数</span>
          <strong>{{ stats.retrievedCount ?? '-' }}</strong>
        </div>
        <div>
          <span>Chat</span>
          <strong>{{ stats.chatModel || '-' }}</strong>
        </div>
        <div>
          <span>Embedding</span>
          <strong>{{ stats.embeddingModel || '-' }}</strong>
        </div>
      </div>
      <div class="tag-row">
        <el-tag :type="stats.usedRag ? 'success' : 'info'">知识召回：{{ stats.usedRag ? '已命中' : '未命中' }}</el-tag>
        <el-tag :type="stats.usedTools ? 'warning' : 'info'">业务工具：{{ stats.usedTools ? '已触发' : '未触发' }}</el-tag>
      </div>
    </article>

    <article class="card card--span-2">
      <div class="card-head">
        <div>
          <h3>回答结果</h3>
          <p>{{ stats.knowledgeBaseName || currentKnowledgeBase?.name || '未选择知识库' }}</p>
        </div>
      </div>
      <div class="answer-box">{{ answer || '暂无回答。' }}</div>
    </article>

    <article class="card">
      <div class="card-head">
        <div>
          <h3>最近问答</h3>
          <p>保留 8 条最近问题，可快速复用。</p>
        </div>
        <el-button text @click="clearQuestionHistory">清空</el-button>
      </div>
      <el-empty v-if="questionHistory.length === 0" description="暂无历史提问" />
      <div v-else class="list-stack">
        <button
          v-for="item in questionHistory"
          :key="item.id"
          type="button"
          class="history-item"
          @click="reuseHistoryQuestion(item.question)"
        >
          <strong>{{ item.question }}</strong>
          <p>{{ item.answer }}</p>
          <span>{{ formatDate(item.createdAt) }} · 来源 {{ item.sourceCount }} · 工具 {{ item.usedTools ? '已触发' : '未触发' }}</span>
        </button>
      </div>
    </article>

    <article class="card">
      <div class="card-head">
        <div>
          <h3>工具调用记录</h3>
          <p>只有当前知识库具备工具权限时才会展示。</p>
        </div>
      </div>
      <el-empty v-if="toolCalls.length === 0" description="暂无工具调用记录" />
      <div v-else class="list-stack">
        <div v-for="(tool, index) in toolCalls" :key="`${tool.name}-${index}`" class="tool-item">
          <strong>{{ tool.name }}</strong>
          <span>{{ tool.arguments }}</span>
          <pre>{{ tool.result }}</pre>
        </div>
      </div>
    </article>

    <article class="card card--span-3">
      <div class="card-head">
        <div>
          <h3>来源片段</h3>
          <p>问答依据仅来自当前知识库内的召回片段。</p>
        </div>
      </div>
      <el-empty v-if="sources.length === 0" description="暂无来源片段" />
      <el-collapse v-else>
        <el-collapse-item v-for="(source, index) in sources" :key="index" :name="index">
          <template #title>
            来源 {{ index + 1 }} · {{ source.documentName }} · chunk {{ source.chunkIndex }}
          </template>
          <div class="source-meta">
            <span>{{ source.knowledgeBaseName }}</span>
            <span>score {{ formatScore(source.score) }}</span>
          </div>
          <pre class="source-content">{{ source.content }}</pre>
        </el-collapse-item>
      </el-collapse>
    </article>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { useChat } from '../composables/useChat'
import { useKnowledgeBases } from '../composables/useKnowledgeBases'
import { extractError, formatDate, formatScore } from '../composables/useUtils'

const {
  question,
  topK,
  asking,
  answer,
  sources,
  toolCalls,
  questionHistory,
  stats,
  questionExamples,
  canAskCurrentKnowledgeBase,
  canUseToolInCurrentKnowledgeBase,
  clearQuestionHistory,
  reuseHistoryQuestion,
  submitQuestion
} = useChat()

const { currentKnowledgeBase, selectedKnowledgeBaseId } = useKnowledgeBases()

async function handleAsk() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }

  try {
    await submitQuestion()
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}
</script>
