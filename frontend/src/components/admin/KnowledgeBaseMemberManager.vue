<template>
  <article class="card">
    <div class="card-head">
      <div>
        <h3>知识库授权</h3>
        <p>仅 `tenant_admin`、`platform_admin` 或当前知识库 `kb_admin` 可修改成员。</p>
      </div>
      <el-button :loading="loadingMembers" @click="$emit('refresh')">刷新成员</el-button>
    </div>

    <div class="filter-grid">
      <el-select v-model="form.userId" placeholder="选择用户">
        <el-option
          v-for="user in users"
          :key="user.id"
          :label="`${user.displayName} (${user.username})`"
          :value="user.id"
        />
      </el-select>
      <el-select v-model="form.roleCodes" multiple collapse-tags placeholder="选择知识库角色">
        <el-option v-for="item in kbRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
      </el-select>
      <el-button type="primary" :disabled="!canManage" @click="$emit('save')">
        保存授权
      </el-button>
    </div>

    <el-table :data="members" stripe>
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
          <el-button text type="danger" :disabled="!canManage" @click="$emit('remove', row)">
            移除
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
  members: {
    type: Array,
    default: () => []
  },
  loadingMembers: {
    type: Boolean,
    default: false
  },
  form: {
    type: Object,
    required: true
  },
  kbRoleOptions: {
    type: Array,
    default: () => []
  },
  canManage: {
    type: Boolean,
    default: false
  }
})

defineEmits(['refresh', 'save', 'remove'])
</script>
