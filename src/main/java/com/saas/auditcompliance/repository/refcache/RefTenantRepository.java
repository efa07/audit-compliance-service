package com.saas.auditcompliance.repository.refcache;

import com.saas.auditcompliance.model.refcache.RefTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefTenantRepository extends JpaRepository<RefTenant, UUID> {

    Optional<RefTenant> findByExternalId(UUID externalId);

    List<RefTenant> findByActiveTrue();
}