package com.saas.auditcompliance.dto.clientDto;

import lombok.Data;

import java.util.UUID;

@Data
public class OrganizationUnitClientDto {
    private UUID externalId;
    private String code;
    private String name;
    private UUID parentExternalId;
    private boolean active;
}