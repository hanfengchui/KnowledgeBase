package com.example.knowledgeassistant.controller;

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
import com.example.knowledgeassistant.service.AdminManagementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminManagementService adminManagementService;

    public AdminController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    @GetMapping("/tenants")
    public List<TenantSummary> listTenants() {
        return adminManagementService.listTenants();
    }

    @PostMapping("/tenants")
    public TenantSummary createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return adminManagementService.createTenant(request);
    }

    @PatchMapping("/tenants/{id}")
    public TenantSummary updateTenant(@PathVariable("id") UUID tenantId, @Valid @RequestBody UpdateTenantRequest request) {
        return adminManagementService.updateTenant(tenantId, request);
    }

    @GetMapping("/users")
    public List<UserSummary> listUsers(@RequestParam(value = "tenantId", required = false) UUID tenantId) {
        return adminManagementService.listUsers(tenantId);
    }

    @PostMapping("/users")
    public UserSummary createUser(@Valid @RequestBody CreateUserRequest request) {
        return adminManagementService.createUser(request);
    }

    @PatchMapping("/users/{id}")
    public UserSummary updateUser(@PathVariable("id") UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return adminManagementService.updateUser(userId, request);
    }

    @GetMapping("/roles")
    public List<RoleSummary> listRoles() {
        return adminManagementService.listRoles();
    }

    @PostMapping("/users/{id}/roles")
    public UserSummary assignUserRoles(@PathVariable("id") UUID userId, @RequestBody AssignUserRolesRequest request) {
        return adminManagementService.assignUserRoles(userId, request);
    }

    @GetMapping("/knowledge-bases/{id}/members")
    public List<KnowledgeBaseMemberSummary> listKnowledgeBaseMembers(@PathVariable("id") UUID knowledgeBaseId) {
        return adminManagementService.listKnowledgeBaseMembers(knowledgeBaseId);
    }

    @PostMapping("/knowledge-bases/{id}/members")
    public List<KnowledgeBaseMemberSummary> saveKnowledgeBaseMembers(
            @PathVariable("id") UUID knowledgeBaseId,
            @Valid @RequestBody KnowledgeBaseMemberRequest request
    ) {
        return adminManagementService.saveKnowledgeBaseMember(knowledgeBaseId, request);
    }

    @DeleteMapping("/knowledge-bases/{id}/members")
    public void deleteKnowledgeBaseMember(
            @PathVariable("id") UUID knowledgeBaseId,
            @RequestParam("userId") UUID userId
    ) {
        adminManagementService.deleteKnowledgeBaseMember(knowledgeBaseId, userId);
    }

    @GetMapping("/audit-logs")
    public List<AuditLogSummary> listAuditLogs(
            @RequestParam(value = "tenantId", required = false) UUID tenantId,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "createdFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(value = "createdTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo
    ) {
        return adminManagementService.listAuditLogs(tenantId, userId, action, resourceType, createdFrom, createdTo);
    }
}
