package com.saas.budgetmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.budgetmanagement.dto.requestDto.CreateAllocationRequest;
import com.saas.budgetmanagement.enums.BudgetPlanStatus;
import com.saas.budgetmanagement.model.BudgetAllocation;
import com.saas.budgetmanagement.model.BudgetPlan;
import com.saas.budgetmanagement.model.refcache.*;
import com.saas.budgetmanagement.repository.BudgetAllocationRepository;
import com.saas.budgetmanagement.repository.BudgetPlanRepository;
import com.saas.budgetmanagement.repository.refcache.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BudgetAllocationControllerTenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID TENANT_B = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID USER_A_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");

    private static final UUID FISCAL_YEAR_B_ID = UUID.fromString("b1111111-2222-3333-4444-555555555555");
    private static final UUID PERIOD_B_ID = UUID.fromString("b9999999-8888-7777-6666-555555555555");
    private static final UUID ACCOUNT_B_ID = UUID.fromString("baaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private static final String SENSITIVE_TENANT_B_ALLOCATION_MARKER = "88888888.88";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetPlanRepository budgetPlanRepository;

    @Autowired
    private BudgetAllocationRepository budgetAllocationRepository;

    @Autowired
    private RefFiscalYearRepository refFiscalYearRepository;

    @Autowired
    private RefAccountingPeriodRepository refAccountingPeriodRepository;

    @Autowired
    private RefCoaAccountRepository refCoaAccountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private BudgetPlan tenantBPlan;
    private BudgetAllocation tenantBAllocation;

    @BeforeEach
    void setUp() {
        // Seed Fiscal Year for Tenant B
        if (refFiscalYearRepository.findByTenantIdAndExternalId(TENANT_B, FISCAL_YEAR_B_ID).isEmpty()) {
            RefFiscalYear fy = new RefFiscalYear();
            fy.setTenantId(TENANT_B);
            fy.setExternalId(FISCAL_YEAR_B_ID);
            fy.setName("FY 2026 Tenant B");
            fy.setStartDate(LocalDate.of(2026, 1, 1));
            fy.setEndDate(LocalDate.of(2026, 12, 31));
            fy.setOpen(true);
            fy.setSyncedAt(LocalDateTime.now());
            refFiscalYearRepository.save(fy);
        }

        // Seed Period for Tenant B
        if (refAccountingPeriodRepository.findByTenantIdAndExternalId(TENANT_B, PERIOD_B_ID).isEmpty()) {
            RefAccountingPeriod p = new RefAccountingPeriod();
            p.setTenantId(TENANT_B);
            p.setExternalId(PERIOD_B_ID);
            p.setFiscalYearExternalId(FISCAL_YEAR_B_ID);
            p.setPeriodNumber(1);
            p.setPeriodName("Jan 2026 Tenant B");
            p.setStartDate(LocalDate.of(2026, 1, 1));
            p.setEndDate(LocalDate.of(2026, 1, 31));
            p.setOpen(true);
            p.setSyncedAt(LocalDateTime.now());
            refAccountingPeriodRepository.save(p);
        }

        // Seed Account for Tenant B
        if (refCoaAccountRepository.findByTenantIdAndExternalId(TENANT_B, ACCOUNT_B_ID).isEmpty()) {
            RefCoaAccount acc = new RefCoaAccount();
            acc.setTenantId(TENANT_B);
            acc.setExternalId(ACCOUNT_B_ID);
            acc.setAccountCode("5100-B");
            acc.setAccountName("Tenant B Expense Account");
            acc.setAccountType("EXPENSE");
            acc.setActive(true);
            acc.setSyncedAt(LocalDateTime.now());
            refCoaAccountRepository.save(acc);
        }

        // Create Tenant B's Approved BudgetPlan
        BudgetPlan plan = new BudgetPlan();
        plan.setTenantId(TENANT_B);
        plan.setFiscalYearId(FISCAL_YEAR_B_ID);
        plan.setRequestedAmount(new BigDecimal("1000000.00"));
        plan.setStatus(BudgetPlanStatus.APPROVED);
        plan.setRequestedBy(UUID.randomUUID());
        tenantBPlan = budgetPlanRepository.save(plan);

        // Create Tenant B's BudgetAllocation
        BudgetAllocation allocation = new BudgetAllocation();
        allocation.setTenantId(TENANT_B);
        allocation.setBudgetPlanId(tenantBPlan.getId());
        allocation.setPeriodId(PERIOD_B_ID);
        allocation.setAccountId(ACCOUNT_B_ID);
        allocation.setAllocatedAmount(new BigDecimal(SENSITIVE_TENANT_B_ALLOCATION_MARKER));
        allocation.setAllocatedAt(LocalDateTime.now());
        allocation.setAllocatedBy(UUID.randomUUID());
        tenantBAllocation = budgetAllocationRepository.save(allocation);
    }

    @Test
    @DisplayName("GET /budget-allocations/{tenantB-id-in-path} as Tenant A -> expect 403 (interceptor path mismatch)")
    void getBudgetAllocations_withTenantBPathId_authenticatedAsTenantA_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/budget-management/budget-allocations/" + TENANT_B)
                        .param("budgetPlanId", tenantBPlan.getId().toString())
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_ALLOCATION_MARKER))))
                .andExpect(content().string(not(containsString(tenantBAllocation.getId().toString()))));
    }

    @Test
    @DisplayName("GET /budget-allocations/{tenantA-path}/{tenantB-allocation-id} as Tenant A -> expect 404")
    void getBudgetAllocationById_tenantBAllocationWithTenantAPath_authenticatedAsTenantA_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/budget-management/budget-allocations/" + TENANT_A + "/" + tenantBAllocation.getId())
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_ALLOCATION_MARKER))));
    }

    @Test
    @DisplayName("GET /budget-allocations/{tenantA-path}?budgetPlanId={tenantBPlanId} as Tenant A -> expect empty result set")
    void listBudgetAllocations_passingTenantBBudgetPlanId_authenticatedAsTenantA_shouldReturnEmptyPage() throws Exception {
        mockMvc.perform(get("/api/budget-management/budget-allocations/" + TENANT_A)
                        .param("budgetPlanId", tenantBPlan.getId().toString())
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_ALLOCATION_MARKER))));
    }

    @Test
    @DisplayName("POST /budget-allocations/{tenantA-path} referencing Tenant B's budgetPlanId as Tenant A -> expect 404")
    void createBudgetAllocation_referencingTenantBBudgetPlanId_authenticatedAsTenantA_shouldReturn404() throws Exception {
        CreateAllocationRequest request = new CreateAllocationRequest();
        request.setBudgetPlanId(tenantBPlan.getId());
        request.setPeriodId(PERIOD_B_ID);
        request.setAccountId(ACCOUNT_B_ID);
        request.setAmount(new BigDecimal("500.00"));

        mockMvc.perform(post("/api/budget-management/budget-allocations/" + TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_ALLOCATION_MARKER))));
    }
}
