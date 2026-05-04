<template>
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
          <el-button text @click="$emit('open-role', row)" :disabled="!canAssignRoles">分配角色</el-button>
          <el-button text @click="$emit('toggle-status', row)">
            {{ row.status === 'active' ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </article>
</template>

<script setup>
import { formatDate } from '../../composables/useUtils'

defineProps({
  users: {
    type: Array,
    default: () => []
  },
  canAssignRoles: {
    type: Boolean,
    default: false
  }
})

defineEmits(['open-role', 'toggle-status'])
</script>
