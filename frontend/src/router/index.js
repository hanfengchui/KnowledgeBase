import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '../components/layout/AppShell.vue'
import { hasAccessToken } from '../api'
import AuditLogsPage from '../pages/AuditLogsPage.vue'
import DocumentsPage from '../pages/DocumentsPage.vue'
import KnowledgeBasesPage from '../pages/KnowledgeBasesPage.vue'
import LoginPage from '../pages/LoginPage.vue'
import RolesPage from '../pages/RolesPage.vue'
import TenantsPage from '../pages/TenantsPage.vue'
import UsersPage from '../pages/UsersPage.vue'
import WorkspacePage from '../pages/WorkspacePage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginPage
    },
    {
      path: '/',
      component: AppShell,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'workspace' } },
        { path: 'workspace', name: 'workspace', component: WorkspacePage, meta: { requiresAuth: true } },
        { path: 'documents', name: 'documents', component: DocumentsPage, meta: { requiresAuth: true } },
        { path: 'knowledge-bases', name: 'knowledge-bases', component: KnowledgeBasesPage, meta: { requiresAuth: true } },
        { path: 'admin/tenants', name: 'tenants', component: TenantsPage, meta: { requiresAuth: true } },
        { path: 'admin/users', name: 'users', component: UsersPage, meta: { requiresAuth: true } },
        { path: 'admin/roles', name: 'roles', component: RolesPage, meta: { requiresAuth: true } },
        { path: 'admin/audit-logs', name: 'audit', component: AuditLogsPage, meta: { requiresAuth: true } }
      ]
    }
  ]
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !hasAccessToken()) {
    return { name: 'login' }
  }
  if (to.name === 'login' && hasAccessToken()) {
    return { name: 'workspace' }
  }
  return true
})

export default router
