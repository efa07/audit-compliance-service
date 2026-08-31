package com.saas.auditcompliance.client;

import com.saas.auditcompliance.config.FeignClientConfig;
import com.saas.auditcompliance.dto.clientDto.OrganizationUnitClientDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "finance-administration-service", configuration = FeignClientConfig.class)
public interface FinanceAdministrationClient {

    @GetMapping("/api/finance-administration/organization-units/{tenantId}")
    List<OrganizationUnitClientDto> getAllOrganizationUnits(@PathVariable UUID tenantId);
}