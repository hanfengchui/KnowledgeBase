<template>
  <main class="app-page">
    <section v-if="!authReady || loadingSession" class="auth-shell">
      <div class="auth-panel auth-panel--loading">
        <span class="panel-kicker">KnowledgeHub AI</span>
        <h1>正在校验登录态</h1>
        <p>加载当前租户、知识库授权和后台权限配置。</p>
      </div>
    </section>

    <RouterView v-else />
  </main>
</template>

<script setup>
import { onMounted, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useAppBootstrap } from './composables/useAppBootstrap'
import { useAuth } from './composables/useAuth'
import { extractError } from './composables/useUtils'

const route = useRoute()
const router = useRouter()
const { authReady, loadingSession, session } = useAuth()
const { initializeApp } = useAppBootstrap()

onMounted(async () => {
  try {
    await initializeApp()
  } catch (error) {
    console.error(extractError(error))
  } finally {
    syncRoute()
  }
})

watch([authReady, loadingSession, session, () => route.name], () => {
  syncRoute()
})

function syncRoute() {
  if (!authReady.value || loadingSession.value) {
    return
  }
  if (!session.value && route.name !== 'login') {
    router.replace({ name: 'login' })
  }
  if (session.value && route.name === 'login') {
    router.replace({ name: 'workspace' })
  }
}
</script>
