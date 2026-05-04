import { useAdmin } from './useAdmin'
import { useAuth } from './useAuth'
import { useChat } from './useChat'
import { useDocuments } from './useDocuments'
import { useKnowledgeBases } from './useKnowledgeBases'

const auth = useAuth()
const knowledgeBases = useKnowledgeBases()
const chat = useChat()
const documents = useDocuments()
const admin = useAdmin()

async function refreshAllData() {
  await knowledgeBases.refreshKnowledgeBases()
  chat.syncKnowledgeBaseLabel()
  chat.loadQuestionHistory()
  await documents.refreshDocuments()

  if (admin.canViewTenants.value) {
    await admin.refreshTenants()
  }

  if (admin.canViewUsers.value || admin.canManageKnowledgeBaseMembers.value || admin.canViewAudit.value) {
    admin.userTenantFilter.value = admin.userTenantFilter.value || auth.selectedTenantId.value
    await admin.refreshUsers()
  }

  if (admin.canAssignRoles.value || admin.canManageKnowledgeBaseMembers.value) {
    await admin.refreshRoles()
  }

  if (admin.canManageKnowledgeBaseMembers.value) {
    await admin.refreshKnowledgeBaseMembers()
  }

  if (admin.canViewAudit.value) {
    await admin.refreshAuditLogs()
  }
}

function resetAllState() {
  knowledgeBases.clearKnowledgeBaseState()
  chat.clearChatState()
  documents.clearDocumentState()
  admin.clearAdminState()
}

async function hydrateSession() {
  auth.loadingSession.value = true
  try {
    await auth.fetchSession()
    await refreshAllData()
  } finally {
    auth.authReady.value = true
    auth.loadingSession.value = false
  }
}

async function initializeApp() {
  if (!auth.getAccessToken()) {
    auth.markReady()
    return
  }
  try {
    await hydrateSession()
  } catch (error) {
    auth.clearSession()
    resetAllState()
    auth.markReady()
    throw error
  }
}

async function loginAndHydrate() {
  auth.loginSubmitting.value = true
  try {
    await auth.loginWithPassword()
    await hydrateSession()
  } catch (error) {
    auth.clearSession()
    resetAllState()
    throw error
  } finally {
    auth.loginSubmitting.value = false
  }
}

async function logoutAndReset() {
  await auth.logoutCurrentSession()
  resetAllState()
  auth.markReady()
}

async function switchTenantAndHydrate(tenantId) {
  await auth.switchCurrentTenant(tenantId)
  await hydrateSession()
}

async function changeKnowledgeBase(knowledgeBaseId) {
  knowledgeBases.selectKnowledgeBase(knowledgeBaseId)
  chat.resetConversationView()
  chat.syncKnowledgeBaseLabel()
  chat.loadQuestionHistory()
  await documents.refreshDocuments()
  await admin.refreshKnowledgeBaseMembers()
}

export function useAppBootstrap() {
  return {
    ...auth,
    initializeApp,
    hydrateSession,
    loginAndHydrate,
    logoutAndReset,
    switchTenantAndHydrate,
    refreshAllData,
    resetAllState,
    changeKnowledgeBase
  }
}
