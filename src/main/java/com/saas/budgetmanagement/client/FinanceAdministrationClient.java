package com.saas.budgetmanagement.client;

import com.saas.budgetmanagement.config.FeignClientConfig;
import com.saas.budgetmanagement.dto.clientDto.AccountingPeriodClientDto;
import com.saas.budgetmanagement.dto.clientDto.CoaSegmentClientDto;
import com.saas.budgetmanagement.dto.clientDto.FiscalYearClientDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "finance-administration-service", configuration = FeignClientConfig.class)
public interface FinanceAdministrationClient {

    @GetMapping("/api/finance-administration/fiscal-years/{tenantId}")
    List<FiscalYearClientDto> getAllFiscalYears(@PathVariable UUID tenantId);

    @GetMapping("/api/finance-administration/accounting-periods/{tenantId}")
    List<AccountingPeriodClientDto> getAllAccountingPeriods(@PathVariable UUID tenantId);

    @GetMapping("/api/finance-administration/coa-accounts/{tenantId}")
    List<CoaSegmentClientDto> getAllAccounts(@PathVariable UUID tenantId);

    @GetMapping("/api/finance-administration/cost-centers/{tenantId}")
    List<CoaSegmentClientDto> getAllCostCenters(@PathVariable UUID tenantId);

    @GetMapping("/api/finance-administration/departments/{tenantId}")
    List<CoaSegmentClientDto> getAllDepartments(@PathVariable UUID tenantId);

    @GetMapping("/api/finance-administration/fund-sources/{tenantId}")
    List<CoaSegmentClientDto> getAllFundSources(@PathVariable UUID tenantId);

    @GetMapping("/api/finance-administration/projects/{tenantId}")
    List<CoaSegmentClientDto> getAllProjects(@PathVariable UUID tenantId);
}