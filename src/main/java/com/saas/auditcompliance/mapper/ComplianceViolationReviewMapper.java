package com.saas.auditcompliance.mapper;

import com.saas.auditcompliance.dto.responseDto.ComplianceViolationReviewResponse;
import com.saas.auditcompliance.model.ComplianceViolationReview;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ComplianceViolationReviewMapper {

    ComplianceViolationReviewResponse toResponse(ComplianceViolationReview entity);
}