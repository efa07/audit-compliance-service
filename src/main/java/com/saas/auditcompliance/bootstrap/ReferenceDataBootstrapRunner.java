package com.saas.auditcompliance.bootstrap;

import com.saas.auditcompliance.model.refcache.RefTenant;
import com.saas.auditcompliance.repository.refcache.RefTenantRepository;
import com.saas.auditcompliance.service.ReferenceDataSyncService;
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
    private final RefTenantRepository refTenantRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running reference-data reconciliation on application startup");
        reconcileAllTenants("startup");
    }

    @Scheduled(cron = "${audit-compliance.reference-sync.reconcile-cron:0 0 * * * *}")
    public void scheduledReconcile() {
        log.info("Running scheduled reference-data reconciliation");
        reconcileAllTenants("scheduled");
    }

    private void reconcileAllTenants(String trigger) {
        List<RefTenant> tenants = refTenantRepository.findByActiveTrue();

        if (tenants.isEmpty()) {
            log.warn("No active tenants found in local RefTenant cache during {} reconciliation — " +
                    "TenantSyncListener may not have received any events yet.", trigger);
            return;
        }

        for (RefTenant tenant : tenants) {
            try {
                referenceDataSyncService.reconcileAll(tenant.getExternalId());
            } catch (Exception e) {
                log.error("Reconciliation failed for tenant {} (trigger={}): {}",
                        tenant.getExternalId(), trigger, e.getMessage(), e);
            }
        }
    }
}