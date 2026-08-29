package com.saas.auditcompliance.dto.responseDto;

import com.saas.auditcompliance.enums.ReviewStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ComplianceViolationReviewResponse extends BaseResponse {

    private UUID violationId;
    private ReviewStatus status;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private String resolutionNotes;
}