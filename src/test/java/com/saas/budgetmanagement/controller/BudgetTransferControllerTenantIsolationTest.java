package com.saas.budgetmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.budgetmanagement.dto.requestDto.ApprovalDecisionRequest;
import com.saas.budgetmanagement.dto.requestDto.CreateTransferRequest;
import com.saas.budgetmanagement.enums.BudgetPlanStatus;
import com.saas.budgetmanagement.enums.TransferStatus;
import com.saas.budgetmanagement.model.BudgetAllocation;
import com.saas.budgetmanagement.model.BudgetPlan;
import com.saas.budgetmanagement.model.BudgetTransfer;
import com.saas.budgetmanagement.model.refcache.RefAccountingPeriod;
import com.saas.budgetmanagement.model.refcache.RefCoaAccount;
import com.saas.budgetmanagement.model.refcache.RefFiscalYear;
import com.saas.budgetmanagement.repository.BudgetAllocationRepository;
import com.saas.budgetmanagement.repository.BudgetPlanRepository;
import com.saas.budgetmanagement.repository.BudgetTransferRepository;
import com.saas.budgetmanagement.repository.refcache.RefAccountingPeriodRepository;
import com.saas.budgetmanagement.repository.refcache.RefCoaAccountRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BudgetTransferControllerTenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID TENANT_B = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID USER_A_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");

    private static final UUID FISCAL_YEAR_A_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PERIOD_A_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final UUID ACCOUNT_A_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private static final UUID FISCAL_YEAR_B_ID = UUID.fromString("b1111111-2222-3333-4444-555555555555");
    private static final UUID PERIOD_B_ID = UUID.fromString("b9999999-8888-7777-6666-555555555555");
    private static final UUID ACCOUNT_B_ID = UUID.fromString("baaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private static final String SENSITIVE_TENANT_B_TRANSFER_MARKER = "CONFIDENTIAL_TENANT_B_JUSTIFICATION_777";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetPlanRepository budgetPlanRepository;

    @Autowired
    private BudgetAllocationRepository budgetAllocationRepository;

    @Autowired
    private BudgetTransferRepository budgetTransferRepository;

    @Autowired
    private RefFiscalYearRepository refFiscalYearRepository;

    @Autowired
    private RefAccountingPeriodRepository refAccountingPeriodRepository;

    @Autowired
    private RefCoaAccountRepository refCoaAccountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private BudgetAllocation tenantAAllocation1;
    private BudgetAllocation tenantAAllocation2;
    private BudgetAllocation tenantBAllocation1;
    private BudgetAllocation tenantBAllocation2;
    private BudgetTransfer tenantBTransfer;

    @BeforeEach
    void setUp() {
        // Seed Tenant A ref cache
        seedRefData(TENANT_A, FISCAL_YEAR_A_ID, PERIOD_A_ID, ACCOUNT_A_ID, "5100-A");
        // Seed Tenant B ref cache
        seedRefData(TENANT_B, FISCAL_YEAR_B_ID, PERIOD_B_ID, ACCOUNT_B_ID, "5100-B");

        // Create Tenant A plan & allocations
        BudgetPlan planA = createPlan(TENANT_A, FISCAL_YEAR_A_ID);
        tenantAAllocation1 = createAllocation(TENANT_A, planA.getId(), PERIOD_A_ID, ACCOUNT_A_ID, new BigDecimal("50000.00"));
        tenantAAllocation2 = createAllocation(TENANT_A, planA.getId(), PERIOD_A_ID, ACCOUNT_A_ID, new BigDecimal("50000.00"));

        // Create Tenant B plan & allocations
        BudgetPlan planB = createPlan(TENANT_B, FISCAL_YEAR_B_ID);
        tenantBAllocation1 = createAllocation(TENANT_B, planB.getId(), PERIOD_B_ID, ACCOUNT_B_ID, new BigDecimal("100000.00"));
        tenantBAllocation2 = createAllocation(TENANT_B, planB.getId(), PERIOD_B_ID, ACCOUNT_B_ID, new BigDecimal("100000.00"));

        // Create Tenant B transfer
        BudgetTransfer transfer = new BudgetTransfer();
        transfer.setTenantId(TENANT_B);
        transfer.setFromAllocationId(tenantBAllocation1.getId());
        transfer.setToAllocationId(tenantBAllocation2.getId());
        transfer.setAmount(new BigDecimal("25000.00"));
        transfer.setStatus(TransferStatus.REQUESTED);
        transfer.setJustification(SENSITIVE_TENANT_B_TRANSFER_MARKER);
        transfer.setRequestedBy(UUID.randomUUID());
        tenantBTransfer = budgetTransferRepository.save(transfer);
    }

    private void seedRefData(UUID tenantId, UUID fyId, UUID pId, UUID accId, String code) {
        if (refFiscalYearRepository.findByTenantIdAndExternalId(tenantId, fyId).isEmpty()) {
            RefFiscalYear fy = new RefFiscalYear();
            fy.setTenantId(tenantId);
            fy.setExternalId(fyId);
            fy.setName("FY 2026 " + tenantId);
            fy.setStartDate(LocalDate.of(2026, 1, 1));
            fy.setEndDate(LocalDate.of(2026, 12, 31));
            fy.setOpen(true);
            fy.setSyncedAt(LocalDateTime.now());
            refFiscalYearRepository.save(fy);
        }
        if (refAccountingPeriodRepository.findByTenantIdAndExternalId(tenantId, pId).isEmpty()) {
            RefAccountingPeriod p = new RefAccountingPeriod();
            p.setTenantId(tenantId);
            p.setExternalId(pId);
            p.setFiscalYearExternalId(fyId);
            p.setPeriodNumber(1);
            p.setPeriodName("Jan 2026");
            p.setStartDate(LocalDate.of(2026, 1, 1));
            p.setEndDate(LocalDate.of(2026, 1, 31));
            p.setOpen(true);
            p.setSyncedAt(LocalDateTime.now());
            refAccountingPeriodRepository.save(p);
        }
        if (refCoaAccountRepository.findByTenantIdAndExternalId(tenantId, accId).isEmpty()) {
            RefCoaAccount acc = new RefCoaAccount();
            acc.setTenantId(tenantId);
            acc.setExternalId(accId);
            acc.setAccountCode(code);
            acc.setAccountName("Expense Account " + code);
            acc.setAccountType("EXPENSE");
            acc.setActive(true);
            acc.setSyncedAt(LocalDateTime.now());
            refCoaAccountRepository.save(acc);
        }
    }

    private BudgetPlan createPlan(UUID tenantId, UUID fyId) {
        BudgetPlan plan = new BudgetPlan();
        plan.setTenantId(tenantId);
        plan.setFiscalYearId(fyId);
        plan.setRequestedAmount(new BigDecimal("500000.00"));
        plan.setStatus(BudgetPlanStatus.APPROVED);
        plan.setRequestedBy(UUID.randomUUID());
        return budgetPlanRepository.save(plan);
    }

    private BudgetAllocation createAllocation(UUID tenantId, UUID planId, UUID pId, UUID accId, BigDecimal amount) {
        BudgetAllocation allocation = new BudgetAllocation();
        allocation.setTenantId(tenantId);
        allocation.setBudgetPlanId(planId);
        allocation.setPeriodId(pId);
        allocation.setAccountId(accId);
        allocation.setAllocatedAmount(amount);
        allocation.setAllocatedAt(LocalDateTime.now());
        allocation.setAllocatedBy(UUID.randomUUID());
        return budgetAllocationRepository.save(allocation);
    }

    @Test
    @DisplayName("GET /budget-transfers/{tenantB-id-in-path} as Tenant A -> expect 403 (interceptor path mismatch)")
    void getBudgetTransfers_withTenantBPathId_authenticatedAsTenantA_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/budget-management/budget-transfers/" + TENANT_B)
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_TRANSFER_MARKER))))
                .andExpect(content().string(not(containsString(tenantBTransfer.getId().toString()))));
    }

    @Test
    @DisplayName("GET /budget-transfers/{tenantA-path}/{tenantB-transfer-id} as Tenant A -> expect 404")
    void getBudgetTransferById_tenantBTransferWithTenantAPath_authenticatedAsTenantA_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/budget-management/budget-transfers/" + TENANT_A + "/" + tenantBTransfer.getId())
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_TRANSFER_MARKER))));
    }

    @Test
    @DisplayName("POST /budget-transfers/{tenantA-path} with Tenant B source allocation as Tenant A -> expect 404 (source allocation not found)")
    void requestTransfer_withTenantBSourceAllocation_authenticatedAsTenantA_shouldReturn404() throws Exception {
        CreateTransferRequest request = new CreateTransferRequest();
        request.setFromAllocationId(tenantBAllocation1.getId());
        request.setToAllocationId(tenantAAllocation2.getId());
        request.setAmount(new BigDecimal("1000.00"));
        request.setJustification("Attempting cross-tenant money movement");

        mockMvc.perform(post("/api/budget-management/budget-transfers/" + TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_TRANSFER_MARKER))));

        // Assert Tenant B allocation amount was untouched
        BudgetAllocation updatedBAllocation = budgetAllocationRepository.findById(tenantBAllocation1.getId()).orElseThrow();
        assertThat(updatedBAllocation.getAllocatedAmount()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("POST /budget-transfers/{tenantA-path} with Tenant B target allocation as Tenant A -> expect 404 (target allocation not found)")
    void requestTransfer_withTenantBTargetAllocation_authenticatedAsTenantA_shouldReturn404() throws Exception {
        CreateTransferRequest request = new CreateTransferRequest();
        request.setFromAllocationId(tenantAAllocation1.getId());
        request.setToAllocationId(tenantBAllocation2.getId());
        request.setAmount(new BigDecimal("1000.00"));
        request.setJustification("Attempting cross-tenant money movement to B");

        mockMvc.perform(post("/api/budget-management/budget-transfers/" + TENANT_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_TRANSFER_MARKER))));

        // Assert Tenant A source allocation amount was untouched
        BudgetAllocation updatedAAllocation = budgetAllocationRepository.findById(tenantAAllocation1.getId()).orElseThrow();
        assertThat(updatedAAllocation.getAllocatedAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("POST /budget-transfers/{tenantA-path}/{tenantB-transfer-id}/approve as Tenant A -> expect 404 and transfer status untouched")
    void approveBudgetTransfer_tenantBTransferWithTenantAPath_authenticatedAsTenantA_shouldReturn404() throws Exception {
        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        request.setComments("Unauthorized transfer approval");

        mockMvc.perform(post("/api/budget-management/budget-transfers/" + TENANT_A + "/" + tenantBTransfer.getId() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder
                                .claim("tenantId", TENANT_A.toString())
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .subject(USER_A_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(SENSITIVE_TENANT_B_TRANSFER_MARKER))));

        // Assert Tenant B transfer status is still REQUESTED
        BudgetTransfer updatedTransfer = budgetTransferRepository.findById(tenantBTransfer.getId()).orElseThrow();
        assertThat(updatedTransfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }
}
