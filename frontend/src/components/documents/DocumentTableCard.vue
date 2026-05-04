<template>
  <article class="card">
    <div class="card-head">
      <div>
        <h3>当前知识库文档</h3>
        <p>{{ knowledgeBaseName || '未选择知识库' }} · 显示 {{ filteredCount }} / {{ totalCount }}</p>
      </div>
    </div>
    <el-table :data="documents" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="220" />
      <el-table-column prop="documentType" label="类型" width="120" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="Chunk 数" width="100" />
      <el-table-column prop="charCount" label="字符数" width="110" />
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="错误信息" min-width="220">
        <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
      </el-table-column>
    </el-table>
  </article>
</template>

<script setup>
import { formatDate } from '../../composables/useUtils'

defineProps({
  knowledgeBaseName: {
    type: String,
    default: ''
  },
  documents: {
    type: Array,
    default: () => []
  },
  filteredCount: {
    type: Number,
    default: 0
  },
  totalCount: {
    type: Number,
    default: 0
  },
  statusLabel: {
    type: Function,
    required: true
  },
  statusTagType: {
    type: Function,
    required: true
  }
})
</script>
