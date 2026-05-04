<template>
  <section class="page-stack">
    <article class="card">
      <div class="card-head">
        <div>
          <h3>租户管理</h3>
          <p>仅平台管理员可创建、启停和切换租户。</p>
        </div>
        <el-button type="primary" @click="tenantDialogVisible = true">新建租户</el-button>
      </div>
    </article>

    <article class="card">
      <el-table :data="tenants" stripe>
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="code" label="代码" width="180" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="用户数" width="100" />
        <el-table-column prop="knowledgeBaseCount" label="知识库数" width="110" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button text @click="handleToggleTenant(row)">
              {{ row.status === 'active' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <el-dialog v-model="tenantDialogVisible" title="新建租户" width="480px">
      <el-form label-position="top">
        <el-form-item label="租户名称">
          <el-input v-model="tenantForm.name" placeholder="例如：华东区演示租户" />
        </el-form-item>
        <el-form-item label="租户代码">
          <el-input v-model="tenantForm.code" placeholder="例如：east-demo" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tenantDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingTenant" @click="handleCreateTenant">创建</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { useAdmin } from '../composables/useAdmin'
import { extractError, formatDate } from '../composables/useUtils'

const {
  tenants,
  tenantDialogVisible,
  creatingTenant,
  tenantForm,
  refreshTenants,
  submitCreateTenant,
  toggleTenantStatus,
  resetTenantForm
} = useAdmin()

async function handleCreateTenant() {
  if (!tenantForm.name.trim()) {
    ElMessage.warning('请输入租户名称')
    return
  }
  try {
    await submitCreateTenant()
    tenantDialogVisible.value = false
    resetTenantForm()
    await refreshTenants()
    ElMessage.success('租户已创建')
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleToggleTenant(row) {
  try {
    const nextStatus = row.status === 'active' ? 'disabled' : 'active'
    await toggleTenantStatus(row)
    await refreshTenants()
    ElMessage.success(`租户状态已更新为 ${nextStatus}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}
</script>
