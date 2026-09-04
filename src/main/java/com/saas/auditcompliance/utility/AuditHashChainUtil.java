package com.saas.auditcompliance.utility;

import com.saas.auditcompliance.dto.common.AuditEventIngestCommand;
import com.saas.auditcompliance.model.AuditRecord;
import com.saas.auditcompliance.repository.AuditRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuditHashChainUtil {

    private final AuditRecordRepository auditRecordRepository;

    /**
     * Must be called from within the SAME transaction that will save the resulting
     * AuditRecord — see the note on AuditRecordRepository.findForUpdateLatestByTenantIdAndSourceService.
     */
    public ChainLink computeNext(AuditEventIngestCommand command) {
        Optional<AuditRecord> previous = auditRecordRepository
                .findForUpdateLatestByTenantIdAndSourceService(command.getTenantId(), command.getSourceService());

        String previousHash = previous.map(AuditRecord::getRecordHash).orElse(null);
        String canonicalContent = buildCanonicalContent(
                command.getTenantId().toString(), command.getSourceService().name(),
                command.getSourceEventId(), command.getEventType(),
                command.getOccurredAt().toString(), command.getRawPayload());

        String toHash = canonicalContent + "|" + (previousHash != null ? previousHash : "GENESIS");
        return new ChainLink(sha256(toHash), previousHash);
    }

    /**
     * Recomputes what this record's recordHash SHOULD be, purely from its own persisted
     * fields and its own stored previousRecordHash — then compares against the recordHash
     * actually stored on the row. A mismatch means either this record's own content or its
     * previousRecordHash pointer was altered after the fact. This does NOT re-walk the whole
     * chain back to genesis — see the class-level note below for what that means.
     */
    public boolean verify(AuditRecord record) {
        String canonicalContent = buildCanonicalContent(
                record.getTenantId().toString(), record.getSourceService().name(),
                record.getSourceEventId(), record.getEventType(),
                record.getOccurredAt().toString(), record.getPayload());

        String toHash = canonicalContent + "|" +
                (record.getPreviousRecordHash() != null ? record.getPreviousRecordHash() : "GENESIS");

        return sha256(toHash).equals(record.getRecordHash());
    }

    private String buildCanonicalContent(String tenantId, String sourceService, String sourceEventId,
                                          String eventType, String occurredAt, String rawPayload) {
        return String.join("|", tenantId, sourceService, sourceEventId, eventType, occurredAt, rawPayload);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record ChainLink(String recordHash, String previousRecordHash) {}
}