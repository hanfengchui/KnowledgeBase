<template>
  <section class="page-stack">
    <article class="card">
      <div class="card-head">
        <div>
          <h3>审计日志</h3>
          <p>支持按时间、用户、动作和资源类型筛选，返回当前租户最近 200 条操作记录。</p>
        </div>
        <el-button :loading="loadingAuditLogs" @click="refreshAuditLogs">查询</el-button>
      </div>

      <div class="filter-grid filter-grid--wide">
        <el-select
          v-if="isPlatformAdmin"
          v-model="auditFilters.tenantId"
          clearable
          placeholder="租户"
        >
          <el-option
            v-for="tenant in availableTenants"
            :key="tenant.id"
            :label="`${tenant.name} (${tenant.code})`"
            :value="tenant.id"
          />
        </el-select>
        <el-select v-model="auditFilters.userId" clearable placeholder="用户">
          <el-option
            v-for="user in users"
            :key="user.id"
            :label="`${user.displayName} (${user.username})`"
            :value="user.id"
          />
        </el-select>
        <el-input v-model="auditFilters.action" clearable placeholder="动作，例如 chat.ask" />
        <el-input v-model="auditFilters.resourceType" clearable placeholder="资源类型，例如 knowledge_base" />
        <el-date-picker
          v-model="auditFilters.dateRange"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </div>
    </article>

    <article class="card">
      <el-table :data="auditLogs" stripe>
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="180" />
        <el-table-column prop="resourceType" label="资源类型" width="160" />
        <el-table-column prop="resourceId" label="资源 ID" min-width="200" />
        <el-table-column prop="username" label="用户" width="140" />
        <el-table-column prop="responseStatus" label="状态码" width="100" />
        <el-table-column prop="requestPath" label="路径" min-width="180" />
        <el-table-column prop="requestPayloadSummary" label="摘要" min-width="260" />
      </el-table>
    </article>
  </section>
</template>

<script setup>
import { useAdmin } from '../composables/useAdmin'
import { useAuth } from '../composables/useAuth'
import { formatDate } from '../composables/useUtils'

const { isPlatformAdmin, availableTenants } = useAuth()
const { users, auditLogs, loadingAuditLogs, auditFilters, refreshAuditLogs } = useAdmin()
</script>
