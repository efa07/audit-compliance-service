package com.saas.auditcompliance.integration;

import com.saas.auditcompliance.dto.common.AuditEventIngestCommand;
import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.model.AuditRecord;
import com.saas.auditcompliance.repository.AuditRecordRepository;
import com.saas.auditcompliance.service.AuditRecordService;
import com.saas.auditcompliance.utility.AuditHashChainUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HashChainIntegrityTest {

    @Autowired
    private AuditRecordService auditRecordService;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Autowired
    private AuditHashChainUtil hashChainUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void secondRecordInAChain_linksToTheFirstRecordsHash() {
        UUID tenantId = UUID.randomUUID();

        AuditRecord first = ingest(tenantId, "evt-1");
        AuditRecord second = ingest(tenantId, "evt-2");

        assertThat(first.getPreviousRecordHash()).isNull(); // genesis record
        assertThat(second.getPreviousRecordHash()).isEqualTo(first.getRecordHash());
    }

    @Test
    @Transactional
    void verify_returnsTrueForAnUntamperedRecord() {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = ingest(tenantId, "evt-untampered");

        assertThat(hashChainUtil.verify(record)).isTrue();
    }

    @Test
    @Transactional
    void verify_detectsTamperingWithThePayloadAfterTheFact() {
        UUID tenantId = UUID.randomUUID();
        AuditRecord record = ingest(tenantId, "evt-to-tamper");

        // Bypass the application entirely — simulate someone editing the row directly
        // in the database, which is exactly the scenario hash-chaining exists to catch.
        jdbcTemplate.update("UPDATE audit_records SET payload = ? WHERE id = UUID_TO_BIN(?)",
                "{\"maliciously\":\"altered\"}", record.getId().toString());

        AuditRecord tampered = auditRecordRepository
                .findByTenantIdAndId(tenantId, record.getId()).orElseThrow();

        assertThat(hashChainUtil.verify(tampered))
                .as("a record whose stored content no longer matches its own recordHash must fail verification")
                .isFalse();
    }

    private AuditRecord ingest(UUID tenantId, String sourceEventId) {
        AuditEventIngestCommand command = new AuditEventIngestCommand(
                tenantId, SourceService.BUDGET_MANAGEMENT, sourceEventId,
                "budget.plan.approved", LocalDateTime.now(), null, null,
                "plan", UUID.randomUUID().toString(), "{\"amount\":1000}");

        auditRecordService.ingest(command);

        return auditRecordRepository.findByTenantIdAndSourceServiceAndSourceEventId(
                tenantId, SourceService.BUDGET_MANAGEMENT, sourceEventId).orElseThrow();
    }
}
