<template>
  <section class="page-stack">
    <RoleMatrixTable :roles="roles" />

    <KnowledgeBaseMemberManager
      :users="users"
      :members="knowledgeBaseMembers"
      :loading-members="loadingMembers"
      :form="memberForm"
      :kb-role-options="kbRoleOptions"
      :can-manage="canManageKnowledgeBaseMembers"
      @refresh="refreshKnowledgeBaseMembers"
      @save="handleSaveKnowledgeBaseMember"
      @remove="handleDeleteKnowledgeBaseMember"
    />
  </section>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import KnowledgeBaseMemberManager from '../components/admin/KnowledgeBaseMemberManager.vue'
import RoleMatrixTable from '../components/admin/RoleMatrixTable.vue'
import { useAppBootstrap } from '../composables/useAppBootstrap'
import { useAdmin } from '../composables/useAdmin'
import { extractError } from '../composables/useUtils'

const {
  roles,
  users,
  knowledgeBaseMembers,
  loadingMembers,
  memberForm,
  kbRoleOptions,
  canManageKnowledgeBaseMembers,
  refreshKnowledgeBaseMembers,
  submitKnowledgeBaseMember,
  removeKnowledgeBaseMember,
  resetMemberForm
} = useAdmin()

const { hydrateSession } = useAppBootstrap()

async function handleSaveKnowledgeBaseMember() {
  if (!memberForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  try {
    await submitKnowledgeBaseMember()
    resetMemberForm()
    await refreshKnowledgeBaseMembers()
    await hydrateSession()
    ElMessage.success('知识库授权已保存')
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleDeleteKnowledgeBaseMember(row) {
  try {
    await ElMessageBox.confirm(`确认移除 ${row.displayName} 的知识库授权吗？`, '移除授权', {
      type: 'warning'
    })
    await removeKnowledgeBaseMember(row.userId)
    await refreshKnowledgeBaseMembers()
    await hydrateSession()
    ElMessage.success('知识库授权已移除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(extractError(error))
    }
  }
}
</script>
