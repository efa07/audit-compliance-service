package com.saas.auditcompliance.dto.requestDto;

import com.saas.auditcompliance.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewViolationRequest {

    @NotNull(message = "status is required")
    private ReviewStatus status;

    private String resolutionNotes;
}