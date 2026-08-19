package com.saas.budgetmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.budgetmanagement.dto.requestDto.ApprovalDecisionRequest;
import com.saas.budgetmanagement.dto.requestDto.BudgetLineItemRequest;
import com.saas.budgetmanagement.dto.requestDto.UpdateBudgetPlanRequest;
import com.saas.budgetmanagement.enums.BudgetPlanStatus;
import com.saas.budgetmanagement.model.BudgetPlan;
import com.saas.budgetmanagement.model.refcache.RefFiscalYear;
import com.saas.budgetmanagement.repository.BudgetPlanRepository;
import com.saas.budgetmanagement.repository.refcache.RefFiscalYearRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BudgetPlanControllerTenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID TENANT_B = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID USER_A_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");

    private static final UUID FISCAL_YEAR_B_ID = UUID.fromString("b1111111-2222-3333-4444-555555555555");
    private static final String SENSITIVE_TENANT_B_PLAN_MARKER = "CONFIDENTIAL_TENANT_B_PLAN_DATA_99999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetPlanRepository budgetPlanRepository;

    @Autowired
    private RefFiscalYearRepository refFiscalYearRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private BudgetPlan tenantBPlan;

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

        // Create a real BudgetPlan owned by Tenant B using repository layer
        BudgetPlan plan = new BudgetPlan();
        plan.setTenantId(TENANT_B);
        plan.setFiscalYearId(FISCAL_YEAR_B_ID);
        plan.setRequestedAmount(new BigDecimal("999999.00"));
        plan.setStatus(BudgetPlanStatus.SUBMITTED);
        plan.setRequestedBy(UUID.randomUUID());
        plan.setRejectionReason(SENSITIVE_TENANT_B_PLAN_MARKER);
        tenantBPlan = budgetPlanRepository.save(plan);
    }

    @Test
    @DisplayName("GET /budget-plans/{tenantB-id-in-path} as Tenant A -> expect 403 (interceptor path mismatch)")
    void getBudgetPlans_withTenantBPathId_authenticatedAsTenantA_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/budget-management/budget-plans/" + TENANT_B)
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("999999.00"))))
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_PLAN_MARKER))))
                .andExpect(content().string(not(containsString(tenantBPlan.getId().toString()))));
    }

    @Test
    @DisplayName("GET /budget-plans/{tenantA-path}/{tenantB-plan-id} as Tenant A -> expect 404 (repository scoping check)")
    void getBudgetPlanById_tenantBPlanWithTenantAPath_authenticatedAsTenantA_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/budget-management/budget-plans/" + TENANT_A + "/" + tenantBPlan.getId())
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("999999.00"))))
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_PLAN_MARKER))));
    }

    @Test
    @DisplayName("POST /budget-plans/{tenantA-path}/{tenantB-plan-id}/approve as Tenant A -> expect 404 and no state change")
    void approveBudgetPlan_tenantBPlanWithTenantAPath_authenticatedAsTenantA_shouldReturn404() throws Exception {
        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        request.setComments("Unauthorized approval attempt by Tenant A");

        mockMvc.perform(post("/api/budget-management/budget-plans/" + TENANT_A + "/" + tenantBPlan.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("999999.00"))))
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_PLAN_MARKER))));

        // Assert plan status remained SUBMITTED in DB
        BudgetPlan updatedPlan = budgetPlanRepository.findById(tenantBPlan.getId()).orElseThrow();
        assertThat(updatedPlan.getStatus()).isEqualTo(BudgetPlanStatus.SUBMITTED);
    }

    @Test
    @DisplayName("PUT /budget-plans/{tenantA-path}/{tenantB-plan-id} as Tenant A -> expect 404")
    void updateBudgetPlan_tenantBPlanWithTenantAPath_authenticatedAsTenantA_shouldReturn404() throws Exception {
        UpdateBudgetPlanRequest request = new UpdateBudgetPlanRequest();
        request.setRequestedAmount(new BigDecimal("111.00"));
        request.setLines(List.of());

        mockMvc.perform(put("/api/budget-management/budget-plans/" + TENANT_A + "/" + tenantBPlan.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("999999.00"))));
    }
}
