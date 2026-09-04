package com.saas.auditcompliance.integration;

import com.saas.auditcompliance.model.AuditRecord;
import com.saas.auditcompliance.repository.AuditRecordRepository;
import com.saas.auditcompliance.repository.ComplianceViolationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditRecordImmutabilityTest {

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Autowired
    private ComplianceViolationRepository complianceViolationRepository;

    @Test
    void auditRecordRepository_hasNoDeleteCapability() {
        assertNoDeleteMethods(AuditRecordRepository.class);
    }

    @Test
    void complianceViolationRepository_hasNoDeleteCapability() {
        assertNoDeleteMethods(ComplianceViolationRepository.class);
    }

    private void assertNoDeleteMethods(Class<?> repositoryInterface) {
        boolean hasDeleteMethod = Arrays.stream(repositoryInterface.getMethods())
                .anyMatch((Method m) -> m.getName().toLowerCase().contains("delete"));

        assertThat(hasDeleteMethod)
                .as("%s must never expose a delete method — this is a structural immutability guarantee, " +
                        "not a convention. If this fails, someone changed the repository to extend " +
                        "JpaRepository instead of the bare Repository marker interface.", repositoryInterface.getSimpleName())
                .isFalse();
    }

    @Test
    @Transactional
    void updatableFalseColumns_areNotChangedByASubsequentSave() {
        AuditRecord original = new AuditRecord();
        original.setTenantId(UUID.randomUUID());
        original.setSourceService(com.saas.auditcompliance.enums.SourceService.BUDGET_MANAGEMENT);
        original.setSourceEventId(UUID.randomUUID().toString());
        original.setEventType("budget.plan.approved");
        original.setOccurredAt(LocalDateTime.now());
        original.setPayload("{\"original\":true}");
        original.setRecordHash("dummy-hash-for-test");
        original.setRetainUntil(LocalDateTime.now().plusYears(10));

        AuditRecord saved = auditRecordRepository.save(original);
        UUID id = saved.getId();

        // Fetch a fresh managed instance and attempt to mutate an updatable=false field.
        AuditRecord fetched = auditRecordRepository.findByTenantIdAndId(saved.getTenantId(), id).orElseThrow();
        fetched.setPayload("{\"tampered\":true}");
        auditRecordRepository.save(fetched);

        // Re-fetch independently — if updatable=false is actually enforced, this must still
        // show the ORIGINAL payload, not the tampered one, regardless of what the in-memory
        // object claims after save().
        AuditRecord reFetched = auditRecordRepository.findByTenantIdAndId(saved.getTenantId(), id).orElseThrow();

        assertThat(reFetched.getPayload())
                .as("payload is updatable=false — a save() after mutation must not change the stored value")
                .isEqualTo("{\"original\":true}");
    }
}
