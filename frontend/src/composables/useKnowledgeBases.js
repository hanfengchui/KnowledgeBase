import { computed, reactive, ref } from 'vue'
import { createKnowledgeBase, listKnowledgeBases, updateKnowledgeBase } from '../api'
import { useAuth } from './useAuth'

const knowledgeBases = ref([])
const loadingKnowledgeBases = ref(false)
const selectedKnowledgeBaseId = ref('')

const kbDialogVisible = ref(false)
const kbEditDialogVisible = ref(false)
const creatingKnowledgeBase = ref(false)
const updatingKnowledgeBase = ref(false)
const editingKnowledgeBaseId = ref('')

const kbForm = reactive({
  name: '',
  code: '',
  description: ''
})

const kbEditForm = reactive({
  name: '',
  code: '',
  description: ''
})

const { session, isPlatformAdmin, hasTenantPermission } = useAuth()

const currentKnowledgeBase = computed(() =>
  knowledgeBases.value.find((item) => item.id === selectedKnowledgeBaseId.value) || null
)

const knowledgeBaseAccessMap = computed(() => {
  const map = new Map()
  for (const item of session.value?.knowledgeBaseAccesses || []) {
    map.set(item.knowledgeBaseId, {
      roleCodes: item.roleCodes || [],
      permissionCodes: new Set(item.permissionCodes || [])
    })
  }
  return map
})

const currentKnowledgeBaseAccess = computed(() => knowledgeBaseAccessMap.value.get(selectedKnowledgeBaseId.value) || {
  roleCodes: [],
  permissionCodes: new Set()
})

const canCreateKnowledgeBase = computed(() => hasTenantPermission('kb.create'))

function hasCurrentKnowledgeBasePermission(code) {
  return isPlatformAdmin.value || currentKnowledgeBaseAccess.value.permissionCodes.has(code)
}

function canUpdateKnowledgeBase(knowledgeBaseId) {
  if (isPlatformAdmin.value || hasTenantPermission('kb.update')) {
    return true
  }
  return Boolean(knowledgeBaseAccessMap.value.get(knowledgeBaseId)?.permissionCodes.has('kb.update'))
}

async function refreshKnowledgeBases() {
  loadingKnowledgeBases.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
    if (!knowledgeBases.value.some((item) => item.id === selectedKnowledgeBaseId.value)) {
      selectedKnowledgeBaseId.value = knowledgeBases.value[0]?.id || ''
    }
  } finally {
    loadingKnowledgeBases.value = false
  }
}

function selectKnowledgeBase(knowledgeBaseId) {
  selectedKnowledgeBaseId.value = knowledgeBaseId
}

function resetKnowledgeBaseForm() {
  kbForm.name = ''
  kbForm.code = ''
  kbForm.description = ''
}

function resetKnowledgeBaseEditForm() {
  editingKnowledgeBaseId.value = ''
  kbEditForm.name = ''
  kbEditForm.code = ''
  kbEditForm.description = ''
}

async function submitCreateKnowledgeBase() {
  creatingKnowledgeBase.value = true
  try {
    const response = await createKnowledgeBase({
      name: kbForm.name,
      code: kbForm.code,
      description: kbForm.description
    })
    kbDialogVisible.value = false
    resetKnowledgeBaseForm()
    return response
  } finally {
    creatingKnowledgeBase.value = false
  }
}

function openKnowledgeBaseEditDialog(knowledgeBaseId) {
  const target = knowledgeBases.value.find((item) => item.id === knowledgeBaseId)
  if (!target) {
    return null
  }
  editingKnowledgeBaseId.value = knowledgeBaseId
  kbEditForm.name = target.name || ''
  kbEditForm.code = target.code || ''
  kbEditForm.description = target.description || ''
  kbEditDialogVisible.value = true
  return target
}

function closeKnowledgeBaseEditDialog() {
  kbEditDialogVisible.value = false
}

async function submitUpdateKnowledgeBase() {
  updatingKnowledgeBase.value = true
  try {
    const response = await updateKnowledgeBase(editingKnowledgeBaseId.value, {
      name: kbEditForm.name,
      code: kbEditForm.code,
      description: kbEditForm.description
    })
    closeKnowledgeBaseEditDialog()
    return response
  } finally {
    updatingKnowledgeBase.value = false
  }
}

function clearKnowledgeBaseState() {
  knowledgeBases.value = []
  selectedKnowledgeBaseId.value = ''
  kbDialogVisible.value = false
  kbEditDialogVisible.value = false
  resetKnowledgeBaseForm()
  resetKnowledgeBaseEditForm()
}

export function useKnowledgeBases() {
  return {
    knowledgeBases,
    loadingKnowledgeBases,
    selectedKnowledgeBaseId,
    currentKnowledgeBase,
    knowledgeBaseAccessMap,
    currentKnowledgeBaseAccess,
    kbDialogVisible,
    kbEditDialogVisible,
    creatingKnowledgeBase,
    updatingKnowledgeBase,
    editingKnowledgeBaseId,
    kbForm,
    kbEditForm,
    canCreateKnowledgeBase,
    hasCurrentKnowledgeBasePermission,
    canUpdateKnowledgeBase,
    refreshKnowledgeBases,
    selectKnowledgeBase,
    resetKnowledgeBaseForm,
    resetKnowledgeBaseEditForm,
    submitCreateKnowledgeBase,
    openKnowledgeBaseEditDialog,
    closeKnowledgeBaseEditDialog,
    submitUpdateKnowledgeBase,
    clearKnowledgeBaseState
  }
}
