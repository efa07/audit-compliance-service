package com.saas.auditcompliance.repository;

import com.saas.auditcompliance.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedOrderByCreatedAtAsc(org.springframework.data.domain.Pageable pageable);

    List<OutboxEvent> findByTenantIdAndPublishedFalseAndRetryCountLessThan(UUID tenantId, int maxRetryCount);
}