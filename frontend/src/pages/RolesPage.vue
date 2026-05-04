<template>
  <section class="page-stack">
    <article class="card">
      <div class="card-head">
        <div>
          <h3>角色与权限</h3>
          <p>本期采用固定角色模型，不支持动态自定义角色。</p>
        </div>
      </div>
      <el-table :data="roles" stripe>
        <el-table-column prop="code" label="角色代码" width="180" />
        <el-table-column prop="name" label="角色名称" width="160" />
        <el-table-column prop="scopeType" label="作用域" width="140" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column label="权限点" min-width="280">
          <template #default="{ row }">
            <div class="tag-row">
              <el-tag v-for="code in row.permissionCodes" :key="code" size="small" effect="plain">{{ code }}</el-tag>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <article class="card">
      <div class="card-head">
        <div>
          <h3>知识库授权</h3>
          <p>仅 `tenant_admin`、`platform_admin` 或当前知识库 `kb_admin` 可修改成员。</p>
        </div>
        <el-button :loading="loadingMembers" @click="refreshKnowledgeBaseMembers">刷新成员</el-button>
      </div>

      <div class="filter-grid">
        <el-select v-model="memberForm.userId" placeholder="选择用户">
          <el-option
            v-for="user in users"
            :key="user.id"
            :label="`${user.displayName} (${user.username})`"
            :value="user.id"
          />
        </el-select>
        <el-select v-model="memberForm.roleCodes" multiple collapse-tags placeholder="选择知识库角色">
          <el-option v-for="item in kbRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
        </el-select>
        <el-button type="primary" :disabled="!canManageKnowledgeBaseMembers" @click="handleSaveKnowledgeBaseMember">
          保存授权
        </el-button>
      </div>

      <el-table :data="knowledgeBaseMembers" stripe>
        <el-table-column prop="displayName" label="用户" min-width="180" />
        <el-table-column prop="username" label="用户名" width="160" />
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <div class="tag-row">
              <el-tag v-for="code in row.roleCodes" :key="code" size="small" type="success" effect="plain">{{ code }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="授权时间" width="180">
          <template #default="{ row }">{{ formatDate(row.grantedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="danger" :disabled="!canManageKnowledgeBaseMembers" @click="handleDeleteKnowledgeBaseMember(row)">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>
  </section>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppBootstrap } from '../composables/useAppBootstrap'
import { useAdmin } from '../composables/useAdmin'
import { extractError, formatDate } from '../composables/useUtils'

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
