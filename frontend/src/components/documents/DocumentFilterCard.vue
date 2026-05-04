<template>
  <article class="card">
    <div class="card-head">
      <div>
        <h3>文档筛选</h3>
        <p>按文件名、类型、状态、错误信息或哈希过滤当前知识库文档。</p>
      </div>
      <el-button text @click="$emit('clear')">清空筛选</el-button>
    </div>
    <div class="filter-grid">
      <el-input v-model="searchModel" placeholder="搜索文件名、错误信息或哈希" clearable />
      <el-select v-model="statusModel" placeholder="全部状态" clearable>
        <el-option v-for="item in statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
      </el-select>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  search: {
    type: String,
    default: ''
  },
  status: {
    type: String,
    default: ''
  },
  statusOptions: {
    type: Array,
    default: () => []
  },
  statusLabel: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:search', 'update:status', 'clear'])

const searchModel = computed({
  get: () => props.search,
  set: (value) => emit('update:search', value)
})

const statusModel = computed({
  get: () => props.status,
  set: (value) => emit('update:status', value)
})
</script>
