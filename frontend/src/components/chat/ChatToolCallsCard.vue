<template>
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
</template>

<script setup>
defineProps({
  toolCalls: {
    type: Array,
    default: () => []
  }
})
</script>
