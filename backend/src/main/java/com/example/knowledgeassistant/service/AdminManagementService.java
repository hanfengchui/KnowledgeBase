package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.dto.AssignUserRolesRequest;
import com.example.knowledgeassistant.dto.AuditLogSummary;
import com.example.knowledgeassistant.dto.CreateTenantRequest;
import com.example.knowledgeassistant.dto.CreateUserRequest;
import com.example.knowledgeassistant.dto.KnowledgeBaseMemberRequest;
import com.example.knowledgeassistant.dto.KnowledgeBaseMemberSummary;
import com.example.knowledgeassistant.dto.RoleSummary;
import com.example.knowledgeassistant.dto.TenantSummary;
import com.example.knowledgeassistant.dto.UpdateTenantRequest;
import com.example.knowledgeassistant.dto.UpdateUserRequest;
import com.example.knowledgeassistant.dto.UserSummary;
import com.example.knowledgeassistant.security.AuthorizationCatalog;
import com.example.knowledgeassistant.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminManagementService {

    private static final Set<String> PLATFORM_SCOPES = Set.of("platform");
    private static final Set<String> TENANT_SCOPES = Set.of("tenant");
    private static final Set<String> KNOWLEDGE_BASE_SCOPES = Set.of("knowledge_base");

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final PermissionService permissionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;

    public AdminManagementService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            PasswordEncoder passwordEncoder,
            CurrentUserProvider currentUserProvider,
            PermissionService permissionService,
            KnowledgeBaseService knowledgeBaseService,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
        this.permissionService = permissionService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
    }

    public List<TenantSummary> listTenants() {
        CurrentUser currentUser = requirePlatformAdmin();
        return jdbcTemplate.query("""
                SELECT t.id,
                       t.code,
                       t.name,
                       t.status,
                       t.created_at,
                       COUNT(DISTINCT u.id) AS user_count,
                       COUNT(DISTINCT kb.id) AS knowledge_base_count
                FROM tenants t
                LEFT JOIN users u ON u.tenant_id = t.id
                LEFT JOIN knowledge_bases kb ON kb.tenant_id = t.id
                GROUP BY t.id, t.code, t.name, t.status, t.created_at
                ORDER BY t.created_at ASC
                """, (rs, rowNum) -> mapTenantSummary(rs));
    }

    @Transactional
    public TenantSummary createTenant(CreateTenantRequest request) {
        CurrentUser currentUser = requirePlatformAdmin();
        String name = request.name().trim();
        String baseCode = StringUtils.hasText(request.code()) ? normalizeCode(request.code()) : normalizeCode(name);
        if (!StringUtils.hasText(baseCode)) {
            baseCode = "tenant-" + UUID.randomUUID().toString().substring(0, 8);
        }

        UUID tenantId = UUID.randomUUID();
        String code = nextAvailableTenantCode(baseCode);
        jdbcTemplate.update("""
                INSERT INTO tenants (id, code, name, status)
                VALUES (?, ?, ?, 'active')
                """, tenantId, code, name);
        knowledgeBaseService.ensureDefaultKnowledgeBaseForTenant(tenantId);

        auditLogService.record(
                currentUser,
                "tenant.create",
                "tenant",
                tenantId.toString(),
                "name=" + name + ", code=" + code,
                HttpStatus.OK.value()
        );
        return getTenantSummary(tenantId);
    }

    @Transactional
    public TenantSummary updateTenant(UUID tenantId, UpdateTenantRequest request) {
        CurrentUser currentUser = requirePlatformAdmin();
        TenantSummary existing = getTenantSummary(tenantId);
        String nextName = StringUtils.hasText(request.name()) ? request.name().trim() : existing.name();
        String nextStatus = normalizeStatus(request.status(), existing.status());

        jdbcTemplate.update("""
                UPDATE tenants
                SET name = ?, status = ?
                WHERE id = ?
                """, nextName, nextStatus, tenantId);

        auditLogService.record(
                currentUser,
                "tenant.update",
                "tenant",
                tenantId.toString(),
                "name=" + nextName + ", status=" + nextStatus,
                HttpStatus.OK.value()
        );
        return getTenantSummary(tenantId);
    }

    public List<UserSummary> listUsers(UUID tenantId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        permissionService.requireTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_USER_READ);
        UUID targetTenantId = resolveTargetTenantId(currentUser, tenantId);

        List<UserRow> rows = jdbcTemplate.query("""
                SELECT u.id,
                       u.tenant_id,
                       t.code AS tenant_code,
                       t.name AS tenant_name,
                       u.username,
                       u.display_name,
                       u.email,
                       u.status,
                       u.created_at
                FROM users u
                LEFT JOIN tenants t ON t.id = u.tenant_id
                WHERE u.tenant_id = ?
                ORDER BY u.created_at ASC
                """, (rs, rowNum) -> mapUserRow(rs), targetTenantId);

        return rows.stream()
                .map(row -> new UserSummary(
                        row.id(),
                        row.tenantId(),
                        row.tenantCode(),
                        row.tenantName(),
                        row.username(),
                        row.displayName(),
                        row.email(),
                        row.status(),
                        row.createdAt(),
                        loadUserRoleCodes(row.id(), null, PLATFORM_SCOPES),
                        loadUserRoleCodes(row.id(), targetTenantId, TENANT_SCOPES)
                ))
                .toList();
    }

    @Transactional
    public UserSummary createUser(CreateUserRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        permissionService.requireTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_USER_CREATE);
        UUID targetTenantId = resolveTargetTenantId(currentUser, request.tenantId());
        ensureTenantExists(targetTenantId);

        UUID userId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (id, tenant_id, username, display_name, email, password_hash, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'active')
                    """,
                    userId,
                    targetTenantId,
                    request.username().trim(),
                    request.displayName().trim(),
                    nullableText(request.email()),
                    passwordEncoder.encode(request.password())
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在", ex);
        }

        replaceTenantRoles(userId, targetTenantId, sanitizeRoleCodes(request.tenantRoleCodes(), TENANT_SCOPES));
        auditLogService.record(
                currentUser,
                "user.create",
                "user",
                userId.toString(),
                "tenantId=" + targetTenantId + ", username=" + request.username().trim(),
                HttpStatus.OK.value()
        );
        return getUserSummary(userId, targetTenantId);
    }

    @Transactional
    public UserSummary updateUser(UUID userId, UpdateUserRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        permissionService.requireTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_USER_UPDATE);
        UserSummary existing = getUserSummary(userId, null);
        ensureTenantAccessible(currentUser, existing.tenantId());

        String nextDisplayName = StringUtils.hasText(request.displayName()) ? request.displayName().trim() : existing.displayName();
        String nextEmail = request.email() == null ? existing.email() : nullableText(request.email());
        String nextStatus = normalizeStatus(request.status(), existing.status());
        if (!existing.status().equals(nextStatus)) {
            permissionService.requireTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_USER_DISABLE);
        }

        jdbcTemplate.update("""
                UPDATE users
                SET display_name = ?, email = ?, status = ?
                WHERE id = ?
                """, nextDisplayName, nextEmail, nextStatus, userId);

        auditLogService.record(
                currentUser,
                "user.update",
                "user",
                userId.toString(),
                "displayName=" + nextDisplayName + ", status=" + nextStatus,
                HttpStatus.OK.value()
        );
        return getUserSummary(userId, existing.tenantId());
    }

    public List<RoleSummary> listRoles() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isPlatformAdmin() && !permissionService.hasTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_ROLE_ASSIGN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权查看角色配置");
        }

        return jdbcTemplate.query("""
                SELECT r.id,
                       r.code,
                       r.name,
                       r.scope_type,
                       r.description,
                       p.code AS permission_code
                FROM roles r
                LEFT JOIN role_permissions rp ON rp.role_id = r.id
                LEFT JOIN permissions p ON p.id = rp.permission_id
                ORDER BY r.scope_type, r.code, p.code
                """, rs -> {
            Map<UUID, RoleAccumulator> roles = new LinkedHashMap<>();
            while (rs.next()) {
                UUID roleId = rs.getObject("id", UUID.class);
                RoleAccumulator accumulator = roles.get(roleId);
                if (accumulator == null) {
                    accumulator = new RoleAccumulator(
                            roleId,
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("scope_type"),
                            rs.getString("description"),
                            new LinkedHashSet<>()
                    );
                    roles.put(roleId, accumulator);
                }
                String permissionCode = rs.getString("permission_code");
                if (permissionCode != null) {
                    accumulator.permissionCodes().add(permissionCode);
                }
            }
            return roles.values().stream()
                    .map(item -> new RoleSummary(
                            item.id(),
                            item.code(),
                            item.name(),
                            item.scopeType(),
                            item.description(),
                            List.copyOf(item.permissionCodes())
                    ))
                    .toList();
        });
    }

    @Transactional
    public UserSummary assignUserRoles(UUID userId, AssignUserRolesRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        permissionService.requireTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_ROLE_ASSIGN);

        UserSummary existing = getUserSummary(userId, null);
        UUID targetTenantId = resolveTargetTenantId(currentUser, request.tenantId() == null ? existing.tenantId() : request.tenantId());
        ensureTenantAccessible(currentUser, targetTenantId);

        if (currentUser.isPlatformAdmin()) {
            replacePlatformRoles(userId, sanitizeRoleCodes(request.platformRoleCodes(), PLATFORM_SCOPES));
        }
        replaceTenantRoles(userId, targetTenantId, sanitizeRoleCodes(request.tenantRoleCodes(), TENANT_SCOPES));

        auditLogService.record(
                currentUser,
                "role.assign",
                "user",
                userId.toString(),
                "tenantId=" + targetTenantId + ", platformRoles=" + request.platformRoleCodes() + ", tenantRoles=" + request.tenantRoleCodes(),
                HttpStatus.OK.value()
        );
        return getUserSummary(userId, targetTenantId);
    }

    public List<KnowledgeBaseMemberSummary> listKnowledgeBaseMembers(UUID knowledgeBaseId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        requireKnowledgeBaseAuthorization(currentUser, knowledgeBaseId);

        return jdbcTemplate.query("""
                SELECT m.knowledge_base_id,
                       u.id AS user_id,
                       u.username,
                       u.display_name,
                       u.email,
                       m.role_code,
                       m.created_at
                FROM knowledge_base_members m
                JOIN users u ON u.id = m.user_id
                WHERE m.knowledge_base_id = ?
                ORDER BY u.username, m.role_code
                """, rs -> {
            Map<UUID, MemberAccumulator> members = new LinkedHashMap<>();
            while (rs.next()) {
                UUID userId = rs.getObject("user_id", UUID.class);
                MemberAccumulator accumulator = members.get(userId);
                if (accumulator == null) {
                    accumulator = new MemberAccumulator(
                            rs.getObject("knowledge_base_id", UUID.class),
                            userId,
                            rs.getString("username"),
                            rs.getString("display_name"),
                            rs.getString("email"),
                            new ArrayList<>(),
                            Optional.ofNullable(rs.getTimestamp("created_at")).map(Timestamp::toInstant).orElse(null)
                    );
                    members.put(userId, accumulator);
                }
                accumulator.roleCodes().add(rs.getString("role_code"));
            }
            return members.values().stream()
                    .map(item -> new KnowledgeBaseMemberSummary(
                            item.knowledgeBaseId(),
                            item.userId(),
                            item.username(),
                            item.displayName(),
                            item.email(),
                            List.copyOf(item.roleCodes()),
                            item.grantedAt()
                    ))
                    .toList();
        }, knowledgeBaseId);
    }

    @Transactional
    public List<KnowledgeBaseMemberSummary> saveKnowledgeBaseMember(UUID knowledgeBaseId, KnowledgeBaseMemberRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        requireKnowledgeBaseAuthorization(currentUser, knowledgeBaseId);
        UUID tenantId = lookupKnowledgeBaseTenant(knowledgeBaseId);
        ensureUserInTenant(request.userId(), tenantId);
        List<String> roleCodes = sanitizeRoleCodes(request.roleCodes(), KNOWLEDGE_BASE_SCOPES);

        jdbcTemplate.update("""
                DELETE FROM knowledge_base_members
                WHERE knowledge_base_id = ?
                  AND user_id = ?
                """, knowledgeBaseId, request.userId());

        for (String roleCode : roleCodes) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_base_members (knowledge_base_id, user_id, role_code, granted_by)
                    VALUES (?, ?, ?, ?)
                    """, knowledgeBaseId, request.userId(), roleCode, currentUser.userId());
        }

        auditLogService.record(
                currentUser,
                "kb.authorize",
                "knowledge_base",
                knowledgeBaseId.toString(),
                "userId=" + request.userId() + ", roleCodes=" + roleCodes,
                HttpStatus.OK.value()
        );
        return listKnowledgeBaseMembers(knowledgeBaseId);
    }

    @Transactional
    public void deleteKnowledgeBaseMember(UUID knowledgeBaseId, UUID userId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        requireKnowledgeBaseAuthorization(currentUser, knowledgeBaseId);

        jdbcTemplate.update("""
                DELETE FROM knowledge_base_members
                WHERE knowledge_base_id = ?
                  AND user_id = ?
                """, knowledgeBaseId, userId);

        auditLogService.record(
                currentUser,
                "kb.deauthorize",
                "knowledge_base",
                knowledgeBaseId.toString(),
                "userId=" + userId,
                HttpStatus.OK.value()
        );
    }

    public List<AuditLogSummary> listAuditLogs(
            UUID tenantId,
            UUID userId,
            String action,
            String resourceType,
            Instant createdFrom,
            Instant createdTo
    ) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        permissionService.requireTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_AUDIT_READ);

        UUID targetTenantId = currentUser.isPlatformAdmin() ? tenantId : currentUser.tenantId();
        StringBuilder sql = new StringBuilder("""
                SELECT a.id,
                       a.tenant_id,
                       t.name AS tenant_name,
                       a.user_id,
                       u.username,
                       a.action,
                       a.resource_type,
                       a.resource_id,
                       a.request_path,
                       a.request_method,
                       a.request_payload_summary,
                       a.response_status,
                       a.created_at
                FROM audit_logs a
                LEFT JOIN tenants t ON t.id = a.tenant_id
                LEFT JOIN users u ON u.id = a.user_id
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (targetTenantId != null) {
            sql.append(" AND a.tenant_id = :tenantId");
            params.addValue("tenantId", targetTenantId);
        }
        if (userId != null) {
            sql.append(" AND a.user_id = :userId");
            params.addValue("userId", userId);
        }
        if (StringUtils.hasText(action)) {
            sql.append(" AND a.action = :action");
            params.addValue("action", action.trim());
        }
        if (StringUtils.hasText(resourceType)) {
            sql.append(" AND a.resource_type = :resourceType");
            params.addValue("resourceType", resourceType.trim());
        }
        if (createdFrom != null) {
            sql.append(" AND a.created_at >= :createdFrom");
            params.addValue("createdFrom", Timestamp.from(createdFrom));
        }
        if (createdTo != null) {
            sql.append(" AND a.created_at <= :createdTo");
            params.addValue("createdTo", Timestamp.from(createdTo));
        }

        sql.append(" ORDER BY a.created_at DESC LIMIT 200");
        return namedParameterJdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new AuditLogSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_name"),
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("request_path"),
                rs.getString("request_method"),
                rs.getString("request_payload_summary"),
                rs.getInt("response_status"),
                Optional.ofNullable(rs.getTimestamp("created_at")).map(Timestamp::toInstant).orElse(null)
        ));
    }

    private CurrentUser requirePlatformAdmin() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅平台管理员可访问当前接口");
        }
        return currentUser;
    }

    private TenantSummary getTenantSummary(UUID tenantId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT t.id,
                           t.code,
                           t.name,
                           t.status,
                           t.created_at,
                           COUNT(DISTINCT u.id) AS user_count,
                           COUNT(DISTINCT kb.id) AS knowledge_base_count
                    FROM tenants t
                    LEFT JOIN users u ON u.tenant_id = t.id
                    LEFT JOIN knowledge_bases kb ON kb.tenant_id = t.id
                    WHERE t.id = ?
                    GROUP BY t.id, t.code, t.name, t.status, t.created_at
                    """, (rs, rowNum) -> mapTenantSummary(rs), tenantId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定租户");
        }
    }

    private TenantSummary mapTenantSummary(ResultSet rs) throws SQLException {
        return new TenantSummary(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("status"),
                Optional.ofNullable(rs.getTimestamp("created_at")).map(Timestamp::toInstant).orElse(null),
                rs.getLong("user_count"),
                rs.getLong("knowledge_base_count")
        );
    }

    private UserSummary getUserSummary(UUID userId, UUID tenantIdHint) {
        try {
            UserRow row = jdbcTemplate.queryForObject("""
                    SELECT u.id,
                           u.tenant_id,
                           t.code AS tenant_code,
                           t.name AS tenant_name,
                           u.username,
                           u.display_name,
                           u.email,
                           u.status,
                           u.created_at
                    FROM users u
                    LEFT JOIN tenants t ON t.id = u.tenant_id
                    WHERE u.id = ?
                    """, (rs, rowNum) -> mapUserRow(rs), userId);
            UUID tenantId = tenantIdHint == null ? row.tenantId() : tenantIdHint;
            return new UserSummary(
                    row.id(),
                    row.tenantId(),
                    row.tenantCode(),
                    row.tenantName(),
                    row.username(),
                    row.displayName(),
                    row.email(),
                    row.status(),
                    row.createdAt(),
                    loadUserRoleCodes(row.id(), null, PLATFORM_SCOPES),
                    loadUserRoleCodes(row.id(), tenantId, TENANT_SCOPES)
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定用户");
        }
    }

    private UserRow mapUserRow(ResultSet rs) throws SQLException {
        return new UserRow(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_code"),
                rs.getString("tenant_name"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("status"),
                Optional.ofNullable(rs.getTimestamp("created_at")).map(Timestamp::toInstant).orElse(null)
        );
    }

    private UUID resolveTargetTenantId(CurrentUser currentUser, UUID requestedTenantId) {
        if (currentUser.isPlatformAdmin()) {
            if (requestedTenantId != null) {
                return requestedTenantId;
            }
            if (currentUser.tenantId() != null) {
                return currentUser.tenantId();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定租户");
        }

        if (currentUser.tenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定租户");
        }
        if (requestedTenantId != null && !requestedTenantId.equals(currentUser.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不可访问其他租户");
        }
        return currentUser.tenantId();
    }

    private void ensureTenantExists(UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM tenants WHERE id = ?",
                Integer.class,
                tenantId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定租户");
        }
    }

    private void ensureTenantAccessible(CurrentUser currentUser, UUID tenantId) {
        if (currentUser.isPlatformAdmin()) {
            return;
        }
        if (currentUser.tenantId() == null || !currentUser.tenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不可访问其他租户");
        }
    }

    private String normalizeStatus(String requestedStatus, String currentStatus) {
        if (!StringUtils.hasText(requestedStatus)) {
            return currentStatus;
        }
        String normalized = requestedStatus.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("active", "disabled", "inactive").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的状态值");
        }
        return normalized;
    }

    private List<String> loadUserRoleCodes(UUID userId, UUID tenantId, Set<String> allowedScopes) {
        String scopeList = allowedScopes.stream()
                .map(scope -> "'" + scope + "'")
                .collect(Collectors.joining(","));
        String sql = """
                SELECT r.code
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND %s
                  AND r.scope_type IN (%s)
                ORDER BY r.code
                """.formatted(tenantId == null ? "ur.tenant_id IS NULL" : "ur.tenant_id = ?", scopeList);
        if (tenantId == null) {
            return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("code"), userId);
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("code"), userId, tenantId);
    }

    private void replacePlatformRoles(UUID userId, List<String> roleCodes) {
        jdbcTemplate.update("""
                DELETE FROM user_roles
                WHERE user_id = ?
                  AND tenant_id IS NULL
                  AND role_id IN (SELECT id FROM roles WHERE scope_type = 'platform')
                """, userId);
        for (String roleCode : roleCodes) {
            insertUserRole(userId, roleCode, null);
        }
    }

    private void replaceTenantRoles(UUID userId, UUID tenantId, List<String> roleCodes) {
        jdbcTemplate.update("""
                DELETE FROM user_roles
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND role_id IN (SELECT id FROM roles WHERE scope_type = 'tenant')
                """, userId, tenantId);
        for (String roleCode : roleCodes) {
            insertUserRole(userId, roleCode, tenantId);
        }
    }

    private void insertUserRole(UUID userId, String roleCode, UUID tenantId) {
        jdbcTemplate.update("""
                INSERT INTO user_roles (id, user_id, role_id, tenant_id)
                SELECT ?, ?, r.id, ?
                FROM roles r
                WHERE r.code = ?
                """, UUID.randomUUID(), userId, tenantId, roleCode);
    }

    private List<String> sanitizeRoleCodes(List<String> roleCodes, Set<String> allowedScopes) {
        if (roleCodes == null) {
            return List.of();
        }

        List<String> normalized = roleCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> existing = namedParameterJdbcTemplate.query("""
                SELECT code
                FROM roles
                WHERE code IN (:codes)
                  AND scope_type IN (:scopes)
                ORDER BY code
                """, new MapSqlParameterSource()
                .addValue("codes", normalized)
                .addValue("scopes", allowedScopes), (rs, rowNum) -> rs.getString("code"));

        if (existing.size() != normalized.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在无效的角色代码");
        }
        return existing;
    }

    private void requireKnowledgeBaseAuthorization(CurrentUser currentUser, UUID knowledgeBaseId) {
        if (currentUser.isPlatformAdmin() || currentUser.hasRole(AuthorizationCatalog.ROLE_TENANT_ADMIN)) {
            return;
        }
        permissionService.requireKnowledgeBasePermission(currentUser, knowledgeBaseId, AuthorizationCatalog.PERMISSION_KB_AUTHORIZE);
    }

    private UUID lookupKnowledgeBaseTenant(UUID knowledgeBaseId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM knowledge_bases WHERE id = ?",
                    UUID.class,
                    knowledgeBaseId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定知识库");
        }
    }

    private void ensureUserInTenant(UUID userId, UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE id = ? AND tenant_id = ?",
                Integer.class,
                userId,
                tenantId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标用户不属于当前租户");
        }
    }

    private String nextAvailableTenantCode(String baseCode) {
        String candidate = baseCode;
        int suffix = 2;
        while (tenantCodeExists(candidate)) {
            candidate = baseCode + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean tenantCodeExists(String code) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT CASE WHEN EXISTS (SELECT 1 FROM tenants WHERE code = ?) THEN 1 ELSE 0 END",
                Integer.class,
                code
        );
        return exists != null && exists == 1;
    }

    private String normalizeCode(String raw) {
        return Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String nullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record UserRow(
            UUID id,
            UUID tenantId,
            String tenantCode,
            String tenantName,
            String username,
            String displayName,
            String email,
            String status,
            Instant createdAt
    ) {
    }

    private record RoleAccumulator(
            UUID id,
            String code,
            String name,
            String scopeType,
            String description,
            LinkedHashSet<String> permissionCodes
    ) {
    }

    private record MemberAccumulator(
            UUID knowledgeBaseId,
            UUID userId,
            String username,
            String displayName,
            String email,
            List<String> roleCodes,
            Instant grantedAt
    ) {
    }
}
