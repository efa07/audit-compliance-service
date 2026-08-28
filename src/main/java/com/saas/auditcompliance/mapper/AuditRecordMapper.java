package com.saas.auditcompliance.mapper;

import com.saas.auditcompliance.dto.responseDto.AuditRecordResponse;
import com.saas.auditcompliance.model.AuditRecord;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditRecordMapper {

    AuditRecordResponse toResponse(AuditRecord entity);
}