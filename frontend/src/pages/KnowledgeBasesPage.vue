<template>
  <section class="page-stack">
    <article class="card">
      <div class="card-head">
        <div>
          <h3>知识库管理</h3>
          <p>知识库按当前租户隔离，列表已自动过滤用户无权访问的空间。</p>
        </div>
        <el-button type="primary" :disabled="!canCreateKnowledgeBase" @click="kbDialogVisible = true">
          新建知识库
        </el-button>
      </div>
    </article>

    <KnowledgeBaseTable
      :knowledge-bases="knowledgeBases"
      :selected-knowledge-base-id="selectedKnowledgeBaseId"
      :can-update-knowledge-base="canUpdateKnowledgeBase"
      @switch="handleSwitch"
      @edit="handleOpenEdit"
    />

    <KnowledgeBaseCreateDialog
      v-model="kbDialogVisible"
      :form="kbForm"
      :loading="creatingKnowledgeBase"
      @submit="handleCreate"
    />

    <KnowledgeBaseEditDialog
      v-model="kbEditDialogVisible"
      :form="kbEditForm"
      :loading="updatingKnowledgeBase"
      @closed="resetKnowledgeBaseEditForm"
      @cancel="closeKnowledgeBaseEditDialog"
      @submit="handleUpdate"
    />
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import KnowledgeBaseCreateDialog from '../components/knowledge-bases/KnowledgeBaseCreateDialog.vue'
import KnowledgeBaseEditDialog from '../components/knowledge-bases/KnowledgeBaseEditDialog.vue'
import KnowledgeBaseTable from '../components/knowledge-bases/KnowledgeBaseTable.vue'
import { useAppBootstrap } from '../composables/useAppBootstrap'
import { useKnowledgeBases } from '../composables/useKnowledgeBases'
import { extractError } from '../composables/useUtils'

const {
  knowledgeBases,
  selectedKnowledgeBaseId,
  kbDialogVisible,
  kbEditDialogVisible,
  creatingKnowledgeBase,
  updatingKnowledgeBase,
  kbForm,
  kbEditForm,
  canCreateKnowledgeBase,
  canUpdateKnowledgeBase,
  refreshKnowledgeBases,
  resetKnowledgeBaseEditForm,
  submitCreateKnowledgeBase,
  openKnowledgeBaseEditDialog,
  closeKnowledgeBaseEditDialog,
  submitUpdateKnowledgeBase
} = useKnowledgeBases()

const { changeKnowledgeBase, hydrateSession } = useAppBootstrap()

async function handleSwitch(knowledgeBaseId) {
  try {
    await changeKnowledgeBase(knowledgeBaseId)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleCreate() {
  if (!kbForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  try {
    const response = await submitCreateKnowledgeBase()
    await hydrateSession()
    await refreshKnowledgeBases()
    await changeKnowledgeBase(response.id)
    ElMessage.success(`知识库已创建：${response.name}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleOpenEdit(knowledgeBaseId) {
  const target = openKnowledgeBaseEditDialog(knowledgeBaseId)
  if (!target) {
    ElMessage.warning('未找到指定知识库')
  }
}

async function handleUpdate() {
  if (!kbEditForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  try {
    const response = await submitUpdateKnowledgeBase()
    await hydrateSession()
    await refreshKnowledgeBases()
    await changeKnowledgeBase(response.id || selectedKnowledgeBaseId.value)
    ElMessage.success(`知识库已更新：${response.name || kbEditForm.name.trim()}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}
</script>
