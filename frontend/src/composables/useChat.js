import { computed, reactive, ref } from 'vue'
import { askQuestion } from '../api'
import { useAuth } from './useAuth'
import { useKnowledgeBases } from './useKnowledgeBases'

const question = ref('')
const topK = ref(5)
const asking = ref(false)
const answer = ref('')
const sources = ref([])
const toolCalls = ref([])
const questionHistory = ref([])

const stats = reactive({
  latencyMs: null,
  retrievedCount: null,
  chatModel: '',
  embeddingModel: '',
  usedRag: false,
  usedTools: false,
  knowledgeBaseName: ''
})

const questionExamples = [
  '企业标准退款规则是什么？',
  '接入向量检索前需要满足哪些数据库条件？',
  '客户要求接入 Oracle 和 Elasticsearch 时需要走什么流程？',
  '查询 ORD-2026-0001 当前状态'
]

const { session } = useAuth()
const {
  currentKnowledgeBase,
  selectedKnowledgeBaseId,
  hasCurrentKnowledgeBasePermission
} = useKnowledgeBases()

const canAskCurrentKnowledgeBase = computed(() => hasCurrentKnowledgeBasePermission('chat.ask'))
const canUseToolInCurrentKnowledgeBase = computed(() => hasCurrentKnowledgeBasePermission('tool.query_order'))
const latestQuestionLabel = computed(() => questionHistory.value[0] ? formatDate(questionHistory.value[0].createdAt) : '暂无历史')

function formatDate(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function resetStats() {
  Object.assign(stats, {
    latencyMs: null,
    retrievedCount: null,
    chatModel: '',
    embeddingModel: '',
    usedRag: false,
    usedTools: false,
    knowledgeBaseName: ''
  })
}

function questionHistoryStorageKey() {
  return `knowledgehub-history:${session.value?.userId || 'guest'}:${session.value?.tenantId || 'none'}:${selectedKnowledgeBaseId.value || 'none'}`
}

function loadQuestionHistory() {
  if (!selectedKnowledgeBaseId.value) {
    questionHistory.value = []
    return
  }
  const raw = window.localStorage.getItem(questionHistoryStorageKey())
  questionHistory.value = raw ? JSON.parse(raw) : []
}

function persistQuestionHistory() {
  window.localStorage.setItem(questionHistoryStorageKey(), JSON.stringify(questionHistory.value))
}

function recordQuestionHistory(entry) {
  questionHistory.value = [entry, ...questionHistory.value.filter((item) => item.question !== entry.question)].slice(0, 8)
  persistQuestionHistory()
}

function clearQuestionHistory() {
  questionHistory.value = []
  persistQuestionHistory()
}

function reuseHistoryQuestion(nextQuestion) {
  question.value = nextQuestion
}

function resetConversationView() {
  answer.value = ''
  sources.value = []
  toolCalls.value = []
  resetStats()
}

async function submitQuestion() {
  asking.value = true
  try {
    const response = await askQuestion({
      question: question.value,
      topK: topK.value,
      knowledgeBaseId: selectedKnowledgeBaseId.value
    })
    answer.value = response.answer
    sources.value = response.sources || []
    toolCalls.value = canUseToolInCurrentKnowledgeBase.value ? (response.toolCalls || []) : []
    Object.assign(stats, response.stats || {})
    recordQuestionHistory({
      id: crypto.randomUUID(),
      question: question.value,
      answer: response.answer,
      sourceCount: (response.sources || []).length,
      usedTools: (response.toolCalls || []).length > 0,
      createdAt: new Date().toISOString()
    })
    return response
  } finally {
    asking.value = false
  }
}

function syncKnowledgeBaseLabel() {
  stats.knowledgeBaseName = currentKnowledgeBase.value?.name || ''
}

function clearChatState() {
  question.value = ''
  topK.value = 5
  questionHistory.value = []
  resetConversationView()
}

export function useChat() {
  return {
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
    latestQuestionLabel,
    resetStats,
    loadQuestionHistory,
    persistQuestionHistory,
    recordQuestionHistory,
    clearQuestionHistory,
    reuseHistoryQuestion,
    resetConversationView,
    submitQuestion,
    syncKnowledgeBaseLabel,
    clearChatState
  }
}
