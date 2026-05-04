import { computed, reactive, ref } from 'vue'
import {
  assignUserRoles,
  createTenant,
  createUser,
  deleteKnowledgeBaseMember,
  listAuditLogs,
  listKnowledgeBaseMembers,
  listRoles,
  listTenants,
  listUsers,
  saveKnowledgeBaseMember,
  updateTenant,
  updateUser
} from '../api'
import { useAuth } from './useAuth'
import { useKnowledgeBases } from './useKnowledgeBases'

const tenants = ref([])
const users = ref([])
const roles = ref([])
const knowledgeBaseMembers = ref([])
const auditLogs = ref([])

const loadingTenants = ref(false)
const loadingUsers = ref(false)
const loadingRoles = ref(false)
const loadingMembers = ref(false)
const loadingAuditLogs = ref(false)

const tenantDialogVisible = ref(false)
const userDialogVisible = ref(false)
const roleDialogVisible = ref(false)

const creatingTenant = ref(false)
const creatingUser = ref(false)
const savingRoles = ref(false)

const userTenantFilter = ref('')

const tenantForm = reactive({
  name: '',
  code: ''
})

const userForm = reactive({
  tenantId: '',
  username: '',
  displayName: '',
  email: '',
  password: '',
  tenantRoleCodes: []
})

const roleAssignForm = reactive({
  userId: '',
  platformRoleCodes: [],
  tenantRoleCodes: []
})

const memberForm = reactive({
  userId: '',
  roleCodes: []
})

const auditFilters = reactive({
  tenantId: '',
  userId: '',
  action: '',
  resourceType: '',
  dateRange: []
})

const {
  availableTenants,
  hasTenantPermission,
  isPlatformAdmin,
  selectedTenantId,
  tenantRoleCodes
} = useAuth()

const { selectedKnowledgeBaseId, hasCurrentKnowledgeBasePermission } = useKnowledgeBases()

const canViewTenants = computed(() => isPlatformAdmin.value)
const canViewUsers = computed(() => hasTenantPermission('user.read'))
const canCreateUsers = computed(() => hasTenantPermission('user.create'))
const canAssignRoles = computed(() => isPlatformAdmin.value || hasTenantPermission('role.assign'))
const canManageKnowledgeBaseMembers = computed(() =>
  isPlatformAdmin.value
  || tenantRoleCodes.value.includes('tenant_admin')
  || hasCurrentKnowledgeBasePermission('kb.authorize')
)
const canViewAudit = computed(() => hasTenantPermission('audit.read'))

const platformRoleOptions = computed(() => roles.value.filter((item) => item.scopeType === 'platform'))
const tenantRoleOptions = computed(() => roles.value.filter((item) => item.scopeType === 'tenant'))
const kbRoleOptions = computed(() => roles.value.filter((item) => item.scopeType === 'knowledge_base'))

const roleDialogUserLabel = computed(() => {
  const user = users.value.find((item) => item.id === roleAssignForm.userId)
  return user ? `${user.displayName} (${user.username})` : ''
})

async function refreshTenants() {
  if (!canViewTenants.value) {
    return
  }
  loadingTenants.value = true
  try {
    tenants.value = await listTenants()
  } finally {
    loadingTenants.value = false
  }
}

async function refreshUsers() {
  if (!canViewUsers.value && !canManageKnowledgeBaseMembers.value && !canViewAudit.value) {
    return
  }
  loadingUsers.value = true
  try {
    const tenantId = isPlatformAdmin.value ? (userTenantFilter.value || selectedTenantId.value) : selectedTenantId.value
    users.value = await listUsers(tenantId || undefined)
  } finally {
    loadingUsers.value = false
  }
}

async function refreshRoles() {
  if (!canAssignRoles.value && !canManageKnowledgeBaseMembers.value) {
    return
  }
  loadingRoles.value = true
  try {
    roles.value = await listRoles()
  } finally {
    loadingRoles.value = false
  }
}

async function refreshKnowledgeBaseMembers() {
  if (!selectedKnowledgeBaseId.value || !canManageKnowledgeBaseMembers.value) {
    knowledgeBaseMembers.value = []
    return
  }
  loadingMembers.value = true
  try {
    knowledgeBaseMembers.value = await listKnowledgeBaseMembers(selectedKnowledgeBaseId.value)
  } finally {
    loadingMembers.value = false
  }
}

async function refreshAuditLogs() {
  if (!canViewAudit.value) {
    return
  }
  loadingAuditLogs.value = true
  try {
    const params = {
      tenantId: isPlatformAdmin.value ? (auditFilters.tenantId || undefined) : undefined,
      userId: auditFilters.userId || undefined,
      action: auditFilters.action || undefined,
      resourceType: auditFilters.resourceType || undefined,
      createdFrom: auditFilters.dateRange?.[0] ? new Date(auditFilters.dateRange[0]).toISOString() : undefined,
      createdTo: auditFilters.dateRange?.[1] ? new Date(auditFilters.dateRange[1]).toISOString() : undefined
    }
    auditLogs.value = await listAuditLogs(params)
  } finally {
    loadingAuditLogs.value = false
  }
}

