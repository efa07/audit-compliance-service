CREATE TABLE ref_organization_units (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255),
    external_id BINARY(16) NOT NULL,
    code VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    parent_external_id BINARY(16),
    active BIT(1) NOT NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ref_org_unit_external UNIQUE (tenant_id, external_id),
    INDEX idx_ref_org_unit_code (tenant_id, code),
    INDEX idx_ref_org_unit_parent (tenant_id, parent_external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
