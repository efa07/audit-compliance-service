package com.saas.auditcompliance.service.impl;

import com.saas.auditcompliance.client.FinanceAdministrationClient;
import com.saas.auditcompliance.dto.clientDto.OrganizationUnitClientDto;
import com.saas.auditcompliance.dto.eventDto.inbound.OrganizationUnitSyncEvent;
import com.saas.auditcompliance.model.refcache.RefOrganizationUnit;
import com.saas.auditcompliance.repository.refcache.RefOrganizationUnitRepository;
import com.saas.auditcompliance.service.ReferenceDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataSyncServiceImpl implements ReferenceDataSyncService {

    private final RefOrganizationUnitRepository organizationUnitRepository;
    private final FinanceAdministrationClient financeAdministrationClient;

    @Override
    @Transactional
    public void syncOrganizationUnit(UUID tenantId, OrganizationUnitSyncEvent event) {
        RefOrganizationUnit entity = organizationUnitRepository
                .findByTenantIdAndExternalId(tenantId, event.getExternalId())
                .orElseGet(RefOrganizationUnit::new);

        entity.setTenantId(tenantId);
        entity.setExternalId(event.getExternalId());
        entity.setCode(event.getCode());
        entity.setName(event.getName());
        entity.setParentExternalId(event.getParentExternalId());
        entity.setActive(event.isActive());
        entity.setSyncedAt(LocalDateTime.now());

        organizationUnitRepository.save(entity);
    }

    @Override
    public void reconcileAll(UUID tenantId) {
        List<OrganizationUnitClientDto> units = financeAdministrationClient.getAllOrganizationUnits(tenantId);

        for (OrganizationUnitClientDto dto : units) {
            OrganizationUnitSyncEvent event = new OrganizationUnitSyncEvent();
            event.setExternalId(dto.getExternalId());
            event.setCode(dto.getCode());
            event.setName(dto.getName());
            event.setParentExternalId(dto.getParentExternalId());
            event.setActive(dto.isActive());
            syncOrganizationUnit(tenantId, event);
        }

        log.info("Reconciled {} organization unit(s) for tenant {}", units.size(), tenantId);
    }
}