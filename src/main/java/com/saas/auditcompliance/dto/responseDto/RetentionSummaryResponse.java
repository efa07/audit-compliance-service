package com.saas.auditcompliance.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetentionSummaryResponse {

    private long eligibleForArchivalCount;
    private String note;
}