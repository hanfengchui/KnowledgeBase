<template>
  <section class="auth-shell auth-shell--landing">
    <div class="clay-hero" aria-hidden="true">
      <div class="clay-scene clay-scene--chat">
        <span class="clay-bot">
          <i></i>
          <i></i>
        </span>
        <div class="clay-bubble clay-bubble--wide"></div>
        <div class="clay-bubble"></div>
      </div>

      <div class="clay-feature-row">
        <div class="clay-feature clay-feature--mint">
          <span></span>
          <strong>RAG</strong>
        </div>
        <div class="clay-feature clay-feature--peach">
          <span></span>
          <strong>Tool</strong>
        </div>
        <div class="clay-feature clay-feature--lilac">
          <span></span>
          <strong>Trace</strong>
        </div>
      </div>

      <div class="clay-flow">
        <span></span>
        <span></span>
        <span></span>
      </div>
    </div>

    <div class="auth-story">
      <span class="panel-kicker">KnowledgeHub AI</span>
      <h1>把复杂的 AI Agent 揉成好上手的工作台</h1>
      <p class="auth-copy">
        文档、工具调用、权限和执行轨迹被捏合成一套柔软清楚的业务助手体验。
      </p>
      <div class="story-grid">
        <div class="story-card">
          <strong>知识像粘土一样可塑</strong>
          <span>上传文档后自动切片、向量化，并在回答时展示来源。</span>
        </div>
        <div class="story-card">
          <strong>工具调用更有手感</strong>
          <span>订单查询和业务工具按权限触发，过程可追踪。</span>
        </div>
        <div class="story-card">
          <strong>执行轨迹不再冰冷</strong>
          <span>RAG、工具、模型生成被串成能看懂的步骤。</span>
        </div>
      </div>
    </div>

    <div class="auth-panel clay-panel">
      <span class="panel-kicker">进入工作台</span>
      <h2>欢迎回来</h2>
      <p class="auth-copy">登录后继续整理知识库，观察 Agent 如何完成每一步。</p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" placeholder="platform-admin 或 tenant-admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="租户代码（可选，仅平台管理员）">
          <el-input v-model="loginForm.tenantCode" placeholder="例如：demo" />
        </el-form-item>
        <el-button type="primary" class="auth-button" :loading="loginSubmitting" @click="handleLogin">
          进入工作台
        </el-button>
      </el-form>

      <div class="auth-hint">
        <strong>默认账号</strong>
        <span>`platform-admin / ChangeMe123!`</span>
        <span>`tenant-admin / TenantAdmin123!`</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAppBootstrap } from '../composables/useAppBootstrap'
import { useAuth } from '../composables/useAuth'
import { extractError } from '../composables/useUtils'

const router = useRouter()
const { loginForm, loginSubmitting } = useAuth()
const { loginAndHydrate } = useAppBootstrap()

async function handleLogin() {
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  try {
    await loginAndHydrate()
    ElMessage.success('登录成功')
    router.replace({ name: 'workspace' })
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}
</script>
