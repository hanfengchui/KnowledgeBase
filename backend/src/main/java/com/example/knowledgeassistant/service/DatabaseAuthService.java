package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.dto.AuthMeResponse;
import com.example.knowledgeassistant.dto.AvailableTenantDto;
import com.example.knowledgeassistant.dto.KnowledgeBaseAccessDto;
import com.example.knowledgeassistant.dto.LoginRequest;
import com.example.knowledgeassistant.dto.LoginResponse;
import com.example.knowledgeassistant.dto.SwitchTenantRequest;
import com.example.knowledgeassistant.security.AuthorizationCatalog;
import com.example.knowledgeassistant.security.CurrentUser;
import com.example.knowledgeassistant.security.InvalidTokenException;
import com.example.knowledgeassistant.security.JwtClaims;
import com.example.knowledgeassistant.security.JwtService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DatabaseAuthService implements AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public DatabaseAuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PermissionService permissionService,
            AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount user = findUserByUsername(request.username())
                .orElseThrow(() -> loginFailed(request.username(), "用户名或密码错误"));

        if (!"active".equalsIgnoreCase(user.status())) {
            throw loginFailed(request.username(), "账号已停用");
        }

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw loginFailed(request.username(), "用户名或密码错误");
        }

        boolean platformAdmin = hasPlatformAdminRole(user.id());
        TenantContext tenantContext = resolveLoginTenant(user, platformAdmin, request.tenantCode());
        Set<String> roleCodes = loadRoleCodes(user.id(), tenantContext.id());
        JwtService.IssuedToken issuedToken = jwtService.issueToken(
                user.id(),
                user.username(),
                tenantContext.id(),
                tenantContext.code(),
                roleCodes
        );

        auditLogService.record(
                tenantContext.id(),
                user.id(),
                "auth.login_success",
                "auth",
                user.id().toString(),
                "username=" + user.username() + ", tenant=" + tenantContext.code(),
                HttpStatus.OK.value()
        );
        return new LoginResponse(issuedToken.token(), "Bearer", issuedToken.expiresAt());
    }

    @Override
    @Transactional
    public LoginResponse switchTenant(CurrentUser currentUser, SwitchTenantRequest request) {
        if (!currentUser.isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅平台管理员可切换租户");
        }

        TenantContext tenantContext = findActiveTenantById(request.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到可切换的租户"));
        Set<String> roleCodes = loadRoleCodes(currentUser.userId(), tenantContext.id());
        JwtService.IssuedToken issuedToken = jwtService.issueToken(
                currentUser.userId(),
                currentUser.username(),
                tenantContext.id(),
                tenantContext.code(),
                roleCodes
        );

        auditLogService.record(
                tenantContext.id(),
                currentUser.userId(),
                "auth.switch_tenant",
                "tenant",
                tenantContext.id().toString(),
                "tenant=" + tenantContext.code(),
                HttpStatus.OK.value()
        );
        return new LoginResponse(issuedToken.token(), "Bearer", issuedToken.expiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthMeResponse me(CurrentUser currentUser) {
        UserAccount user = findUserById(currentUser.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效"));
        TenantContext tenant = currentUser.tenantId() == null ? null : findActiveTenantById(currentUser.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前租户不可用"));

        Set<String> tenantPermissions = permissionService.getTenantPermissionCodes(currentUser);
        List<AvailableTenantDto> availableTenants = listAvailableTenants(user, currentUser.isPlatformAdmin()).stream()
                .map(item -> new AvailableTenantDto(item.id(), item.code(), item.name(), item.status()))
                .toList();

        List<KnowledgeBaseAccessDto> knowledgeBaseAccesses = tenant == null ? List.of() : jdbcTemplate.query("""
                SELECT id, name
                FROM knowledge_bases
                WHERE tenant_id = ?
                ORDER BY is_default DESC, created_at ASC
                """, (rs, rowNum) -> new SimpleKnowledgeBase(rs.getObject("id", UUID.class), rs.getString("name")), tenant.id()).stream()
                .filter(kb -> permissionService.canAccessKnowledgeBase(currentUser, kb.id()))
                .map(kb -> new KnowledgeBaseAccessDto(
                        kb.id(),
                        kb.name(),
                        permissionService.getKnowledgeBaseRoleCodes(currentUser, kb.id()),
                        permissionService.getKnowledgeBasePermissionCodes(currentUser, kb.id())
                ))
                .toList();

        return new AuthMeResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                user.status(),
                currentUser.isPlatformAdmin(),
                tenant == null ? null : tenant.id(),
                tenant == null ? null : tenant.code(),
                tenant == null ? null : tenant.name(),
                currentUser.roleCodes(),
                tenantPermissions,
                availableTenants,
                knowledgeBaseAccesses
        );
    }

    @Override
    @Transactional
    public void logout(CurrentUser currentUser, String rawToken) {
        JwtClaims claims = jwtService.parse(rawToken);
        jdbcTemplate.update("""
                INSERT INTO revoked_tokens (token_id, user_id, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (token_id) DO NOTHING
                """,
                claims.tokenId(),
                currentUser.userId(),
                claims.expiresAt()
        );

        auditLogService.record(
                currentUser,
                "auth.logout",
                "auth",
                currentUser.userId().toString(),
                "tokenId=" + currentUser.tokenId(),
                HttpStatus.OK.value()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUser loadCurrentUser(JwtClaims claims) {
        if (isTokenRevoked(claims.tokenId())) {
            throw new InvalidTokenException("Token has been revoked");
        }

        UserAccount user = findUserById(claims.userId())
                .orElseThrow(() -> new InvalidTokenException("User not found"));
        if (!"active".equalsIgnoreCase(user.status())) {
            throw new InvalidTokenException("User is disabled");
        }

        boolean platformAdmin = hasPlatformAdminRole(user.id());
        if (!platformAdmin) {
            if (user.tenantId() == null || claims.tenantId() == null || !user.tenantId().equals(claims.tenantId())) {
                throw new InvalidTokenException("Tenant scope is invalid");
            }
        }

        TenantContext tenant = claims.tenantId() == null ? null : findActiveTenantById(claims.tenantId())
                .orElseThrow(() -> new InvalidTokenException("Tenant is unavailable"));
        Set<String> roleCodes = loadRoleCodes(user.id(), tenant == null ? null : tenant.id());

        return new CurrentUser(
                user.id(),
                user.username(),
                user.displayName(),
                tenant == null ? null : tenant.id(),
                tenant == null ? null : tenant.code(),
                roleCodes,
                claims.tokenId()
        );
    }

    private ResponseStatusException loginFailed(String username, String reason) {
        auditLogService.record(
                null,
                null,
                "auth.login_failed",
                "auth",
                username,
                "username=" + username + ", reason=" + reason,
                HttpStatus.UNAUTHORIZED.value()
        );
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }

    private TenantContext resolveLoginTenant(UserAccount user, boolean platformAdmin, String tenantCode) {
        List<TenantContext> availableTenants = listAvailableTenants(user, platformAdmin);
        if (availableTenants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有可用租户");
        }

        if (StringUtils.hasText(tenantCode)) {
            return availableTenants.stream()
                    .filter(item -> item.code().equalsIgnoreCase(tenantCode.trim()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不可访问指定租户"));
        }

        if (!platformAdmin && user.tenantId() != null) {
            return availableTenants.stream()
                    .filter(item -> item.id().equals(user.tenantId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前租户不可用"));
        }

        return availableTenants.get(0);
    }

    private List<TenantContext> listAvailableTenants(UserAccount user, boolean platformAdmin) {
        if (platformAdmin) {
            return jdbcTemplate.query("""
                    SELECT id, code, name, status
                    FROM tenants
                    WHERE status = 'active'
                    ORDER BY created_at ASC
                    """, (rs, rowNum) -> mapTenant(rs));
        }

        if (user.tenantId() == null) {
            return List.of();
        }

        return jdbcTemplate.query("""
                SELECT id, code, name, status
                FROM tenants
                WHERE id = ?
                  AND status = 'active'
                """, (rs, rowNum) -> mapTenant(rs), user.tenantId());
    }

    private boolean hasPlatformAdminRole(UUID userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND ur.tenant_id IS NULL
                  AND r.code = ?
                """, Integer.class, userId, AuthorizationCatalog.ROLE_PLATFORM_ADMIN);
        return count != null && count > 0;
    }

    private Set<String> loadRoleCodes(UUID userId, UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT DISTINCT r.code
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND (ur.tenant_id = ? OR ur.tenant_id IS NULL)
                ORDER BY r.code
                """, (rs, rowNum) -> rs.getString("code"), userId, tenantId).stream()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean isTokenRevoked(String tokenId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM revoked_tokens WHERE token_id = ?",
                Integer.class,
                tokenId
        );
        return count != null && count > 0;
    }

    private java.util.Optional<UserAccount> findUserByUsername(String username) {
        return queryOptional("""
                SELECT id, tenant_id, username, display_name, email, password_hash, status
                FROM users
                WHERE lower(username) = lower(?)
                """, username);
    }

    private java.util.Optional<UserAccount> findUserById(UUID userId) {
        return queryOptional("""
                SELECT id, tenant_id, username, display_name, email, password_hash, status
                FROM users
                WHERE id = ?
                """, userId);
    }

    private java.util.Optional<UserAccount> queryOptional(String sql, Object... args) {
        try {
            return java.util.Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapUser, args));
        } catch (EmptyResultDataAccessException ex) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<TenantContext> findActiveTenantById(UUID tenantId) {
        try {
            return java.util.Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, code, name, status
                    FROM tenants
                    WHERE id = ?
                      AND status = 'active'
                    """, (rs, rowNum) -> mapTenant(rs), tenantId));
        } catch (EmptyResultDataAccessException ex) {
            return java.util.Optional.empty();
        }
    }

    private UserAccount mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserAccount(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("status")
        );
    }

    private TenantContext mapTenant(ResultSet rs) throws SQLException {
        return new TenantContext(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("status")
        );
    }

    private record UserAccount(
            UUID id,
            UUID tenantId,
            String username,
            String displayName,
            String email,
            String passwordHash,
            String status
    ) {
    }

    private record TenantContext(
            UUID id,
            String code,
            String name,
            String status
    ) {
    }

    private record SimpleKnowledgeBase(UUID id, String name) {
    }
}
