package com.saas.budgetmanagement.service.impl;

import com.saas.budgetmanagement.dto.responseDto.CommitmentResponse;
import com.saas.budgetmanagement.enums.CommitmentStatus;
import com.saas.budgetmanagement.enums.SourceService;
import com.saas.budgetmanagement.mapper.CommitmentMapper;
import com.saas.budgetmanagement.model.Commitment;
import com.saas.budgetmanagement.repository.CommitmentRepository;
import com.saas.budgetmanagement.service.BudgetEventPublisherService;
import com.saas.budgetmanagement.service.BudgetLedgerService;
import com.saas.budgetmanagement.utility.CoaSegmentDisplayResolver;
import com.saas.budgetmanagement.utility.CoaSegmentValidator;
import com.saas.budgetmanagement.utility.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommitmentServiceImplTest {

    @Test
    void listShouldEnrichCommitmentResponsesWithCoaSegmentNames() {
        CommitmentRepository commitmentRepository = mock(CommitmentRepository.class);
        BudgetLedgerService budgetLedgerService = mock(BudgetLedgerService.class);
        CoaSegmentValidator coaSegmentValidator = mock(CoaSegmentValidator.class);
        CoaSegmentDisplayResolver displayResolver = mock(CoaSegmentDisplayResolver.class);
        BudgetEventPublisherService eventPublisherService = mock(BudgetEventPublisherService.class);
        CommitmentMapper commitmentMapper = mock(CommitmentMapper.class);
        SecurityUtil securityUtil = mock(SecurityUtil.class);

        CommitmentServiceImpl service = new CommitmentServiceImpl(
                commitmentRepository, budgetLedgerService, coaSegmentValidator, displayResolver,
                eventPublisherService, commitmentMapper, securityUtil);

        UUID tenantId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID costCenterId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID fundSourceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(securityUtil.getTenantId()).thenReturn(tenantId.toString());

        Commitment commitment = new Commitment();
        CommitmentResponse response = new CommitmentResponse();
        response.setAccountId(accountId);
        response.setCostCenterId(costCenterId);
        response.setDepartmentId(departmentId);
        response.setFundSourceId(fundSourceId);
        response.setProjectId(projectId);

        List<Commitment> commitments = new ArrayList<>();
        commitments.add(commitment);

        when(commitmentRepository.findByTenantIdAndSourceServiceAndStatus(
                eq(tenantId), eq(SourceService.AP), org.mockito.ArgumentMatchers.nullable(CommitmentStatus.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(commitments, PageRequest.of(0, 10), 1));
        when(commitmentMapper.toResponse(commitment)).thenReturn(response);

        when(displayResolver.resolveAccountNames(eq(tenantId), ArgumentMatchers.<Set<UUID>>any())).thenReturn(Map.of(accountId, "Account"));
        when(displayResolver.resolveCostCenterNames(eq(tenantId), ArgumentMatchers.<Set<UUID>>any())).thenReturn(Map.of(costCenterId, "Cost Center"));
        when(displayResolver.resolveDepartmentNames(eq(tenantId), ArgumentMatchers.<Set<UUID>>any())).thenReturn(Map.of(departmentId, "Department"));
        when(displayResolver.resolveFundSourceNames(eq(tenantId), ArgumentMatchers.<Set<UUID>>any())).thenReturn(Map.of(fundSourceId, "Fund Source"));
        when(displayResolver.resolveProjectNames(eq(tenantId), ArgumentMatchers.<Set<UUID>>any())).thenReturn(Map.of(projectId, "Project"));
        org.mockito.Mockito.doCallRealMethod().when(displayResolver).enrichCoaSegments(any(), any());

        Page<CommitmentResponse> result = service.list(SourceService.AP, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getAccountName()).isEqualTo("Account");
            assertThat(item.getCostCenterName()).isEqualTo("Cost Center");
            assertThat(item.getDepartmentName()).isEqualTo("Department");
            assertThat(item.getFundSourceName()).isEqualTo("Fund Source");
            assertThat(item.getProjectName()).isEqualTo("Project");
        });
    }
}
