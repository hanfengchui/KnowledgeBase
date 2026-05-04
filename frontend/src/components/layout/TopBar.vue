<template>
  <header class="topbar">
    <div>
      <span class="panel-kicker">{{ currentSection.kicker }}</span>
      <h2>{{ currentSection.title }}</h2>
      <p>{{ currentSection.description }}</p>
    </div>

    <div class="topbar-actions">
      <div v-if="isPlatformAdmin" class="field-group">
        <span class="field-label">当前租户</span>
        <el-select :model-value="selectedTenantId" class="field-select" @change="$emit('switch-tenant', $event)">
          <el-option
            v-for="tenant in availableTenants"
            :key="tenant.id"
            :label="`${tenant.name} (${tenant.code})`"
            :value="tenant.id"
          />
        </el-select>
      </div>

      <div class="field-group">
        <span class="field-label">知识库</span>
        <el-select
          :model-value="selectedKnowledgeBaseId"
          class="field-select"
          placeholder="请选择知识库"
          :loading="loadingKnowledgeBases"
          @change="$emit('switch-knowledge-base', $event)"
        >
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="`${kb.name}${kb.isDefault ? ' · 默认' : ''}`"
            :value="kb.id"
          />
        </el-select>
      </div>

      <el-button @click="$emit('logout')">退出登录</el-button>
    </div>
  </header>
</template>

<script setup>
defineProps({
  currentSection: {
    type: Object,
    required: true
  },
  isPlatformAdmin: {
    type: Boolean,
    default: false
  },
  availableTenants: {
    type: Array,
    default: () => []
  },
  selectedTenantId: {
    type: String,
    default: ''
  },
  knowledgeBases: {
    type: Array,
    default: () => []
  },
  selectedKnowledgeBaseId: {
    type: String,
    default: ''
  },
  loadingKnowledgeBases: {
    type: Boolean,
    default: false
  }
})

defineEmits(['switch-tenant', 'switch-knowledge-base', 'logout'])
</script>
