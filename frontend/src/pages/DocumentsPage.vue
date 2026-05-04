<template>
  <section class="page-stack">
    <article class="card">
      <div class="card-head">
        <div>
          <h3>文档入库</h3>
          <p>支持 `.txt / .md / .pdf / .docx`。上传和重建索引仍以当前知识库为边界。</p>
        </div>
        <div class="inline-actions">
          <el-button :loading="loadingDocuments" @click="refreshDocuments">刷新列表</el-button>
          <el-button :loading="reindexing" :disabled="!canReindexCurrentKnowledgeBase" @click="handleReindex">
            重建索引
          </el-button>
        </div>
      </div>

      <el-upload
        drag
        accept=".txt,.md,.pdf,.docx"
        :show-file-list="false"
        :http-request="handleUpload"
        :disabled="!canUploadCurrentKnowledgeBase"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到这里，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            {{ canUploadCurrentKnowledgeBase ? '上传后会自动解析、切分、写入向量或关键词检索索引。' : '当前账号在该知识库上没有上传权限。' }}
          </div>
        </template>
      </el-upload>
    </article>

    <article class="card">
      <div class="card-head">
        <div>
          <h3>文档筛选</h3>
          <p>按文件名、类型、状态、错误信息或哈希过滤当前知识库文档。</p>
        </div>
        <el-button text @click="resetDocumentFilters">清空筛选</el-button>
      </div>
      <div class="filter-grid">
        <el-input v-model="documentSearch" placeholder="搜索文件名、错误信息或哈希" clearable />
        <el-select v-model="documentStatusFilter" placeholder="全部状态" clearable>
          <el-option v-for="item in documentStatusOptions" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
      </div>
    </article>

    <article class="card">
      <div class="card-head">
        <div>
          <h3>当前知识库文档</h3>
          <p>{{ currentKnowledgeBase?.name || '未选择知识库' }} · 显示 {{ filteredDocuments.length }} / {{ documents.length }}</p>
        </div>
      </div>
      <el-table :data="filteredDocuments" stripe>
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
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useDocuments } from '../composables/useDocuments'
import { useKnowledgeBases } from '../composables/useKnowledgeBases'
import { extractError, formatDate } from '../composables/useUtils'

const {
  documents,
  loadingDocuments,
  reindexing,
  documentSearch,
  documentStatusFilter,
  documentStatusOptions,
  currentKnowledgeBase,
  filteredDocuments,
  canUploadCurrentKnowledgeBase,
  canReindexCurrentKnowledgeBase,
  refreshDocuments,
  submitUpload,
  submitReindex,
  resetDocumentFilters,
  statusLabel,
  statusTagType
} = useDocuments()

const { refreshKnowledgeBases, selectedKnowledgeBaseId } = useKnowledgeBases()

async function handleUpload(options) {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    options.onError?.(new Error('knowledge base required'))
    return
  }
  try {
    const response = await submitUpload(options.file)
    ElMessage.success(`${response.fileName} 已入库`)
    await refreshDocuments()
    await refreshKnowledgeBases()
    options.onSuccess?.(response)
  } catch (error) {
    ElMessage.error(extractError(error))
    options.onError?.(error)
  }
}

async function handleReindex() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  try {
    const response = await submitReindex()
    ElMessage.success(response.message || `已重建索引 ${response.chunkCount} 个 chunk`)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}
</script>
