CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tenants_status
    ON tenants (status, created_at DESC);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    username VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(120);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'active';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS fk_users_tenant;

ALTER TABLE users
    ADD CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_users_tenant_status
    ON users (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (user_id, role_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_tenant
    ON user_roles (user_id, tenant_id);

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE knowledge_bases
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

ALTER TABLE knowledge_bases
    DROP CONSTRAINT IF EXISTS knowledge_bases_code_key;

DROP INDEX IF EXISTS idx_knowledge_bases_code;
DROP INDEX IF EXISTS idx_knowledge_bases_default;

ALTER TABLE knowledge_bases
    DROP CONSTRAINT IF EXISTS fk_knowledge_bases_tenant;

ALTER TABLE knowledge_bases
    ADD CONSTRAINT fk_knowledge_bases_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_bases_tenant_code
    ON knowledge_bases (tenant_id, code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_bases_tenant_default
    ON knowledge_bases (tenant_id)
    WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_knowledge_bases_tenant_created_at
    ON knowledge_bases (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS kb_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    knowledge_base_id UUID,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    document_type VARCHAR(32),
    content_hash CHAR(64) NOT NULL,
    char_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS knowledge_base_id UUID;

ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(32);

ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'pending';

ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS error_message TEXT;

ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE kb_documents
    ALTER COLUMN char_count SET DEFAULT 0;

UPDATE kb_documents
SET document_type = COALESCE(document_type, 'markdown'),
    status = COALESCE(status, 'indexed'),
    updated_at = COALESCE(updated_at, created_at, now())
WHERE document_type IS NULL
   OR status IS NULL
   OR updated_at IS NULL;

ALTER TABLE kb_documents
    DROP CONSTRAINT IF EXISTS fk_kb_documents_knowledge_base;

ALTER TABLE kb_documents
    ADD CONSTRAINT fk_kb_documents_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE;

ALTER TABLE kb_documents
    DROP CONSTRAINT IF EXISTS fk_kb_documents_tenant;

ALTER TABLE kb_documents
    ADD CONSTRAINT fk_kb_documents_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_kb_documents_tenant_kb_created_at
    ON kb_documents (tenant_id, knowledge_base_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_kb_documents_status
    ON kb_documents (status);

CREATE TABLE IF NOT EXISTS kb_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES kb_documents (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    char_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_kb_chunks_document_id
    ON kb_chunks (document_id);

CREATE TABLE IF NOT EXISTS knowledge_base_members (
    knowledge_base_id UUID NOT NULL REFERENCES knowledge_bases (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code VARCHAR(64) NOT NULL,
    granted_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (knowledge_base_id, user_id, role_code)
);

CREATE INDEX IF NOT EXISTS idx_kb_members_user
    ON knowledge_base_members (user_id, knowledge_base_id);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    token_id VARCHAR(64) PRIMARY KEY,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at
    ON revoked_tokens (expires_at);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants (id) ON DELETE SET NULL,
    user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(120),
    request_path VARCHAR(255),
    request_method VARCHAR(16),
    request_payload_summary TEXT,
    response_status INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_created_at
    ON audit_logs (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_created_at
    ON audit_logs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created_at
    ON audit_logs (action, created_at DESC);
