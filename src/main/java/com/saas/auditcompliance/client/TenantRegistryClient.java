package com.saas.budgetmanagement.client;

import com.saas.auditcompliance.config.FeignClientConfig;
import com.saas.budgetmanagement.dto.clientDto.TenantClientDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "auth-service", configuration = FeignClientConfig.class)
public interface TenantRegistryClient {

    @GetMapping("/api/auth/tenants")
    List<TenantClientDto> getActiveTenants();
}