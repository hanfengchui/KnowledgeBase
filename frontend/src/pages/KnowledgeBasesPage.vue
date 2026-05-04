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
            <el-button text type="primary" @click="handleSwitch(row.id)">切换</el-button>
            <el-button
              text
              type="warning"
              :disabled="!canUpdateKnowledgeBase(row.id)"
              @click="handleOpenEdit(row.id)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <el-dialog v-model="kbDialogVisible" title="新建知识库" width="520px">
      <el-form label-position="top">
        <el-form-item label="知识库名称">
          <el-input v-model="kbForm.name" placeholder="例如：售后服务知识库" />
        </el-form-item>
        <el-form-item label="知识库代码">
          <el-input v-model="kbForm.code" placeholder="可选，例如：after-sales" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="kbForm.description" type="textarea" :rows="4" placeholder="简要说明覆盖范围" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingKnowledgeBase" @click="handleCreate">
          创建并切换
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="kbEditDialogVisible" title="编辑知识库" width="520px" @closed="resetKnowledgeBaseEditForm">
      <el-form label-position="top">
        <el-form-item label="知识库名称">
          <el-input v-model="kbEditForm.name" placeholder="例如：售后服务知识库" />
        </el-form-item>
        <el-form-item label="知识库代码">
          <el-input v-model="kbEditForm.code" disabled />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="kbEditForm.description" type="textarea" :rows="4" placeholder="简要说明覆盖范围" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeKnowledgeBaseEditDialog">取消</el-button>
        <el-button type="primary" :loading="updatingKnowledgeBase" @click="handleUpdate">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { useAppBootstrap } from '../composables/useAppBootstrap'
import { useKnowledgeBases } from '../composables/useKnowledgeBases'
import { extractError, formatDate } from '../composables/useUtils'

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
