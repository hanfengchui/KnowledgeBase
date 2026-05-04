<template>
  <main class="app-page">
    <section v-if="!authReady || loadingSession" class="auth-shell">
      <div class="auth-panel auth-panel--loading">
        <span class="panel-kicker">KnowledgeHub AI</span>
        <h1>正在校验登录态</h1>
        <p>加载当前租户、知识库授权和后台权限配置。</p>
      </div>
    </section>

    <section v-else-if="!session" class="auth-shell">
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

    <section v-else class="workspace-shell">
      <aside class="sidebar">
        <div class="brand-block">
          <span class="brand-mark">KH</span>
          <div>
            <span class="panel-kicker">KnowledgeHub AI</span>
            <h1>ToB 工作台</h1>
            <p>{{ session.tenantName || '未选择租户' }}</p>
          </div>
        </div>

        <div class="sidebar-panel">
          <span class="panel-kicker">当前账号</span>
          <h2 class="panel-title">{{ session.displayName }}</h2>
          <p class="panel-description">{{ session.username }} · {{ session.platformAdmin ? '平台管理员' : '租户账号' }}</p>
          <div class="tag-row">
            <el-tag v-for="code in session.roleCodes" :key="code" size="small" effect="plain">{{ code }}</el-tag>
          </div>
        </div>

        <nav class="nav-list">
          <button
            v-for="item in visibleMenus"
            :key="item.key"
            type="button"
            :class="['nav-item', { active: activeTab === item.key }]"
            @click="activeTab = item.key"
          >
            <span class="nav-badge">{{ item.badge }}</span>
            <span class="nav-body">
              <strong>{{ item.label }}</strong>
              <small>{{ item.description }}</small>
            </span>
          </button>
        </nav>

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
            <el-tag v-for="code in currentKnowledgeBaseAccess.roleCodes" :key="code" size="small" type="success" effect="plain">
              {{ code }}
            </el-tag>
          </div>
        </div>
      </aside>

      <section class="stage">
        <header class="topbar">
          <div>
            <span class="panel-kicker">{{ currentSection.kicker }}</span>
            <h2>{{ currentSection.title }}</h2>
            <p>{{ currentSection.description }}</p>
          </div>

          <div class="topbar-actions">
            <div v-if="isPlatformAdmin" class="field-group">
              <span class="field-label">当前租户</span>
              <el-select v-model="selectedTenantId" class="field-select" @change="handleTenantSwitch">
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
                v-model="selectedKnowledgeBaseId"
                class="field-select"
                placeholder="请选择知识库"
                :loading="loadingKnowledgeBases"
                @change="handleKnowledgeBaseChange"
              >
                <el-option
                  v-for="kb in knowledgeBases"
                  :key="kb.id"
                  :label="`${kb.name}${kb.isDefault ? ' · 默认' : ''}`"
                  :value="kb.id"
                />
              </el-select>
            </div>

            <el-button @click="handleLogout">退出登录</el-button>
          </div>
        </header>

        <section class="overview-grid">
          <article class="overview-card">
            <span>当前租户</span>
            <strong>{{ session.tenantName || '-' }}</strong>
            <p>{{ session.tenantCode || '-' }}</p>
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

        <section v-if="activeTab === 'workspace'" class="page-grid">
          <article class="card card--span-2">
            <div class="card-head">
              <div>
                <h3>知识问答</h3>
                <p>问题仅在当前知识库范围内召回。若无工具权限，订单类问题仍可提交但不会触发业务查询。</p>
              </div>
              <div class="inline-actions">
                <span class="field-label">Top K</span>
                <el-input-number v-model="topK" :min="1" :max="10" size="small" />
              </div>
            </div>

            <div class="question-chips">
              <el-button v-for="item in questionExamples" :key="item" class="chip" @click="question = item">
                {{ item }}
              </el-button>
            </div>

            <el-input
              v-model="question"
              type="textarea"
              :rows="5"
              placeholder="例如：企业标准退款规则是什么？或 查询 ORD-2026-0001 当前状态"
            />

            <div class="action-row">
              <span class="muted">
                当前权限：
                {{ canAskCurrentKnowledgeBase ? '可问答' : '不可问答' }}
                / {{ canUseToolInCurrentKnowledgeBase ? '可调工具' : '不可调工具' }}
              </span>
              <el-button type="primary" :loading="asking" :disabled="!canAskCurrentKnowledgeBase" @click="handleAsk">
                发送问题
              </el-button>
            </div>
          </article>

          <article class="card">
            <div class="card-head">
              <div>
                <h3>运行状态</h3>
                <p>当前回答的检索和模型信息。</p>
              </div>
            </div>
            <div class="metric-grid metric-grid--light">
              <div>
                <span>耗时</span>
                <strong>{{ stats.latencyMs ?? '-' }} ms</strong>
              </div>
              <div>
                <span>召回数</span>
                <strong>{{ stats.retrievedCount ?? '-' }}</strong>
              </div>
              <div>
                <span>Chat</span>
                <strong>{{ stats.chatModel || '-' }}</strong>
              </div>
              <div>
                <span>Embedding</span>
                <strong>{{ stats.embeddingModel || '-' }}</strong>
              </div>
            </div>
            <div class="tag-row">
              <el-tag :type="stats.usedRag ? 'success' : 'info'">知识召回：{{ stats.usedRag ? '已命中' : '未命中' }}</el-tag>
              <el-tag :type="stats.usedTools ? 'warning' : 'info'">业务工具：{{ stats.usedTools ? '已触发' : '未触发' }}</el-tag>
            </div>
          </article>

          <article class="card card--span-2">
            <div class="card-head">
              <div>
                <h3>回答结果</h3>
                <p>{{ stats.knowledgeBaseName || currentKnowledgeBase?.name || '未选择知识库' }}</p>
              </div>
            </div>
            <div class="answer-box">{{ answer || '暂无回答。' }}</div>
          </article>

          <article class="card">
            <div class="card-head">
              <div>
                <h3>最近问答</h3>
                <p>保留 8 条最近问题，可快速复用。</p>
              </div>
              <el-button text @click="clearQuestionHistory">清空</el-button>
            </div>
            <el-empty v-if="questionHistory.length === 0" description="暂无历史提问" />
            <div v-else class="list-stack">
              <button
                v-for="item in questionHistory"
                :key="item.id"
                type="button"
                class="history-item"
                @click="reuseHistoryQuestion(item.question)"
              >
                <strong>{{ item.question }}</strong>
                <p>{{ item.answer }}</p>
                <span>{{ formatDate(item.createdAt) }} · 来源 {{ item.sourceCount }} · 工具 {{ item.usedTools ? '已触发' : '未触发' }}</span>
              </button>
            </div>
          </article>

          <article class="card">
            <div class="card-head">
              <div>
                <h3>工具调用记录</h3>
                <p>只有当前知识库具备工具权限时才会展示。</p>
              </div>
            </div>
            <el-empty v-if="toolCalls.length === 0" description="暂无工具调用记录" />
            <div v-else class="list-stack">
              <div v-for="(tool, index) in toolCalls" :key="`${tool.name}-${index}`" class="tool-item">
                <strong>{{ tool.name }}</strong>
                <span>{{ tool.arguments }}</span>
                <pre>{{ tool.result }}</pre>
              </div>
            </div>
          </article>

          <article class="card card--span-3">
            <div class="card-head">
              <div>
                <h3>来源片段</h3>
                <p>问答依据仅来自当前知识库内的召回片段。</p>
              </div>
            </div>
            <el-empty v-if="sources.length === 0" description="暂无来源片段" />
            <el-collapse v-else>
              <el-collapse-item v-for="(source, index) in sources" :key="index" :name="index">
                <template #title>
                  来源 {{ index + 1 }} · {{ source.documentName }} · chunk {{ source.chunkIndex }}
                </template>
                <div class="source-meta">
                  <span>{{ source.knowledgeBaseName }}</span>
                  <span>score {{ formatScore(source.score) }}</span>
                </div>
                <pre class="source-content">{{ source.content }}</pre>
              </el-collapse-item>
            </el-collapse>
          </article>
        </section>

        <section v-else-if="activeTab === 'documents'" class="page-stack">
          <article class="card">
            <div class="card-head">
              <div>
                <h3>文档入库</h3>
                <p>支持 `.txt / .md / .pdf / .docx`。上传和重建索引仍以当前知识库为边界。</p>
              </div>
              <div class="inline-actions">
                <el-button :loading="loadingDocuments" @click="refreshDocuments">刷新列表</el-button>
                <el-button :loading="reindexing" :disabled="!canReindexCurrentKnowledgeBase" @click="handleReindex">
                  重建索引
                </el-button>
              </div>
            </div>

            <el-upload
              drag
              accept=".txt,.md,.pdf,.docx"
              :show-file-list="false"
              :http-request="handleUpload"
              :disabled="!canUploadCurrentKnowledgeBase"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽文件到这里，或 <em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">
                  {{ canUploadCurrentKnowledgeBase ? '上传后会自动解析、切分、写入向量或关键词检索索引。' : '当前账号在该知识库上没有上传权限。' }}
                </div>
              </template>
            </el-upload>
          </article>

          <article class="card">
            <div class="card-head">
              <div>
                <h3>文档筛选</h3>
                <p>按文件名、类型、状态、错误信息或哈希过滤当前知识库文档。</p>
              </div>
              <el-button text @click="resetDocumentFilters">清空筛选</el-button>
            </div>
            <div class="filter-grid">
              <el-input v-model="documentSearch" placeholder="搜索文件名、错误信息或哈希" clearable />
              <el-select v-model="documentStatusFilter" placeholder="全部状态" clearable>
                <el-option v-for="item in documentStatusOptions" :key="item" :label="statusLabel(item)" :value="item" />
              </el-select>
            </div>
          </article>

          <article class="card">
            <div class="card-head">
              <div>
                <h3>当前知识库文档</h3>
                <p>{{ currentKnowledgeBase?.name || '未选择知识库' }} · 显示 {{ filteredDocuments.length }} / {{ documents.length }}</p>
              </div>
            </div>
            <el-table :data="filteredDocuments" stripe>
              <el-table-column prop="fileName" label="文件名" min-width="220" />
              <el-table-column prop="documentType" label="类型" width="120" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="chunkCount" label="Chunk 数" width="100" />
              <el-table-column prop="charCount" label="字符数" width="110" />
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="错误信息" min-width="220">
                <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
              </el-table-column>
            </el-table>
          </article>
        </section>

        <section v-else-if="activeTab === 'knowledge-bases'" class="page-stack">
          <article class="card">
            <div class="card-head">
              <div>
                <h3>知识库管理</h3>
                <p>知识库按当前租户隔离，列表已自动过滤用户无权访问的空间。</p>
              </div>
              <el-button type="primary" :disabled="!canCreateKnowledgeBase" @click="kbDialogVisible = true">
                新建知识库
              </el-button>
            </div>
          </article>

          <article class="card">
            <el-table :data="knowledgeBases" stripe>
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="code" label="代码" width="180" />
              <el-table-column prop="documentCount" label="文档数" width="100" />
              <el-table-column label="属性" width="160">
                <template #default="{ row }">
                  <el-tag v-if="row.isDefault" size="small" type="success">默认</el-tag>
                  <el-tag v-if="row.id === selectedKnowledgeBaseId" size="small" type="warning">当前</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column prop="description" label="说明" min-width="260" />
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button text type="primary" @click="switchKnowledgeBase(row.id)">切换</el-button>
                  <el-button
                    text
                    type="primary"
                    :disabled="!canUpdateKnowledgeBase(row.id)"
                    @click="UpdateKnowledgeBaseRequest(row.id)"
                  >
                    编辑
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </section>

        <section v-else-if="activeTab === 'tenants'" class="page-stack">
          <article class="card">
            <div class="card-head">
              <div>
                <h3>租户管理</h3>
                <p>仅平台管理员可创建、启停和切换租户。</p>
              </div>
              <el-button type="primary" @click="tenantDialogVisible = true">新建租户</el-button>
            </div>
          </article>

          <article class="card">
            <el-table :data="tenants" stripe>
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="code" label="代码" width="180" />
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="userCount" label="用户数" width="100" />
              <el-table-column prop="knowledgeBaseCount" label="知识库数" width="110" />
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button text @click="toggleTenantStatus(row)">
                    {{ row.status === 'active' ? '禁用' : '启用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </section>

        <section v-else-if="activeTab === 'users'" class="page-stack">
          <article class="card">
            <div class="card-head">
              <div>
                <h3>用户管理</h3>
                <p>管理员创建用户、启停账号，并按租户分配固定角色。</p>
              </div>
              <div class="inline-actions">
                <el-select
                  v-if="isPlatformAdmin"
                  v-model="userTenantFilter"
                  class="field-select"
                  placeholder="选择租户"
                  @change="refreshUsers"
                >
                  <el-option
                    v-for="tenant in availableTenants"
                    :key="tenant.id"
                    :label="`${tenant.name} (${tenant.code})`"
                    :value="tenant.id"
                  />
                </el-select>
                <el-button type="primary" :disabled="!canCreateUsers" @click="userDialogVisible = true">创建用户</el-button>
              </div>
            </div>
          </article>

          <article class="card">
            <el-table :data="users" stripe>
              <el-table-column prop="username" label="用户名" width="160" />
              <el-table-column prop="displayName" label="显示名" width="160" />
              <el-table-column prop="email" label="邮箱" min-width="220" />
              <el-table-column prop="status" label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="租户角色" min-width="220">
                <template #default="{ row }">
                  <div class="tag-row">
                    <el-tag v-for="code in row.tenantRoleCodes" :key="code" size="small" effect="plain">{{ code }}</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="平台角色" min-width="160">
                <template #default="{ row }">
                  <div class="tag-row">
                    <el-tag v-for="code in row.platformRoleCodes" :key="code" size="small" type="warning" effect="plain">
                      {{ code }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="220">
                <template #default="{ row }">
                  <el-button text @click="openRoleDialog(row)" :disabled="!canAssignRoles">分配角色</el-button>
                  <el-button text @click="toggleUserStatus(row)">
                    {{ row.status === 'active' ? '停用' : '启用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </section>

        <section v-else-if="activeTab === 'roles'" class="page-stack">
          <article class="card">
            <div class="card-head">
              <div>
                <h3>角色与权限</h3>
                <p>本期采用固定角色模型，不支持动态自定义角色。</p>
              </div>
            </div>
            <el-table :data="roles" stripe>
              <el-table-column prop="code" label="角色代码" width="180" />
              <el-table-column prop="name" label="角色名称" width="160" />
              <el-table-column prop="scopeType" label="作用域" width="140" />
              <el-table-column prop="description" label="说明" min-width="220" />
              <el-table-column label="权限点" min-width="280">
                <template #default="{ row }">
                  <div class="tag-row">
                    <el-tag v-for="code in row.permissionCodes" :key="code" size="small" effect="plain">{{ code }}</el-tag>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </article>

          <article class="card">
            <div class="card-head">
              <div>
                <h3>知识库授权</h3>
                <p>仅 `tenant_admin`、`platform_admin` 或当前知识库 `kb_admin` 可修改成员。</p>
              </div>
              <el-button :loading="loadingMembers" @click="refreshKnowledgeBaseMembers">刷新成员</el-button>
            </div>

            <div class="filter-grid">
              <el-select v-model="memberForm.userId" placeholder="选择用户">
                <el-option
                  v-for="user in users"
                  :key="user.id"
                  :label="`${user.displayName} (${user.username})`"
                  :value="user.id"
                />
              </el-select>
              <el-select v-model="memberForm.roleCodes" multiple collapse-tags placeholder="选择知识库角色">
                <el-option v-for="item in kbRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <el-button type="primary" :disabled="!canManageKnowledgeBaseMembers" @click="handleSaveKnowledgeBaseMember">
                保存授权
              </el-button>
            </div>

            <el-table :data="knowledgeBaseMembers" stripe>
              <el-table-column prop="displayName" label="用户" min-width="180" />
              <el-table-column prop="username" label="用户名" width="160" />
              <el-table-column label="角色" min-width="220">
                <template #default="{ row }">
                  <div class="tag-row">
                    <el-tag v-for="code in row.roleCodes" :key="code" size="small" type="success" effect="plain">{{ code }}</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="授权时间" width="180">
                <template #default="{ row }">{{ formatDate(row.grantedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button text type="danger" :disabled="!canManageKnowledgeBaseMembers" @click="handleDeleteKnowledgeBaseMember(row)">
                    移除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </article>
        </section>

        <section v-else-if="activeTab === 'audit'" class="page-stack">
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
      </section>
    </section>

    <el-dialog v-model="kbDialogVisible" title="新建知识库" width="520px">
      <el-form label-position="top">
        <el-form-item label="知识库名称">
          <el-input v-model="kbForm.name" placeholder="例如：售后服务知识库" />
        </el-form-item>
        <el-form-item label="知识库代码">
          <el-input v-model="kbForm.code" placeholder="可选，例如：after-sales" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="kbForm.description" type="textarea" :rows="4" placeholder="简要说明覆盖范围" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingKnowledgeBase" @click="handleCreateKnowledgeBase">
          创建并切换
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="kbEditDialogVisible" title="编辑知识库" width="520px" @closed="resetKnowledgeBaseEditForm">
      <el-form label-position="top">
        <el-form-item label="知识库名称">
          <el-input v-model="kbEditForm.name" placeholder="例如：售后服务知识库" />
        </el-form-item>
        <el-form-item label="知识库代码">
          <el-input v-model="kbEditForm.code" placeholder="可选，例如：after-sales" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="kbEditForm.description" type="textarea" :rows="4" placeholder="简要说明覆盖范围" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeKnowledgeBaseEditDialog">取消</el-button>
        <el-button type="primary" :loading="updatingKnowledgeBase" @click="handleUpdateKnowledgeBase">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tenantDialogVisible" title="新建租户" width="480px">
      <el-form label-position="top">
        <el-form-item label="租户名称">
          <el-input v-model="tenantForm.name" placeholder="例如：华东区演示租户" />
        </el-form-item>
        <el-form-item label="租户代码">
          <el-input v-model="tenantForm.code" placeholder="例如：east-demo" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tenantDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingTenant" @click="handleCreateTenant">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialogVisible" title="创建用户" width="540px">
      <el-form label-position="top">
        <el-form-item v-if="isPlatformAdmin" label="所属租户">
          <el-select v-model="userForm.tenantId" class="dialog-select" placeholder="请选择租户">
            <el-option v-for="tenant in availableTenants" :key="tenant.id" :label="tenant.name" :value="tenant.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="userForm.displayName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="租户角色">
          <el-select v-model="userForm.tenantRoleCodes" multiple collapse-tags class="dialog-select">
            <el-option v-for="item in tenantRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingUser" @click="handleCreateUser">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="560px">
      <el-form label-position="top">
        <el-form-item label="用户">
          <el-input :model-value="roleDialogUserLabel" disabled />
        </el-form-item>
        <el-form-item v-if="isPlatformAdmin" label="平台角色">
          <el-select v-model="roleAssignForm.platformRoleCodes" multiple collapse-tags class="dialog-select">
            <el-option v-for="item in platformRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户角色">
          <el-select v-model="roleAssignForm.tenantRoleCodes" multiple collapse-tags class="dialog-select">
            <el-option v-for="item in tenantRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="handleAssignRoles">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  askQuestion,
  assignUserRoles,
  clearAccessToken,
  createKnowledgeBase,
  createTenant,
  createUser,
  deleteKnowledgeBaseMember,
  getAccessToken,
  getMe,
  listAuditLogs,
  listDocuments,
  listKnowledgeBaseMembers,
  listKnowledgeBases,
  listRoles,
  listTenants,
  listUsers,
  login,
  logout,
  reindexDocuments,
  saveKnowledgeBaseMember,
  setAccessToken,
  switchTenant,
  updateKnowledgeBase,
  updateTenant,
  updateUser,
  uploadDocument
} from './api'

const authReady = ref(false)
const loadingSession = ref(false)
const loginSubmitting = ref(false)
const session = ref(null)

const loginForm = reactive({
  username: 'platform-admin',
  password: 'ChangeMe123!',
  tenantCode: ''
})

const activeTab = ref('workspace')
const selectedTenantId = ref('')
const selectedKnowledgeBaseId = ref('')

const question = ref('')
const topK = ref(5)
const asking = ref(false)
const answer = ref('')
const sources = ref([])
const toolCalls = ref([])
const stats = reactive({
  latencyMs: null,
  retrievedCount: null,
  chatModel: '',
  embeddingModel: '',
  usedRag: false,
  usedTools: false,
  knowledgeBaseName: ''
})

const knowledgeBases = ref([])
const documents = ref([])
const questionHistory = ref([])
const tenants = ref([])
const users = ref([])
const roles = ref([])
const knowledgeBaseMembers = ref([])
const auditLogs = ref([])

const loadingKnowledgeBases = ref(false)
const loadingDocuments = ref(false)
const reindexing = ref(false)
const loadingTenants = ref(false)
const loadingUsers = ref(false)
const loadingRoles = ref(false)
const loadingMembers = ref(false)
const loadingAuditLogs = ref(false)

const kbDialogVisible = ref(false)
const kbEditDialogVisible = ref(false)
const tenantDialogVisible = ref(false)
const userDialogVisible = ref(false)
const roleDialogVisible = ref(false)

const creatingKnowledgeBase = ref(false)
const updatingKnowledgeBase = ref(false)
const creatingTenant = ref(false)
const creatingUser = ref(false)
const savingRoles = ref(false)

const documentSearch = ref('')
const documentStatusFilter = ref('')
const userTenantFilter = ref('')

const kbForm = reactive({
  name: '',
  code: '',
  description: ''
})

const editingKnowledgeBaseId = ref('')
const kbEditForm = reactive({
  name: '',
  code: '',
  description: ''
})

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

const questionExamples = [
  '企业标准退款规则是什么？',
  '接入向量检索前需要满足哪些数据库条件？',
  '客户要求接入 Oracle 和 Elasticsearch 时需要走什么流程？',
  '查询 ORD-2026-0001 当前状态'
]

const documentStatusOptions = ['pending', 'indexing', 'indexed', 'failed']

const isPlatformAdmin = computed(() => Boolean(session.value?.platformAdmin))
const availableTenants = computed(() => session.value?.availableTenants || [])
const tenantRoleCodes = computed(() => session.value?.roleCodes || [])
const tenantPermissionCodes = computed(() => new Set(session.value?.permissionCodes || []))
const currentKnowledgeBase = computed(() =>
  knowledgeBases.value.find((item) => item.id === selectedKnowledgeBaseId.value) || null
)
const knowledgeBaseAccessMap = computed(() => {
  const map = new Map()
  for (const item of session.value?.knowledgeBaseAccesses || []) {
    map.set(item.knowledgeBaseId, {
      roleCodes: item.roleCodes || [],
      permissionCodes: new Set(item.permissionCodes || [])
    })
  }
  return map
})
const currentKnowledgeBaseAccess = computed(() => knowledgeBaseAccessMap.value.get(selectedKnowledgeBaseId.value) || {
  roleCodes: [],
  permissionCodes: new Set()
})

const platformRoleOptions = computed(() => roles.value.filter((item) => item.scopeType === 'platform'))
const tenantRoleOptions = computed(() => roles.value.filter((item) => item.scopeType === 'tenant'))
const kbRoleOptions = computed(() => roles.value.filter((item) => item.scopeType === 'knowledge_base'))

const filteredDocuments = computed(() => {
  const keyword = documentSearch.value.trim().toLowerCase()
  return documents.value.filter((item) => {
    if (documentStatusFilter.value && item.status !== documentStatusFilter.value) {
      return false
    }
    if (!keyword) {
      return true
    }
    return [item.fileName, item.documentType, item.errorMessage, item.contentHash]
      .some((field) => String(field || '').toLowerCase().includes(keyword))
  })
})

const indexedDocumentCount = computed(() => documents.value.filter((item) => item.status === 'indexed').length)
const failedDocumentCount = computed(() => documents.value.filter((item) => item.status === 'failed').length)
const latestQuestionLabel = computed(() => questionHistory.value[0] ? formatDate(questionHistory.value[0].createdAt) : '暂无历史')

const canViewTenants = computed(() => isPlatformAdmin.value)
const canViewUsers = computed(() => hasTenantPermission('user.read'))
const canCreateUsers = computed(() => hasTenantPermission('user.create'))
const canAssignRoles = computed(() => isPlatformAdmin.value || hasTenantPermission('role.assign'))
const canCreateKnowledgeBase = computed(() => hasTenantPermission('kb.create'))
const canAskCurrentKnowledgeBase = computed(() => hasCurrentKnowledgeBasePermission('chat.ask'))
const canUseToolInCurrentKnowledgeBase = computed(() => hasCurrentKnowledgeBasePermission('tool.query_order'))
const canUploadCurrentKnowledgeBase = computed(() => isPlatformAdmin.value || tenantRoleCodes.value.includes('tenant_admin') || hasCurrentKnowledgeBasePermission('document.upload'))
const canReindexCurrentKnowledgeBase = computed(() => isPlatformAdmin.value || tenantRoleCodes.value.includes('tenant_admin') || hasCurrentKnowledgeBasePermission('document.reindex'))
const canManageKnowledgeBaseMembers = computed(() => isPlatformAdmin.value || tenantRoleCodes.value.includes('tenant_admin') || hasCurrentKnowledgeBasePermission('kb.authorize'))
const canViewAudit = computed(() => hasTenantPermission('audit.read'))

const visibleMenus = computed(() => {
  const items = [
    { key: 'workspace', label: '工作台', badge: 'QA', description: '知识问答、来源依据和工具记录', visible: knowledgeBases.value.length > 0 },
    { key: 'documents', label: '文档中心', badge: 'DOC', description: '上传、状态跟踪和索引重建', visible: knowledgeBases.value.length > 0 },
    { key: 'knowledge-bases', label: '知识库管理', badge: 'KB', description: '按租户查看与创建知识空间', visible: knowledgeBases.value.length > 0 || canCreateKnowledgeBase.value },
    { key: 'tenants', label: '租户管理', badge: 'TEN', description: '平台级租户开通、启停与切换', visible: canViewTenants.value },
    { key: 'users', label: '用户管理', badge: 'USR', description: '创建用户、启停账号和分配角色', visible: canViewUsers.value },
    { key: 'roles', label: '角色授权', badge: 'IAM', description: '固定角色矩阵和知识库成员授权', visible: canAssignRoles.value || canManageKnowledgeBaseMembers.value },
    { key: 'audit', label: '审计日志', badge: 'LOG', description: '筛选关键操作与权限变更记录', visible: canViewAudit.value }
  ]
  return items.filter((item) => item.visible)
})

const currentSection = computed(() => {
  const item = visibleMenus.value.find((entry) => entry.key === activeTab.value)
  if (!item) {
    return {
      kicker: 'KnowledgeHub AI',
      title: '企业知识库与业务助手平台',
      description: '基于租户和知识库权限的统一工作台。'
    }
  }
  return {
    kicker: 'KnowledgeHub AI',
    title: item.label,
    description: item.description
  }
})

const roleDialogUserLabel = computed(() => {
  const user = users.value.find((item) => item.id === roleAssignForm.userId)
  return user ? `${user.displayName} (${user.username})` : ''
})

onMounted(() => {
  initializeApp()
})

async function initializeApp() {
  const token = getAccessToken()
  if (!token) {
    authReady.value = true
    return
  }
  await hydrateSession()
}

async function hydrateSession() {
  loadingSession.value = true
  try {
    session.value = await getMe()
    selectedTenantId.value = session.value.tenantId || ''
    await refreshAllData()
  } catch (error) {
    clearAccessToken()
    session.value = null
    resetState()
    ElMessage.error(extractError(error))
  } finally {
    authReady.value = true
    loadingSession.value = false
  }
}

async function refreshAllData() {
  await refreshKnowledgeBases()
  loadQuestionHistory()
  await refreshDocuments()

  if (canViewTenants.value) {
    await refreshTenants()
  }
  if (canViewUsers.value || canManageKnowledgeBaseMembers.value || canViewAudit.value) {
    userTenantFilter.value = userTenantFilter.value || selectedTenantId.value
    await refreshUsers()
  }
  if (canAssignRoles.value || canManageKnowledgeBaseMembers.value) {
    await refreshRoles()
  }
  if (canManageKnowledgeBaseMembers.value) {
    await refreshKnowledgeBaseMembers()
  }
  if (canViewAudit.value) {
    await refreshAuditLogs()
  }
  ensureActiveTab()
}

function resetState() {
  answer.value = ''
  sources.value = []
  toolCalls.value = []
  knowledgeBases.value = []
  documents.value = []
  tenants.value = []
  users.value = []
  roles.value = []
  knowledgeBaseMembers.value = []
  auditLogs.value = []
  questionHistory.value = []
  selectedKnowledgeBaseId.value = ''
  selectedTenantId.value = ''
  activeTab.value = 'workspace'
  resetStats()
}

function resetStats() {
  Object.assign(stats, {
    latencyMs: null,
    retrievedCount: null,
    chatModel: '',
    embeddingModel: '',
    usedRag: false,
    usedTools: false,
    knowledgeBaseName: ''
  })
}

function ensureActiveTab() {
  if (!visibleMenus.value.some((item) => item.key === activeTab.value)) {
    activeTab.value = visibleMenus.value[0]?.key || 'workspace'
  }
}

function hasTenantPermission(code) {
  return isPlatformAdmin.value || tenantPermissionCodes.value.has(code)
}

function hasCurrentKnowledgeBasePermission(code) {
  return isPlatformAdmin.value || currentKnowledgeBaseAccess.value.permissionCodes.has(code)
}

function canUpdateKnowledgeBase(knowledgeBaseId) {
  if (isPlatformAdmin.value || hasTenantPermission('kb.update')) {
    return true
  }
  return Boolean(knowledgeBaseAccessMap.value.get(knowledgeBaseId)?.permissionCodes.has('kb.update'))
}

function questionHistoryStorageKey() {
  return `knowledgehub-history:${session.value?.userId || 'guest'}:${session.value?.tenantId || 'none'}:${selectedKnowledgeBaseId.value || 'none'}`
}

function loadQuestionHistory() {
  if (!selectedKnowledgeBaseId.value) {
    questionHistory.value = []
    return
  }
  const raw = window.localStorage.getItem(questionHistoryStorageKey())
  questionHistory.value = raw ? JSON.parse(raw) : []
}

function persistQuestionHistory() {
  window.localStorage.setItem(questionHistoryStorageKey(), JSON.stringify(questionHistory.value))
}

function recordQuestionHistory(entry) {
  questionHistory.value = [entry, ...questionHistory.value.filter((item) => item.question !== entry.question)].slice(0, 8)
  persistQuestionHistory()
}

function clearQuestionHistory() {
  questionHistory.value = []
  persistQuestionHistory()
}

function reuseHistoryQuestion(nextQuestion) {
  question.value = nextQuestion
  activeTab.value = 'workspace'
}

async function handleLogin() {
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loginSubmitting.value = true
  try {
    const data = await login({
      username: loginForm.username,
      password: loginForm.password,
      tenantCode: loginForm.tenantCode || null
    })
    setAccessToken(data.accessToken)
    await hydrateSession()
    ElMessage.success('登录成功')
  } catch (error) {
    clearAccessToken()
    ElMessage.error(extractError(error))
  } finally {
    loginSubmitting.value = false
  }
}

async function handleLogout() {
  try {
    await logout()
  } catch (error) {
    ElMessage.warning(extractError(error))
  } finally {
    clearAccessToken()
    session.value = null
    resetState()
    authReady.value = true
  }
}

async function handleTenantSwitch(tenantId) {
  if (!tenantId || tenantId === session.value?.tenantId) {
    return
  }
  try {
    const data = await switchTenant(tenantId)
    setAccessToken(data.accessToken)
    await hydrateSession()
    ElMessage.success('已切换租户')
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function refreshKnowledgeBases() {
  loadingKnowledgeBases.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
    if (!knowledgeBases.value.some((item) => item.id === selectedKnowledgeBaseId.value)) {
      selectedKnowledgeBaseId.value = knowledgeBases.value[0]?.id || ''
    }
    stats.knowledgeBaseName = currentKnowledgeBase.value?.name || ''
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    loadingKnowledgeBases.value = false
  }
}

async function refreshDocuments() {
  if (!selectedKnowledgeBaseId.value) {
    documents.value = []
    return
  }
  loadingDocuments.value = true
  try {
    documents.value = await listDocuments(selectedKnowledgeBaseId.value)
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    loadingDocuments.value = false
  }
}

async function refreshTenants() {
  if (!canViewTenants.value) {
    return
  }
  loadingTenants.value = true
  try {
    tenants.value = await listTenants()
  } catch (error) {
    ElMessage.error(extractError(error))
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
  } catch (error) {
    ElMessage.error(extractError(error))
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
  } catch (error) {
    ElMessage.error(extractError(error))
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
  } catch (error) {
    ElMessage.error(extractError(error))
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
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    loadingAuditLogs.value = false
  }
}

async function handleKnowledgeBaseChange() {
  answer.value = ''
  sources.value = []
  toolCalls.value = []
  resetStats()
  loadQuestionHistory()
  await refreshDocuments()
  await refreshKnowledgeBaseMembers()
}

async function switchKnowledgeBase(knowledgeBaseId) {
  selectedKnowledgeBaseId.value = knowledgeBaseId
  await handleKnowledgeBaseChange()
}

async function UpdateKnowledgeBaseRequest(knowledgeBaseId) {
  if (!canUpdateKnowledgeBase(knowledgeBaseId)) {
    ElMessage.warning('当前账号没有编辑该知识库的权限')
    return
  }

  selectedKnowledgeBaseId.value = knowledgeBaseId
  await handleKnowledgeBaseChange()

  const target = knowledgeBases.value.find((item) => item.id === knowledgeBaseId)
  if (!target) {
    ElMessage.warning('未找到指定知识库')
    return
  }

  editingKnowledgeBaseId.value = knowledgeBaseId
  Object.assign(kbEditForm, {
    name: target.name || '',
    code: target.code || '',
    description: target.description || ''
  })
  kbEditDialogVisible.value = true
}

async function handleAsk() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }

  asking.value = true
  try {
    const data = await askQuestion({
      question: question.value,
      topK: topK.value,
      knowledgeBaseId: selectedKnowledgeBaseId.value
    })
    answer.value = data.answer
    sources.value = data.sources || []
    toolCalls.value = canUseToolInCurrentKnowledgeBase.value ? (data.toolCalls || []) : []
    Object.assign(stats, data.stats || {})
    recordQuestionHistory({
      id: crypto.randomUUID(),
      question: question.value,
      answer: data.answer,
      sourceCount: (data.sources || []).length,
      usedTools: (data.toolCalls || []).length > 0,
      createdAt: new Date().toISOString()
    })
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    asking.value = false
  }
}

async function handleUpload(options) {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    options.onError?.(new Error('knowledge base required'))
    return
  }
  try {
    const data = await uploadDocument(options.file, selectedKnowledgeBaseId.value)
    ElMessage.success(`${data.fileName} 已入库`)
    await refreshDocuments()
    await refreshKnowledgeBases()
    options.onSuccess?.(data)
  } catch (error) {
    ElMessage.error(extractError(error))
    options.onError?.(error)
  }
}

async function handleReindex() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  reindexing.value = true
  try {
    const data = await reindexDocuments(selectedKnowledgeBaseId.value)
    ElMessage.success(data.message || `已重建索引 ${data.chunkCount} 个 chunk`)
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    reindexing.value = false
  }
}

async function handleCreateKnowledgeBase() {
  if (!kbForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  creatingKnowledgeBase.value = true
  try {
    const data = await createKnowledgeBase({
      name: kbForm.name,
      code: kbForm.code,
      description: kbForm.description
    })
    kbDialogVisible.value = false
    kbForm.name = ''
    kbForm.code = ''
    kbForm.description = ''
    await hydrateSession()
    selectedKnowledgeBaseId.value = data.id
    await handleKnowledgeBaseChange()
    ElMessage.success(`知识库已创建：${data.name}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    creatingKnowledgeBase.value = false
  }
}

function closeKnowledgeBaseEditDialog() {
  kbEditDialogVisible.value = false
}

function resetKnowledgeBaseEditForm() {
  editingKnowledgeBaseId.value = ''
  Object.assign(kbEditForm, {
    name: '',
    code: '',
    description: ''
  })
}

async function handleUpdateKnowledgeBase() {
  if (!editingKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!canUpdateKnowledgeBase(editingKnowledgeBaseId.value)) {
    ElMessage.warning('当前账号没有编辑该知识库的权限')
    return
  }
  if (!kbEditForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }

  updatingKnowledgeBase.value = true
  try {
    const submittedName = kbEditForm.name.trim()
    const data = await updateKnowledgeBase(editingKnowledgeBaseId.value, {
      name: submittedName,
      code: kbEditForm.code,
      description: kbEditForm.description
    })
    const updatedKnowledgeBaseId = data?.id || editingKnowledgeBaseId.value
    closeKnowledgeBaseEditDialog()
    selectedKnowledgeBaseId.value = updatedKnowledgeBaseId
    await refreshKnowledgeBases()
    await handleKnowledgeBaseChange()
    if (canViewAudit.value) {
      await refreshAuditLogs()
    }
    ElMessage.success(`知识库已更新：${data?.name || submittedName}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    updatingKnowledgeBase.value = false
  }
}

async function handleCreateTenant() {
  if (!tenantForm.name.trim()) {
    ElMessage.warning('请输入租户名称')
    return
  }
  creatingTenant.value = true
  try {
    await createTenant({
      name: tenantForm.name,
      code: tenantForm.code
    })
    tenantDialogVisible.value = false
    tenantForm.name = ''
    tenantForm.code = ''
    await refreshTenants()
    ElMessage.success('租户已创建')
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    creatingTenant.value = false
  }
}

async function toggleTenantStatus(row) {
  try {
    const nextStatus = row.status === 'active' ? 'disabled' : 'active'
    await updateTenant(row.id, {
      status: nextStatus
    })
    await refreshTenants()
    ElMessage.success(`租户状态已更新为 ${nextStatus}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleCreateUser() {
  if (!userForm.username.trim() || !userForm.displayName.trim() || !userForm.password.trim()) {
    ElMessage.warning('请完整填写用户名、显示名和初始密码')
    return
  }
  creatingUser.value = true
  try {
    await createUser({
      tenantId: isPlatformAdmin.value ? userForm.tenantId || selectedTenantId.value : selectedTenantId.value,
      username: userForm.username,
      displayName: userForm.displayName,
      email: userForm.email,
      password: userForm.password,
      tenantRoleCodes: userForm.tenantRoleCodes
    })
    userDialogVisible.value = false
    Object.assign(userForm, {
      tenantId: '',
      username: '',
      displayName: '',
      email: '',
      password: '',
      tenantRoleCodes: []
    })
    await refreshUsers()
    ElMessage.success('用户已创建')
  } catch (error) {
    ElMessage.error(extractError(error))
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

async function handleAssignRoles() {
  if (!roleAssignForm.userId) {
    ElMessage.warning('请先选择用户')
    return
  }
  savingRoles.value = true
  try {
    await assignUserRoles(roleAssignForm.userId, {
      tenantId: selectedTenantId.value,
      platformRoleCodes: roleAssignForm.platformRoleCodes,
      tenantRoleCodes: roleAssignForm.tenantRoleCodes
    })
    roleDialogVisible.value = false
    await refreshUsers()
    await hydrateSession()
    ElMessage.success('角色已更新')
  } catch (error) {
    ElMessage.error(extractError(error))
  } finally {
    savingRoles.value = false
  }
}

async function toggleUserStatus(row) {
  try {
    const nextStatus = row.status === 'active' ? 'disabled' : 'active'
    await updateUser(row.id, {
      status: nextStatus
    })
    await refreshUsers()
    ElMessage.success(`用户状态已更新为 ${nextStatus}`)
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleSaveKnowledgeBaseMember() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!memberForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  try {
    await saveKnowledgeBaseMember(selectedKnowledgeBaseId.value, {
      userId: memberForm.userId,
      roleCodes: memberForm.roleCodes
    })
    memberForm.userId = ''
    memberForm.roleCodes = []
    await refreshKnowledgeBaseMembers()
    await hydrateSession()
    ElMessage.success('知识库授权已保存')
  } catch (error) {
    ElMessage.error(extractError(error))
  }
}

async function handleDeleteKnowledgeBaseMember(row) {
  try {
    await ElMessageBox.confirm(`确认移除 ${row.displayName} 的知识库授权吗？`, '移除授权', {
      type: 'warning'
    })
    await deleteKnowledgeBaseMember(selectedKnowledgeBaseId.value, row.userId)
    await refreshKnowledgeBaseMembers()
    await hydrateSession()
    ElMessage.success('知识库授权已移除')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(extractError(error))
    }
  }
}

function resetDocumentFilters() {
  documentSearch.value = ''
  documentStatusFilter.value = ''
}

function statusLabel(status) {
  return {
    pending: '待处理',
    indexing: '处理中',
    indexed: '已入库',
    failed: '失败'
  }[status] || status
}

function statusTagType(status) {
  return {
    pending: 'info',
    indexing: 'warning',
    indexed: 'success',
    failed: 'danger'
  }[status] || 'info'
}

function formatScore(score) {
  return score == null ? 'n/a' : Number(score).toFixed(4)
}

function formatDate(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function extractError(error) {
  const response = error?.response?.data
  if (response?.details?.length) {
    return response.details.join('；')
  }
  return response?.error || error?.message || '请求失败'
}
</script>
