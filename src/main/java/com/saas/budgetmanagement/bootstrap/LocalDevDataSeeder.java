package com.saas.budgetmanagement.bootstrap;

import com.saas.budgetmanagement.model.refcache.*;
import com.saas.budgetmanagement.repository.refcache.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalDevDataSeeder implements ApplicationRunner {

    private static final UUID TENANT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID FISCAL_YEAR_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PERIOD_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private final RefFiscalYearRepository fiscalYearRepository;
    private final RefAccountingPeriodRepository accountingPeriodRepository;
    private final RefCoaAccountRepository coaAccountRepository;
    private final RefCostCenterRepository costCenterRepository;
    private final RefDepartmentRepository departmentRepository;
    private final RefFundSourceRepository fundSourceRepository;
    private final RefProjectRepository projectRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Seeding local dev reference data for tenant {}", TENANT_ID);

        seedFiscalYear();
        seedAccountingPeriod();
        seedCoaAccount("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "5100", "Operating Expenses", "EXPENSE");
        seedCoaAccount("bbbbbbbb-cccc-dddd-eeee-ffffffffffff", "5200", "Training & Travel", "EXPENSE");
        seedCostCenter("11111111-1111-1111-1111-111111111111", "CC-100", "Finance Department");
        seedCostCenter("55555555-5555-5555-5555-555555555555", "CC-200", "HR Department");
        seedDepartment("22222222-2222-2222-2222-222222222222", "DEP-100", "Finance");
        seedDepartment("66666666-6666-6666-6666-666666666666", "DEP-200", "Human Resources");
        seedFundSource("33333333-3333-3333-3333-333333333333", "FS-100", "General Fund");
        seedFundSource("77777777-7777-7777-7777-777777777777", "FS-200", "Donor Fund");
        seedProject("44444444-4444-4444-4444-444444444444", "PRJ-100", "HQ Renovation");
        seedProject("88888888-8888-8888-8888-888888888888", "PRJ-200", "Staff Training Program");

        log.info("Local dev reference data seeding complete");
    }

    private void seedFiscalYear() {
        if (fiscalYearRepository.findByTenantIdAndExternalId(TENANT_ID, FISCAL_YEAR_ID).isPresent()) {
            return;
        }
        RefFiscalYear entity = new RefFiscalYear();
        entity.setTenantId(TENANT_ID);
        entity.setExternalId(FISCAL_YEAR_ID);
        entity.setName("FY 2026");
        entity.setStartDate(LocalDate.of(2026, 1, 1));
        entity.setEndDate(LocalDate.of(2026, 12, 31));
        entity.setOpen(true);
        entity.setSyncedAt(LocalDateTime.now());
        fiscalYearRepository.save(entity);
    }

    private void seedAccountingPeriod() {
        if (accountingPeriodRepository.findByTenantIdAndExternalId(TENANT_ID, PERIOD_ID).isPresent()) {
            return;
        }
        RefAccountingPeriod entity = new RefAccountingPeriod();
        entity.setTenantId(TENANT_ID);
        entity.setExternalId(PERIOD_ID);
        entity.setFiscalYearExternalId(FISCAL_YEAR_ID);
        entity.setPeriodNumber(1);
        entity.setPeriodName("January 2026");
        entity.setStartDate(LocalDate.of(2026, 1, 1));
        entity.setEndDate(LocalDate.of(2026, 1, 31));
        entity.setOpen(true);
        entity.setSyncedAt(LocalDateTime.now());
        accountingPeriodRepository.save(entity);
    }

    private void seedCoaAccount(String externalId, String code, String name, String type) {
        UUID id = UUID.fromString(externalId);
        if (coaAccountRepository.findByTenantIdAndExternalId(TENANT_ID, id).isPresent()) {
            return;
        }
        RefCoaAccount entity = new RefCoaAccount();
        entity.setTenantId(TENANT_ID);
        entity.setExternalId(id);
        entity.setAccountCode(code);
        entity.setAccountName(name);
        entity.setAccountType(type);
        entity.setActive(true);
        entity.setSyncedAt(LocalDateTime.now());
        coaAccountRepository.save(entity);
    }

    private void seedCostCenter(String externalId, String code, String name) {
        UUID id = UUID.fromString(externalId);
        if (costCenterRepository.findByTenantIdAndExternalId(TENANT_ID, id).isPresent()) {
            return;
        }
        RefCostCenter entity = new RefCostCenter();
        entity.setTenantId(TENANT_ID);
        entity.setExternalId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setActive(true);
        entity.setSyncedAt(LocalDateTime.now());
        costCenterRepository.save(entity);
    }

    private void seedDepartment(String externalId, String code, String name) {
        UUID id = UUID.fromString(externalId);
        if (departmentRepository.findByTenantIdAndExternalId(TENANT_ID, id).isPresent()) {
            return;
        }
        RefDepartment entity = new RefDepartment();
        entity.setTenantId(TENANT_ID);
        entity.setExternalId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setActive(true);
        entity.setSyncedAt(LocalDateTime.now());
        departmentRepository.save(entity);
    }

    private void seedFundSource(String externalId, String code, String name) {
        UUID id = UUID.fromString(externalId);
        if (fundSourceRepository.findByTenantIdAndExternalId(TENANT_ID, id).isPresent()) {
            return;
        }
        RefFundSource entity = new RefFundSource();
        entity.setTenantId(TENANT_ID);
        entity.setExternalId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setActive(true);
        entity.setSyncedAt(LocalDateTime.now());
        fundSourceRepository.save(entity);
    }

    private void seedProject(String externalId, String code, String name) {
        UUID id = UUID.fromString(externalId);
        if (projectRepository.findByTenantIdAndExternalId(TENANT_ID, id).isPresent()) {
            return;
        }
        RefProject entity = new RefProject();
        entity.setTenantId(TENANT_ID);
        entity.setExternalId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setActive(true);
        entity.setSyncedAt(LocalDateTime.now());
        projectRepository.save(entity);
    }
}