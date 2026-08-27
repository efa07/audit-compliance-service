package com.saas.auditcompliance.model.refcache;

import com.saas.auditcompliance.model.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "ref_organization_units",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_ref_org_unit_external", columnNames = {"tenantId", "externalId"})
    },
    indexes = {
        @Index(name = "idx_ref_org_unit_code", columnList = "tenantId, code"),
        @Index(name = "idx_ref_org_unit_parent", columnList = "tenantId, parentExternalId")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class RefOrganizationUnit extends Base {

    @Column(nullable = false)
    private UUID externalId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private UUID parentExternalId;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime syncedAt;
}