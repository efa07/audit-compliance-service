CREATE TABLE compliance_violation_reviews (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255),
    violation_id BINARY(16) NOT NULL,
    status VARCHAR(255) NOT NULL,
    reviewed_by BINARY(16),
    reviewed_at DATETIME(6),
    resolution_notes VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uq_review_violation UNIQUE (violation_id),
    INDEX idx_review_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
