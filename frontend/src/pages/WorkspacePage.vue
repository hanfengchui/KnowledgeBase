<template>
  <section class="workspace-grid">
    <div class="workspace-column">
      <ChatQuestionCard
        v-model:question="question"
        v-model:top-k="topK"
        :asking="asking"
        :can-ask="canAskCurrentKnowledgeBase"
        :can-use-tools="canUseToolInCurrentKnowledgeBase"
        :question-examples="questionExamples"
        @submit="handleAsk"
      />

      <ChatStatsCard :stats="stats" />
    </div>

    <div class="workspace-column">
      <ChatAnswerCard :knowledge-base-name="stats.knowledgeBaseName || currentKnowledgeBase?.name" :answer="answer" />
      <ChatSourcesCard :sources="sources" />
    </div>

    <div class="workspace-column workspace-column--side">
      <ChatAgentTraceCard :agent-trace="agentTrace" />
      <ChatToolCallsCard :tool-calls="toolCalls" />
      <ChatHistoryCard :history="questionHistory" @clear="clearQuestionHistory" @reuse="reuseHistoryQuestion" />
    </div>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import ChatAgentTraceCard from '../components/chat/ChatAgentTraceCard.vue'
import ChatAnswerCard from '../components/chat/ChatAnswerCard.vue'
import ChatHistoryCard from '../components/chat/ChatHistoryCard.vue'
import ChatQuestionCard from '../components/chat/ChatQuestionCard.vue'
import ChatSourcesCard from '../components/chat/ChatSourcesCard.vue'
import ChatStatsCard from '../components/chat/ChatStatsCard.vue'
import ChatToolCallsCard from '../components/chat/ChatToolCallsCard.vue'
import { useChat } from '../composables/useChat'
import { useKnowledgeBases } from '../composables/useKnowledgeBases'
import { extractError } from '../composables/useUtils'

const {
  question,
  topK,
  asking,
  answer,
  sources,
  toolCalls,
  agentTrace,
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
