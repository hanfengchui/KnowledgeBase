<template>
  <article class="card">
    <el-table :data="knowledgeBases" stripe>
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="code" label="代码" width="180" />
      <el-table-column prop="documentCount" label="文档数" width="100" />
      <el-table-column label="属性" width="220">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" size="small" type="success">默认</el-tag>
          <el-tag v-if="row.id === selectedKnowledgeBaseId" size="small" type="warning">当前</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="260" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button text type="primary" @click="$emit('switch', row.id)">切换</el-button>
          <el-button
            text
            type="warning"
            :disabled="!canUpdateKnowledgeBase(row.id)"
            @click="$emit('edit', row.id)"
          >
            编辑
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </article>
</template>

<script setup>
import { formatDate } from '../../composables/useUtils'

defineProps({
  knowledgeBases: {
    type: Array,
    default: () => []
  },
  selectedKnowledgeBaseId: {
    type: String,
    default: ''
  },
  canUpdateKnowledgeBase: {
    type: Function,
    required: true
  }
})

defineEmits(['switch', 'edit'])
</script>
