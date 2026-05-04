package com.example.knowledgeassistant.security;

import java.util.List;
import java.util.Set;

public final class AuthorizationCatalog {

    public static final String ROLE_PLATFORM_ADMIN = "platform_admin";
    public static final String ROLE_TENANT_ADMIN = "tenant_admin";
    public static final String ROLE_TENANT_OPERATOR = "tenant_operator";
    public static final String ROLE_TENANT_AUDITOR = "tenant_auditor";
    public static final String ROLE_KB_ADMIN = "kb_admin";
    public static final String ROLE_KB_EDITOR = "kb_editor";
    public static final String ROLE_KB_VIEWER = "kb_viewer";
    public static final String ROLE_KB_TOOL_OPERATOR = "kb_tool_operator";

    public static final String PERMISSION_TENANT_READ = "tenant.read";
    public static final String PERMISSION_TENANT_MANAGE_USERS = "tenant.manage_users";
    public static final String PERMISSION_USER_READ = "user.read";
    public static final String PERMISSION_USER_CREATE = "user.create";
    public static final String PERMISSION_USER_UPDATE = "user.update";
    public static final String PERMISSION_USER_DISABLE = "user.disable";
    public static final String PERMISSION_ROLE_ASSIGN = "role.assign";
    public static final String PERMISSION_KB_READ = "kb.read";
    public static final String PERMISSION_KB_CREATE = "kb.create";
    public static final String PERMISSION_KB_UPDATE = "kb.update";
    public static final String PERMISSION_KB_AUTHORIZE = "kb.authorize";
    public static final String PERMISSION_DOCUMENT_READ = "document.read";
    public static final String PERMISSION_DOCUMENT_UPLOAD = "document.upload";
    public static final String PERMISSION_DOCUMENT_REINDEX = "document.reindex";
    public static final String PERMISSION_CHAT_ASK = "chat.ask";
    public static final String PERMISSION_TOOL_QUERY_ORDER = "tool.query_order";
    public static final String PERMISSION_AUDIT_READ = "audit.read";

    public static final List<PermissionDefinition> PERMISSIONS = List.of(
            new PermissionDefinition(PERMISSION_TENANT_READ, "查看租户", "tenant", "read"),
            new PermissionDefinition(PERMISSION_TENANT_MANAGE_USERS, "管理租户用户", "tenant", "manage_users"),
            new PermissionDefinition(PERMISSION_USER_READ, "查看用户", "user", "read"),
            new PermissionDefinition(PERMISSION_USER_CREATE, "创建用户", "user", "create"),
            new PermissionDefinition(PERMISSION_USER_UPDATE, "更新用户", "user", "update"),
            new PermissionDefinition(PERMISSION_USER_DISABLE, "禁用用户", "user", "disable"),
            new PermissionDefinition(PERMISSION_ROLE_ASSIGN, "分配角色", "role", "assign"),
            new PermissionDefinition(PERMISSION_KB_READ, "查看知识库", "knowledge_base", "read"),
            new PermissionDefinition(PERMISSION_KB_CREATE, "创建知识库", "knowledge_base", "create"),
            new PermissionDefinition(PERMISSION_KB_UPDATE, "更新知识库", "knowledge_base", "update"),
            new PermissionDefinition(PERMISSION_KB_AUTHORIZE, "管理知识库授权", "knowledge_base", "authorize"),
            new PermissionDefinition(PERMISSION_DOCUMENT_READ, "查看文档", "document", "read"),
            new PermissionDefinition(PERMISSION_DOCUMENT_UPLOAD, "上传文档", "document", "upload"),
            new PermissionDefinition(PERMISSION_DOCUMENT_REINDEX, "重建索引", "document", "reindex"),
            new PermissionDefinition(PERMISSION_CHAT_ASK, "知识问答", "chat", "ask"),
            new PermissionDefinition(PERMISSION_TOOL_QUERY_ORDER, "查询订单", "tool", "query_order"),
            new PermissionDefinition(PERMISSION_AUDIT_READ, "查看审计日志", "audit", "read")
    );

