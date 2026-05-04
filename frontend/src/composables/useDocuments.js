import { computed, ref } from 'vue'
import { listDocuments, reindexDocuments, uploadDocument } from '../api'
import { useAuth } from './useAuth'
import { useKnowledgeBases } from './useKnowledgeBases'

const documents = ref([])
const loadingDocuments = ref(false)
const reindexing = ref(false)
const documentSearch = ref('')
const documentStatusFilter = ref('')

const documentStatusOptions = ['pending', 'indexing', 'indexed', 'failed']

const { isPlatformAdmin, tenantRoleCodes } = useAuth()
const { currentKnowledgeBase, selectedKnowledgeBaseId, hasCurrentKnowledgeBasePermission } = useKnowledgeBases()

const indexedDocumentCount = computed(() => documents.value.filter((item) => item.status === 'indexed').length)
const failedDocumentCount = computed(() => documents.value.filter((item) => item.status === 'failed').length)
const filteredDocuments = computed(() => {
  const keyword = documentSearch.value.trim().toLowerCase()
  return documents.value.filter((item) => {
    if (documentStatusFilter.value && item.status !== documentStatusFilter.value) {
      return false
    }
    if (!keyword) {
      return true
    }
    return [item.fileName, item.documentType, item.errorMessage, item.contentHash]
      .some((field) => String(field || '').toLowerCase().includes(keyword))
  })
})

const canUploadCurrentKnowledgeBase = computed(() =>
  isPlatformAdmin.value
  || tenantRoleCodes.value.includes('tenant_admin')
  || hasCurrentKnowledgeBasePermission('document.upload')
)

const canReindexCurrentKnowledgeBase = computed(() =>
  isPlatformAdmin.value
  || tenantRoleCodes.value.includes('tenant_admin')
  || hasCurrentKnowledgeBasePermission('document.reindex')
)

async function refreshDocuments() {
  if (!selectedKnowledgeBaseId.value) {
    documents.value = []
    return
  }
  loadingDocuments.value = true
  try {
    documents.value = await listDocuments(selectedKnowledgeBaseId.value)
  } finally {
    loadingDocuments.value = false
  }
}

async function submitUpload(file) {
  return uploadDocument(file, selectedKnowledgeBaseId.value)
}

async function submitReindex() {
  reindexing.value = true
  try {
    return await reindexDocuments(selectedKnowledgeBaseId.value)
  } finally {
    reindexing.value = false
  }
}

function resetDocumentFilters() {
  documentSearch.value = ''
  documentStatusFilter.value = ''
}

function statusLabel(status) {
  return {
    pending: '待处理',
    indexing: '处理中',
    indexed: '已入库',
    failed: '失败'
  }[status] || status
}

function statusTagType(status) {
  return {
    pending: 'info',
    indexing: 'warning',
    indexed: 'success',
    failed: 'danger'
  }[status] || 'info'
}

function clearDocumentState() {
  documents.value = []
  documentSearch.value = ''
  documentStatusFilter.value = ''
}

export function useDocuments() {
  return {
    documents,
    loadingDocuments,
    reindexing,
    documentSearch,
    documentStatusFilter,
    documentStatusOptions,
    currentKnowledgeBase,
    indexedDocumentCount,
    failedDocumentCount,
    filteredDocuments,
    canUploadCurrentKnowledgeBase,
    canReindexCurrentKnowledgeBase,
    refreshDocuments,
    submitUpload,
    submitReindex,
    resetDocumentFilters,
    statusLabel,
    statusTagType,
    clearDocumentState
  }
}
