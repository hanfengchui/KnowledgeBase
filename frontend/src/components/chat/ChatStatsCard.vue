<template>
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
</template>

<script setup>
defineProps({
  stats: {
    type: Object,
    required: true
  }
})
</script>