    public static final List<RoleDefinition> ROLES = List.of(
            new RoleDefinition(
                    ROLE_PLATFORM_ADMIN,
                    "平台管理员",
                    "platform",
                    "可跨租户管理平台、租户、用户、授权与审计。",
                    PERMISSIONS.stream().map(PermissionDefinition::code).collect(java.util.stream.Collectors.toSet())
            ),
            new RoleDefinition(
                    ROLE_TENANT_ADMIN,
                    "租户管理员",
                    "tenant",
                    "管理本租户用户、知识库与授权。",
                    Set.of(
                            PERMISSION_TENANT_READ,
                            PERMISSION_TENANT_MANAGE_USERS,
                            PERMISSION_USER_READ,
                            PERMISSION_USER_CREATE,
                            PERMISSION_USER_UPDATE,
                            PERMISSION_USER_DISABLE,
                            PERMISSION_ROLE_ASSIGN,
                            PERMISSION_KB_READ,
                            PERMISSION_KB_CREATE,
                            PERMISSION_KB_UPDATE,
                            PERMISSION_KB_AUTHORIZE,
                            PERMISSION_DOCUMENT_READ,
                            PERMISSION_CHAT_ASK,
                            PERMISSION_AUDIT_READ
                    )
            ),
            new RoleDefinition(
                    ROLE_TENANT_OPERATOR,
                    "租户运营",
                    "tenant",
                    "运营侧维护租户知识库与基础资料。",
                    Set.of(
                            PERMISSION_TENANT_READ,
                            PERMISSION_USER_READ,
                            PERMISSION_KB_READ,
                            PERMISSION_KB_CREATE,
                            PERMISSION_KB_UPDATE,
                            PERMISSION_DOCUMENT_READ,
                            PERMISSION_CHAT_ASK
                    )
            ),
            new RoleDefinition(
                    ROLE_TENANT_AUDITOR,
                    "租户审计员",
                    "tenant",
                    "查看租户用户、知识库和审计记录。",
                    Set.of(
                            PERMISSION_TENANT_READ,
                            PERMISSION_USER_READ,
                            PERMISSION_KB_READ,
                            PERMISSION_DOCUMENT_READ,
                            PERMISSION_CHAT_ASK,
                            PERMISSION_AUDIT_READ
                    )
            ),
            new RoleDefinition(
                    ROLE_KB_ADMIN,
                    "知识库管理员",
                    "knowledge_base",
                    "管理指定知识库内容与成员。",
                    Set.of(
                            PERMISSION_KB_READ,
                            PERMISSION_KB_UPDATE,
                            PERMISSION_KB_AUTHORIZE,
                            PERMISSION_DOCUMENT_READ,
                            PERMISSION_DOCUMENT_UPLOAD,
                            PERMISSION_DOCUMENT_REINDEX,
                            PERMISSION_CHAT_ASK
                    )
            ),
            new RoleDefinition(
                    ROLE_KB_EDITOR,
                    "知识库编辑",
                    "knowledge_base",
                    "上传文档和重建索引。",
                    Set.of(
                            PERMISSION_KB_READ,
                            PERMISSION_DOCUMENT_READ,
                            PERMISSION_DOCUMENT_UPLOAD,
                            PERMISSION_DOCUMENT_REINDEX,
                            PERMISSION_CHAT_ASK
                    )
            ),
            new RoleDefinition(
                    ROLE_KB_VIEWER,
                    "知识库查看者",
                    "knowledge_base",
                    "仅可在知识库内检索和问答。",
                    Set.of(
                            PERMISSION_KB_READ,
                            PERMISSION_DOCUMENT_READ,
                            PERMISSION_CHAT_ASK
                    )
            ),
            new RoleDefinition(
                    ROLE_KB_TOOL_OPERATOR,
                    "知识库工具操作员",
                    "knowledge_base",
                    "可在指定知识库问答时调用订单查询工具。",
                    Set.of(
                            PERMISSION_KB_READ,
                            PERMISSION_DOCUMENT_READ,
                            PERMISSION_CHAT_ASK,
                            PERMISSION_TOOL_QUERY_ORDER
                    )
            )
    );

    private AuthorizationCatalog() {
    }

    public record PermissionDefinition(String code, String name, String resourceType, String action) {
    }

    public record RoleDefinition(
            String code,
            String name,
            String scopeType,
            String description,
            Set<String> permissionCodes
    ) {
    }
}
