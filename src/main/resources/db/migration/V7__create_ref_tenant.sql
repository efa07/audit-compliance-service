CREATE TABLE ref_tenants (
    id BINARY(16) NOT NULL,
    external_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BIT(1) NOT NULL,
    synced_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE (external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
