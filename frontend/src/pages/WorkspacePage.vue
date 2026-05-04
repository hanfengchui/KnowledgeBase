<template>
  <section class="page-grid">
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

    <ChatAnswerCard :knowledge-base-name="stats.knowledgeBaseName || currentKnowledgeBase?.name" :answer="answer" />

    <ChatHistoryCard :history="questionHistory" @clear="clearQuestionHistory" @reuse="reuseHistoryQuestion" />

    <ChatToolCallsCard :tool-calls="toolCalls" />

    <ChatSourcesCard :sources="sources" />
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
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
