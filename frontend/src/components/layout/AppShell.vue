<template>
  <section class="workspace-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <span class="brand-mark">KH</span>
        <div>
          <span class="panel-kicker">KnowledgeHub AI</span>
          <h1>ToB 工作台</h1>
          <p>{{ session?.tenantName || '未选择租户' }}</p>
        </div>
      </div>

      <div class="sidebar-panel">
        <span class="panel-kicker">当前账号</span>
        <h2 class="panel-title">{{ session?.displayName }}</h2>
        <p class="panel-description">{{ session?.username }} · {{ isPlatformAdmin ? '平台管理员' : '租户账号' }}</p>
        <div class="tag-row">
          <el-tag v-for="code in session?.roleCodes || []" :key="code" size="small" effect="plain">{{ code }}</el-tag>
        </div>
      </div>

      <SidebarNav :items="visibleMenus" :active-name="String(route.name || '')" @navigate="handleNavigate" />

      <div class="sidebar-panel">
        <span class="panel-kicker">当前知识库</span>
        <h2 class="panel-title">{{ currentKnowledgeBase?.name || '未选择知识库' }}</h2>
        <div class="metric-grid">
          <div>
            <span>代码</span>
            <strong>{{ currentKnowledgeBase?.code || '-' }}</strong>
          </div>
          <div>
            <span>文档数</span>
            <strong>{{ currentKnowledgeBase?.documentCount ?? 0 }}</strong>
          </div>
          <div>
            <span>已入库</span>
            <strong>{{ indexedDocumentCount }}</strong>
          </div>
          <div>
            <span>失败</span>
            <strong>{{ failedDocumentCount }}</strong>
          </div>
        </div>
        <div class="tag-row">
          <el-tag
            v-for="code in currentKnowledgeBaseAccess.roleCodes"
            :key="code"
            size="small"
            type="success"
            effect="plain"
          >
            {{ code }}
          </el-tag>
        </div>
      </div>
    </aside>

    <section class="stage">
      <TopBar
        :current-section="currentSection"
        :is-platform-admin="isPlatformAdmin"
        :available-tenants="availableTenants"
        :selected-tenant-id="selectedTenantId"
        :knowledge-bases="knowledgeBases"
        :selected-knowledge-base-id="selectedKnowledgeBaseId"
        :loading-knowledge-bases="loadingKnowledgeBases"
        @switch-tenant="handleTenantSwitch"
        @switch-knowledge-base="handleKnowledgeBaseSwitch"
        @logout="handleLogout"
      />

      <section class="overview-grid">
        <article class="overview-card">
          <span>当前租户</span>
          <strong>{{ session?.tenantName || '-' }}</strong>
          <p>{{ session?.tenantCode || '-' }}</p>
        </article>
        <article class="overview-card">
          <span>可见知识库</span>
          <strong>{{ knowledgeBases.length }}</strong>
          <p>仅展示当前租户下当前用户有权访问的知识库。</p>
        </article>
        <article class="overview-card">
          <span>当前文档数</span>
          <strong>{{ documents.length }}</strong>
          <p>按当前知识库和授权范围过滤。</p>
        </article>
        <article class="overview-card">
          <span>最近提问</span>
          <strong>{{ latestQuestionLabel }}</strong>
          <p>问题历史按用户、租户和知识库本地隔离。</p>
        </article>
      </section>

      <RouterView />
    </section>
  </section>
</template>

<script setup>
import { computed, watchEffect } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterView, useRoute, useRouter } from 'vue-router'
import SidebarNav from './SidebarNav.vue'
import TopBar from './TopBar.vue'
import { navigationItems } from '../../config/navigation'
import { useAdmin } from '../../composables/useAdmin'
import { useAppBootstrap } from '../../composables/useAppBootstrap'
import { useAuth } from '../../composables/useAuth'
import { useChat } from '../../composables/useChat'
import { useDocuments } from '../../composables/useDocuments'
import { useKnowledgeBases } from '../../composables/useKnowledgeBases'
import { extractError } from '../../composables/useUtils'

const route = useRoute()
const router = useRouter()

const { session, isPlatformAdmin, availableTenants, selectedTenantId } = useAuth()
const {
  knowledgeBases,
  loadingKnowledgeBases,
  selectedKnowledgeBaseId,
  currentKnowledgeBase,
  currentKnowledgeBaseAccess,
  canCreateKnowledgeBase
} = useKnowledgeBases()
const { documents, indexedDocumentCount, failedDocumentCount } = useDocuments()
const { latestQuestionLabel } = useChat()
const {
  canViewTenants,
  canViewUsers,
  canAssignRoles,
  canManageKnowledgeBaseMembers,
  canViewAudit
} = useAdmin()
const {
  changeKnowledgeBase,
  logoutAndReset,
  switchTenantAndHydrate
} = useAppBootstrap()

const visibleMenus = computed(() => navigationItems.filter((item) => {
  if (item.name === 'workspace' || item.name === 'documents') {
    return knowledgeBases.value.length > 0
  }
  if (item.name === 'knowledge-bases') {
    return knowledgeBases.value.length > 0 || canCreateKnowledgeBase.value
  }
  if (item.name === 'tenants') {
    return canViewTenants.value
  }
  if (item.name === 'users') {
    return canViewUsers.value
  }
  if (item.name === 'roles') {
    return canAssignRoles.value || canManageKnowledgeBaseMembers.value
  }
  if (item.name === 'audit') {
    return canViewAudit.value
  }
  return false
}))

const currentSection = computed(() => {
  const active = visibleMenus.value.find((item) => item.name === route.name)
  if (!active) {
    return {
      kicker: 'KnowledgeHub AI',
      title: '企业知识库与业务助手平台',
      description: '基于租户和知识库权限的统一工作台。'
    }
  }
  return {
    kicker: 'KnowledgeHub AI',
    title: active.label,
    description: active.description
  }
})

watchEffect(() => {
  if (!session.value) {
    return
  }
  if (!visibleMenus.value.some((item) => item.name === route.name)) {
    const fallback = visibleMenus.value[0]
    if (fallback) {
      router.replace({ name: fallback.name })
    }
  }
})

function handleNavigate(name) {
  router.push({ name })
}

async function handleKnowledgeBaseSwitch(knowledgeBaseId) {
  try {
    await changeKnowledgeBase(knowledgeBaseId)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleTenantSwitch(tenantId) {
  if (!tenantId || tenantId === session.value?.tenantId) {
    return
  }
  try {
    await switchTenantAndHydrate(tenantId)
    ElMessage.success('已切换租户')
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleLogout() {
  try {
    await logoutAndReset()
  } catch (error) {
    ElMessage.warning(extractError(error))
  } finally {
    router.replace({ name: 'login' })
  }
}
</script>
