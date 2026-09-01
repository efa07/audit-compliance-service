package com.saas.auditcompliance.client;

import com.saas.auditcompliance.config.FeignClientConfig;
import com.saas.auditcompliance.dto.clientDto.TenantClientDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * FALLBACK PATH ONLY — the primary tenant-discovery mechanism is TenantSyncListener,
 * consuming organization-service's create-tenant-queue/delete-tenant-queue events.
 *
 * This client currently requires a valid admin-role JWT (per organization-service's
 * TenantController: @PreAuthorize("hasRole('admin')") on getAllTenants()), which a
 * scheduled/background caller structurally does not have. Do not wire this into
 * ReferenceDataBootstrapRunner's primary path until service-to-service (machine)
 * authentication is resolved with the Keycloak/security team.
 */
@FeignClient(name = "organization-service", configuration = FeignClientConfig.class)
public interface TenantRegistryClient {

    @GetMapping("/api/organization/tenants/get-all")
    List<TenantClientDto> getActiveTenants();
}