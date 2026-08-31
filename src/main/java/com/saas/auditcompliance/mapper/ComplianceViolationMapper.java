package com.saas.auditcompliance.mapper;

import com.saas.auditcompliance.dto.eventDto.outbound.ComplianceViolationDetectedEvent;
import com.saas.auditcompliance.dto.responseDto.ComplianceViolationResponse;
import com.saas.auditcompliance.model.ComplianceViolation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ComplianceViolationMapper {

    ComplianceViolationResponse toResponse(ComplianceViolation entity);

    ComplianceViolationDetectedEvent toDetectedEvent(ComplianceViolation entity);
}