package com.saas.auditcompliance.repository;

import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.model.SourceIngestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceIngestionStatusRepository extends JpaRepository<SourceIngestionStatus, UUID> {

    Optional<SourceIngestionStatus> findByTenantIdAndSourceService(UUID tenantId, SourceService sourceService);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SourceIngestionStatus s WHERE s.tenantId = :tenantId AND s.sourceService = :sourceService")
    Optional<SourceIngestionStatus> findForUpdateByTenantIdAndSourceService(
            @Param("tenantId") UUID tenantId, @Param("sourceService") SourceService sourceService);

    List<SourceIngestionStatus> findByGapSuspectedTrue();
}