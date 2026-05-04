package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.SecurityProperties;
import com.example.knowledgeassistant.security.AuthorizationCatalog;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Component
public class BootstrapDataService implements ApplicationRunner {

    public static final UUID DEMO_TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    public static final UUID DEMO_DEFAULT_KB_ID = UUID.fromString("21000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    public BootstrapDataService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedRoles();
        UUID demoTenantId = seedDemoTenant();
        seedPlatformAdmin();
        seedDemoTenantAdmin(demoTenantId);
        backfillExistingRecords(demoTenantId);
        ensureDefaultKnowledgeBase(demoTenantId);
        cleanupExpiredRevokedTokens();
    }

    private void seedPermissions() {
        for (AuthorizationCatalog.PermissionDefinition definition : AuthorizationCatalog.PERMISSIONS) {
            jdbcTemplate.update("""
                    INSERT INTO permissions (id, code, name, resource_type, action)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (code) DO UPDATE
                    SET name = EXCLUDED.name,
                        resource_type = EXCLUDED.resource_type,
                        action = EXCLUDED.action
                    """,
                    UUID.randomUUID(),
                    definition.code(),
                    definition.name(),
                    definition.resourceType(),
                    definition.action()
            );
        }
    }

    private void seedRoles() {
        for (AuthorizationCatalog.RoleDefinition definition : AuthorizationCatalog.ROLES) {
            jdbcTemplate.update("""
                    INSERT INTO roles (id, code, name, scope_type, description)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (code) DO UPDATE
                    SET name = EXCLUDED.name,
                        scope_type = EXCLUDED.scope_type,
                        description = EXCLUDED.description
                    """,
                    UUID.randomUUID(),
                    definition.code(),
                    definition.name(),
                    definition.scopeType(),
                    definition.description()
            );

            for (String permissionCode : definition.permissionCodes()) {
                jdbcTemplate.update("""
                        INSERT INTO role_permissions (role_id, permission_id)
                        SELECT r.id, p.id
                        FROM roles r
                        JOIN permissions p ON p.code = ?
                        WHERE r.code = ?
                        ON CONFLICT DO NOTHING
                        """,
                        permissionCode,
                        definition.code()
                );
            }
        }
    }

    private UUID seedDemoTenant() {
        jdbcTemplate.update("""
                INSERT INTO tenants (id, code, name, status)
                VALUES (?, 'demo', '演示租户', 'active')
                ON CONFLICT (code) DO UPDATE
                SET name = EXCLUDED.name,
                    status = EXCLUDED.status
                """,
                DEMO_TENANT_ID
        );
        return DEMO_TENANT_ID;
    }

    private void seedPlatformAdmin() {
        SecurityProperties.BootstrapAdmin bootstrapAdmin = securityProperties.getBootstrapAdmin();
        UUID userId = upsertUser(
                null,
                bootstrapAdmin.getUsername(),
                "平台管理员",
                "platform-admin@knowledgehub.local",
                bootstrapAdmin.getPassword(),
                "active"
        );
        ensureUserRole(userId, AuthorizationCatalog.ROLE_PLATFORM_ADMIN, null);
    }

    private void seedDemoTenantAdmin(UUID tenantId) {
        UUID userId = upsertUser(
                tenantId,
                "tenant-admin",
                "演示租户管理员",
                "tenant-admin@knowledgehub.local",
                "TenantAdmin123!",
                "active"
        );
        ensureUserRole(userId, AuthorizationCatalog.ROLE_TENANT_ADMIN, tenantId);
    }

    private UUID upsertUser(
            UUID tenantId,
            String username,
            String displayName,
            String email,
            String rawPassword,
            String status
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (id, tenant_id, username, display_name, email, password_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (username) DO UPDATE
                SET tenant_id = EXCLUDED.tenant_id,
                    display_name = EXCLUDED.display_name,
                    email = EXCLUDED.email,
                    password_hash = EXCLUDED.password_hash,
                    status = EXCLUDED.status
                RETURNING id
                """,
                UUID.class,
                UUID.randomUUID(),
                tenantId,
                username,
                displayName,
                email,
                passwordEncoder.encode(rawPassword),
                status
        );
    }

    private void ensureUserRole(UUID userId, String roleCode, UUID tenantId) {
        jdbcTemplate.update("""
                INSERT INTO user_roles (id, user_id, role_id, tenant_id)
                SELECT ?, ?, r.id, ?
                FROM roles r
                WHERE r.code = ?
                ON CONFLICT DO NOTHING
                """,
                UUID.randomUUID(),
                userId,
                tenantId,
                roleCode
        );
    }

    private void backfillExistingRecords(UUID tenantId) {
        jdbcTemplate.update("""
                UPDATE knowledge_bases
                SET tenant_id = ?
                WHERE tenant_id IS NULL
                """,
                tenantId
        );

        jdbcTemplate.update("""
                UPDATE kb_documents d
                SET tenant_id = kb.tenant_id
                FROM knowledge_bases kb
                WHERE d.knowledge_base_id = kb.id
                  AND d.tenant_id IS NULL
                """);
    }

    private void ensureDefaultKnowledgeBase(UUID tenantId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM knowledge_bases WHERE tenant_id = ? AND is_default = TRUE",
                Integer.class,
                tenantId
        );
        if (existing != null && existing > 0) {
            return;
        }

        Integer anyKnowledgeBase = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM knowledge_bases WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        if (anyKnowledgeBase != null && anyKnowledgeBase > 0) {
            jdbcTemplate.update("""
                    UPDATE knowledge_bases
                    SET is_default = TRUE
                    WHERE id = (
                        SELECT id
                        FROM knowledge_bases
                        WHERE tenant_id = ?
                        ORDER BY created_at ASC
                        LIMIT 1
                    )
                    """,
                    tenantId
            );
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO knowledge_bases (id, tenant_id, code, name, description, is_default)
                VALUES (?, ?, 'general-operations', '通用运营知识库', '用于产品说明、政策流程、实施接入和常见问题的默认知识库。', TRUE)
                ON CONFLICT DO NOTHING
                """,
                DEMO_DEFAULT_KB_ID,
                tenantId
        );
    }

    private void cleanupExpiredRevokedTokens() {
        jdbcTemplate.update("DELETE FROM revoked_tokens WHERE expires_at < now()");
    }
}
