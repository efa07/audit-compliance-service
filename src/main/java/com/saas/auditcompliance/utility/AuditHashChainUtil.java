package com.saas.auditcompliance.utility;

import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.model.AuditRecord;
import com.saas.auditcompliance.repository.AuditRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditHashChainUtil {

    private final AuditRecordRepository auditRecordRepository;

    public ChainLink computeNext(UUID tenantId, SourceService sourceService, String canonicalContent) {
        Optional<AuditRecord> previous = auditRecordRepository
                .findTopByTenantIdAndSourceServiceOrderByCreatedAtDesc(tenantId, sourceService);

        String previousHash = previous.map(AuditRecord::getRecordHash).orElse(null);
        String toHash = canonicalContent + "|" + (previousHash != null ? previousHash : "GENESIS");

        return new ChainLink(sha256(toHash), previousHash);
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