async function submitCreateTenant() {
  creatingTenant.value = true
  try {
    return await createTenant({
      name: tenantForm.name,
      code: tenantForm.code
    })
  } finally {
    creatingTenant.value = false
  }
}

async function toggleTenantStatus(row) {
  const nextStatus = row.status === 'active' ? 'disabled' : 'active'
  return updateTenant(row.id, { status: nextStatus })
}

async function submitCreateUser() {
  creatingUser.value = true
  try {
    return await createUser({
      tenantId: isPlatformAdmin.value ? userForm.tenantId : selectedTenantId.value,
      username: userForm.username,
      displayName: userForm.displayName,
      email: userForm.email,
      password: userForm.password,
      tenantRoleCodes: userForm.tenantRoleCodes
    })
  } finally {
    creatingUser.value = false
  }
}

function openRoleDialog(user) {
  roleAssignForm.userId = user.id
  roleAssignForm.platformRoleCodes = [...(user.platformRoleCodes || [])]
  roleAssignForm.tenantRoleCodes = [...(user.tenantRoleCodes || [])]
  roleDialogVisible.value = true
}

async function submitAssignRoles() {
  savingRoles.value = true
  try {
    return await assignUserRoles(roleAssignForm.userId, {
      tenantId: selectedTenantId.value,
      platformRoleCodes: roleAssignForm.platformRoleCodes,
      tenantRoleCodes: roleAssignForm.tenantRoleCodes
    })
  } finally {
    savingRoles.value = false
  }
}

async function toggleUserStatus(row) {
  const nextStatus = row.status === 'active' ? 'disabled' : 'active'
  return updateUser(row.id, { status: nextStatus })
}

async function submitKnowledgeBaseMember() {
  return saveKnowledgeBaseMember(selectedKnowledgeBaseId.value, {
    userId: memberForm.userId,
    roleCodes: memberForm.roleCodes
  })
}

async function removeKnowledgeBaseMember(userId) {
  return deleteKnowledgeBaseMember(selectedKnowledgeBaseId.value, userId)
}

function resetTenantForm() {
  tenantForm.name = ''
  tenantForm.code = ''
}

function resetUserForm() {
  userForm.tenantId = ''
  userForm.username = ''
  userForm.displayName = ''
  userForm.email = ''
  userForm.password = ''
  userForm.tenantRoleCodes = []
}

function resetRoleAssignForm() {
  roleAssignForm.userId = ''
  roleAssignForm.platformRoleCodes = []
  roleAssignForm.tenantRoleCodes = []
}

function resetMemberForm() {
  memberForm.userId = ''
  memberForm.roleCodes = []
}

function clearAdminState() {
  tenants.value = []
  users.value = []
  roles.value = []
  knowledgeBaseMembers.value = []
  auditLogs.value = []
  userTenantFilter.value = ''
  tenantDialogVisible.value = false
  userDialogVisible.value = false
  roleDialogVisible.value = false
  resetTenantForm()
  resetUserForm()
  resetRoleAssignForm()
  resetMemberForm()
  auditFilters.tenantId = ''
  auditFilters.userId = ''
  auditFilters.action = ''
  auditFilters.resourceType = ''
  auditFilters.dateRange = []
}

export function useAdmin() {
  return {
    tenants,
    users,
    roles,
    knowledgeBaseMembers,
    auditLogs,
    loadingTenants,
    loadingUsers,
    loadingRoles,
    loadingMembers,
    loadingAuditLogs,
    tenantDialogVisible,
    userDialogVisible,
    roleDialogVisible,
    creatingTenant,
    creatingUser,
    savingRoles,
    userTenantFilter,
    tenantForm,
    userForm,
    roleAssignForm,
    memberForm,
    auditFilters,
    canViewTenants,
    canViewUsers,
    canCreateUsers,
    canAssignRoles,
    canManageKnowledgeBaseMembers,
    canViewAudit,
    platformRoleOptions,
    tenantRoleOptions,
    kbRoleOptions,
    roleDialogUserLabel,
    refreshTenants,
    refreshUsers,
    refreshRoles,
    refreshKnowledgeBaseMembers,
    refreshAuditLogs,
    submitCreateTenant,
    toggleTenantStatus,
    submitCreateUser,
    openRoleDialog,
    submitAssignRoles,
    toggleUserStatus,
    submitKnowledgeBaseMember,
    removeKnowledgeBaseMember,
    resetTenantForm,
    resetUserForm,
    resetRoleAssignForm,
    resetMemberForm,
    clearAdminState
  }
}
