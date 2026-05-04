<template>
  <section class="page-stack">
    <DocumentUploadCard
      :loading-documents="loadingDocuments"
      :reindexing="reindexing"
      :can-upload="canUploadCurrentKnowledgeBase"
      :can-reindex="canReindexCurrentKnowledgeBase"
      @refresh="refreshDocuments"
      @reindex="handleReindex"
      @upload="handleUpload"
    />

    <DocumentFilterCard
      v-model:search="documentSearch"
      v-model:status="documentStatusFilter"
      :status-options="documentStatusOptions"
      :status-label="statusLabel"
      @clear="resetDocumentFilters"
    />

    <DocumentTableCard
      :knowledge-base-name="currentKnowledgeBase?.name"
      :documents="filteredDocuments"
      :filtered-count="filteredDocuments.length"
      :total-count="documents.length"
      :status-label="statusLabel"
      :status-tag-type="statusTagType"
    />
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import DocumentFilterCard from '../components/documents/DocumentFilterCard.vue'
import DocumentTableCard from '../components/documents/DocumentTableCard.vue'
import DocumentUploadCard from '../components/documents/DocumentUploadCard.vue'
import { useDocuments } from '../composables/useDocuments'
import { useKnowledgeBases } from '../composables/useKnowledgeBases'
import { extractError } from '../composables/useUtils'

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
