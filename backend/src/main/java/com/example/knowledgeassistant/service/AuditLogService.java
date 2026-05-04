package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentUser currentUser, String action, String resourceType, String resourceId, String payloadSummary, int status) {
        insert(
                currentUser == null ? null : currentUser.tenantId(),
                currentUser == null ? null : currentUser.userId(),
                action,
                resourceType,
                resourceId,
                payloadSummary,
                status
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID tenantId, UUID userId, String action, String resourceType, String resourceId, String payloadSummary, int status) {
        insert(tenantId, userId, action, resourceType, resourceId, payloadSummary, status);
    }

    private void insert(UUID tenantId, UUID userId, String action, String resourceType, String resourceId, String payloadSummary, int status) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String requestPath = attributes == null ? null : attributes.getRequest().getRequestURI();
        String requestMethod = attributes == null ? null : attributes.getRequest().getMethod();

        jdbcTemplate.update("""
                INSERT INTO audit_logs (
                    id, tenant_id, user_id, action, resource_type, resource_id,
                    request_path, request_method, request_payload_summary, response_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                userId,
                action,
                resourceType,
                resourceId,
                requestPath,
                requestMethod,
                summarize(payloadSummary),
                status
        );
    }

    public String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 400 ? normalized : normalized.substring(0, 397) + "...";
    }
}
