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

    <UserTable :users="users" :can-assign-roles="canAssignRoles" @open-role="openRoleDialog" @toggle-status="handleToggleUser" />

    <UserCreateDialog
      v-model="userDialogVisible"
      :is-platform-admin="isPlatformAdmin"
      :available-tenants="availableTenants"
      :form="userForm"
      :tenant-role-options="tenantRoleOptions"
      :loading="creatingUser"
      @submit="handleCreateUser"
    />

    <RoleAssignDialog
      v-model="roleDialogVisible"
      :is-platform-admin="isPlatformAdmin"
      :user-label="roleDialogUserLabel"
      :form="roleAssignForm"
      :platform-role-options="platformRoleOptions"
      :tenant-role-options="tenantRoleOptions"
      :loading="savingRoles"
      @submit="handleAssignRoles"
    />
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import RoleAssignDialog from '../components/admin/RoleAssignDialog.vue'
import UserCreateDialog from '../components/admin/UserCreateDialog.vue'
import UserTable from '../components/admin/UserTable.vue'
import { useAppBootstrap } from '../composables/useAppBootstrap'
import { useAdmin } from '../composables/useAdmin'
import { useAuth } from '../composables/useAuth'
import { extractError } from '../composables/useUtils'

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
