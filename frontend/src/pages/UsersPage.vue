<template>
  <section class="page-stack">
    <article class="card">
      <div class="card-head">
        <div>
          <h3>用户管理</h3>
          <p>管理员创建用户、启停账号，并按租户分配固定角色。</p>
        </div>
        <div class="inline-actions">
          <el-select
            v-if="isPlatformAdmin"
            v-model="userTenantFilter"
            class="field-select"
            placeholder="选择租户"
            @change="refreshUsers"
          >
            <el-option
              v-for="tenant in availableTenants"
              :key="tenant.id"
              :label="`${tenant.name} (${tenant.code})`"
              :value="tenant.id"
            />
          </el-select>
          <el-button type="primary" :disabled="!canCreateUsers" @click="userDialogVisible = true">创建用户</el-button>
        </div>
      </div>
    </article>

    <article class="card">
      <el-table :data="users" stripe>
        <el-table-column prop="username" label="用户名" width="160" />
        <el-table-column prop="displayName" label="显示名" width="160" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="租户角色" min-width="220">
          <template #default="{ row }">
            <div class="tag-row">
              <el-tag v-for="code in row.tenantRoleCodes" :key="code" size="small" effect="plain">{{ code }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平台角色" min-width="160">
          <template #default="{ row }">
            <div class="tag-row">
              <el-tag v-for="code in row.platformRoleCodes" :key="code" size="small" type="warning" effect="plain">
                {{ code }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button text @click="openRoleDialog(row)" :disabled="!canAssignRoles">分配角色</el-button>
            <el-button text @click="handleToggleUser(row)">
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <el-dialog v-model="userDialogVisible" title="创建用户" width="540px">
      <el-form label-position="top">
        <el-form-item v-if="isPlatformAdmin" label="所属租户">
          <el-select v-model="userForm.tenantId" class="dialog-select" placeholder="请选择租户">
            <el-option v-for="tenant in availableTenants" :key="tenant.id" :label="tenant.name" :value="tenant.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="userForm.displayName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="租户角色">
          <el-select v-model="userForm.tenantRoleCodes" multiple collapse-tags class="dialog-select">
            <el-option v-for="item in tenantRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingUser" @click="handleCreateUser">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="560px">
      <el-form label-position="top">
        <el-form-item label="用户">
          <el-input :model-value="roleDialogUserLabel" disabled />
        </el-form-item>
        <el-form-item v-if="isPlatformAdmin" label="平台角色">
          <el-select v-model="roleAssignForm.platformRoleCodes" multiple collapse-tags class="dialog-select">
            <el-option v-for="item in platformRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户角色">
          <el-select v-model="roleAssignForm.tenantRoleCodes" multiple collapse-tags class="dialog-select">
            <el-option v-for="item in tenantRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="handleAssignRoles">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { useAppBootstrap } from '../composables/useAppBootstrap'
import { useAdmin } from '../composables/useAdmin'
import { useAuth } from '../composables/useAuth'
import { extractError, formatDate } from '../composables/useUtils'

const { isPlatformAdmin, availableTenants } = useAuth()
const {
  users,
  userTenantFilter,
  userDialogVisible,
  roleDialogVisible,
  creatingUser,
  savingRoles,
  userForm,
  roleAssignForm,
  canCreateUsers,
  canAssignRoles,
  platformRoleOptions,
  tenantRoleOptions,
  roleDialogUserLabel,
  refreshUsers,
  submitCreateUser,
  openRoleDialog,
  submitAssignRoles,
  toggleUserStatus,
  resetUserForm
} = useAdmin()

const { hydrateSession } = useAppBootstrap()

async function handleCreateUser() {
  if (!userForm.username.trim() || !userForm.displayName.trim() || !userForm.password.trim()) {
    ElMessage.warning('请完整填写用户名、显示名和初始密码')
    return
  }
  if (isPlatformAdmin.value && !userForm.tenantId) {
    ElMessage.warning('平台管理员创建用户时必须选择所属租户')
    return
  }
  try {
    await submitCreateUser()
    userDialogVisible.value = false
    resetUserForm()
    await refreshUsers()
    ElMessage.success('用户已创建')
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleAssignRoles() {
  if (!roleAssignForm.userId) {
    ElMessage.warning('请先选择用户')
    return
  }
  try {
    await submitAssignRoles()
    roleDialogVisible.value = false
    await refreshUsers()
    await hydrateSession()
    ElMessage.success('角色已更新')
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleToggleUser(row) {
  try {
    const nextStatus = row.status === 'active' ? 'disabled' : 'active'
    await toggleUserStatus(row)
    await refreshUsers()
    ElMessage.success(`用户状态已更新为 ${nextStatus}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}
</script>
