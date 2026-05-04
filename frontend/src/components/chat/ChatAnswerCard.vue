<template>
  <article class="card card--span-2">
    <div class="card-head">
      <div>
        <h3>回答结果</h3>
        <p>{{ knowledgeBaseName || '未选择知识库' }}</p>
      </div>
    </div>
    <div class="answer-box">{{ answer || '暂无回答。' }}</div>
  </article>
</template>

<script setup>
defineProps({
  knowledgeBaseName: {
    type: String,
    default: ''
  },
  answer: {
    type: String,
    default: ''
  }
})
</script>
