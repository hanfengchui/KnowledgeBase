import { computed, reactive, ref } from 'vue'
import { clearAccessToken, getAccessToken, getMe, login, logout, setAccessToken, switchTenant } from '../api'

const authReady = ref(false)
const loadingSession = ref(false)
const loginSubmitting = ref(false)
const session = ref(null)
const selectedTenantId = ref('')

const loginForm = reactive({
  username: 'platform-admin',
  password: 'ChangeMe123!',
  tenantCode: ''
})

const isPlatformAdmin = computed(() => Boolean(session.value?.platformAdmin))
const availableTenants = computed(() => session.value?.availableTenants || [])
const tenantRoleCodes = computed(() => session.value?.roleCodes || [])
const tenantPermissionCodes = computed(() => new Set(session.value?.permissionCodes || []))

function applySession(nextSession) {
  session.value = nextSession
  selectedTenantId.value = nextSession?.tenantId || ''
}

function clearSession() {
  clearAccessToken()
  applySession(null)
}

function markReady() {
  authReady.value = true
}

function hasTenantPermission(code) {
  return isPlatformAdmin.value || tenantPermissionCodes.value.has(code)
}

async function fetchSession() {
  const nextSession = await getMe()
  applySession(nextSession)
  return nextSession
}

async function loginWithPassword() {
  const response = await login({
    username: loginForm.username,
    password: loginForm.password,
    tenantCode: loginForm.tenantCode || null
  })
  setAccessToken(response.accessToken)
  return response
}

async function logoutCurrentSession() {
  try {
    await logout()
  } finally {
    clearSession()
  }
}

async function switchCurrentTenant(tenantId) {
  const response = await switchTenant(tenantId)
  setAccessToken(response.accessToken)
  return response
}

export function useAuth() {
  return {
    authReady,
    loadingSession,
    loginSubmitting,
    loginForm,
    session,
    selectedTenantId,
    isPlatformAdmin,
    availableTenants,
    tenantRoleCodes,
    tenantPermissionCodes,
    getAccessToken,
    applySession,
    clearSession,
    markReady,
    hasTenantPermission,
    fetchSession,
    loginWithPassword,
    logoutCurrentSession,
    switchCurrentTenant
  }
}
