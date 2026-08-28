package com.saas.auditcompliance.repository.refcache;

import com.saas.auditcompliance.model.refcache.RefOrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefOrganizationUnitRepository extends JpaRepository<RefOrganizationUnit, UUID> {

    Optional<RefOrganizationUnit> findByTenantIdAndExternalId(UUID tenantId, UUID externalId);

    Optional<RefOrganizationUnit> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<RefOrganizationUnit> findByTenantIdAndExternalIdAndActiveTrue(UUID tenantId, UUID externalId);

    List<RefOrganizationUnit> findByTenantIdAndActiveTrue(UUID tenantId);

    List<RefOrganizationUnit> findByTenantIdAndParentExternalId(UUID tenantId, UUID parentExternalId);
}