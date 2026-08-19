package com.saas.budgetmanagement.bootstrap;

import com.saas.budgetmanagement.client.TenantRegistryClient;
import com.saas.budgetmanagement.dto.clientDto.TenantClientDto;
import com.saas.budgetmanagement.service.ReferenceDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataBootstrapRunner implements ApplicationRunner {

    private final ReferenceDataSyncService referenceDataSyncService;
    private final TenantRegistryClient tenantRegistryClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running reference-data reconciliation on application startup");
        reconcileAllTenants("startup");
    }

    @Scheduled(cron = "${budget-management.reference-sync.reconcile-cron:0 0 * * * *}")
    public void scheduledReconcile() {
        log.info("Running scheduled reference-data reconciliation");
        reconcileAllTenants("scheduled");
    }

    private void reconcileAllTenants(String trigger) {
        List<TenantClientDto> tenants;
        try {
            tenants = tenantRegistryClient.getActiveTenants();
        } catch (Exception e) {
            log.error("Failed to fetch tenant list for reconciliation (trigger={}): {}", trigger, e.getMessage(), e);
            return;
        }

        for (TenantClientDto tenant : tenants) {
            if (!tenant.isActive()) {
                continue;
            }
            try {
                referenceDataSyncService.reconcileAll(tenant.getTenantId());
            } catch (Exception e) {
                log.error("Reconciliation failed for tenant {} (trigger={}): {}",
                        tenant.getTenantId(), trigger, e.getMessage(), e);
                // deliberately continue to the next tenant rather than aborting the whole run
            }
        }
    }
}