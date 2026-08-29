package com.saas.auditcompliance.mapper;

import com.saas.auditcompliance.dto.eventDto.outbound.ComplianceViolationDetectedEvent;
import com.saas.auditcompliance.model.ComplianceViolation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ComplianceViolationMapper {

    ComplianceViolationDetectedEvent toDetectedEvent(ComplianceViolation entity);
}