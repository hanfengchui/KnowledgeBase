<template>
  <article class="card">
    <div class="card-head">
      <div>
        <h3>最近问答</h3>
        <p>保留 8 条最近问题，可快速复用。</p>
      </div>
      <el-button text @click="$emit('clear')">清空</el-button>
    </div>
    <el-empty v-if="history.length === 0" description="暂无历史提问" />
    <div v-else class="list-stack">
      <button
        v-for="item in history"
        :key="item.id"
        type="button"
        class="history-item"
        @click="$emit('reuse', item.question)"
      >
        <strong>{{ item.question }}</strong>
        <p>{{ item.answer }}</p>
        <span>{{ formatDate(item.createdAt) }} · 来源 {{ item.sourceCount }} · 工具 {{ item.usedTools ? '已触发' : '未触发' }}</span>
      </button>
    </div>
  </article>
</template>

<script setup>
import { formatDate } from '../../composables/useUtils'

defineProps({
  history: {
    type: Array,
    default: () => []
  }
})

defineEmits(['clear', 'reuse'])
</script>
