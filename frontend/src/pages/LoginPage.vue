<template>
  <section class="auth-shell">
    <div class="auth-panel">
      <span class="panel-kicker">KnowledgeHub AI</span>
      <h1>企业知识库与业务助手平台</h1>
      <p class="auth-copy">
        使用本地账号密码登录，进入租户隔离的知识问答工作台和管理后台。
      </p>

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
          登录
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
