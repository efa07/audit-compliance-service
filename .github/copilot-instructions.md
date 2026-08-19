# Project Context: Budget Management Service

Paste this into Copilot Chat (or add it as a `.github/copilot-instructions.md` / workspace context file) so it has full background before generating code. This is reference context, not a task list — refer back to it, don't execute it top to bottom.

---

## 1. What this project is

This is a **production SaaS-based ERP system** intended to be hosted for **national-level organizations in Ethiopia**. This is a real deployment target, not a training/intern exercise — treat security, data handling, and reliability requirements as hard constraints, not nice-to-haves. This service — **Budget Management Service** — is one of ten planned microservices inside the system's **Finance module**, alongside Finance Administration, General Ledger, Cash & Banking, Accounts Payable, Accounts Receivable, Employee Finance, Asset & Inventory Finance, Financial Reporting, and Audit & Compliance.

Every service in this ERP is cloned from a shared `startup-service` template and must follow the same folder structure, naming conventions, and config setup so that all module teams stay consistent and services can be maintained interchangeably.

**Stack:** Spring Boot · RabbitMQ · PostgreSQL · Keycloak (auth) · Feign (inter-service calls) · API Gateway (Spring Cloud Gateway, route-per-service) · MapStruct · Flyway.

### Compliance & regulatory constraints (Ethiopia-specific)

Two constraints apply across this entire system, not just this service. Copilot should keep both in mind for any infrastructure, data-handling, or logging code it suggests:

- **Data residency:** All data — including database storage, backups, and any caching layer — must stay within Ethiopia. Do not suggest or default to foreign cloud regions, third-party SaaS integrations, or external logging/monitoring services that would transmit or store data outside the country. If a task implies choosing infrastructure (a queue broker, object storage, a managed database), flag the residency requirement rather than assuming a default cloud provider/region.
- **INSA cybersecurity directives/standards:** This system must align with Ethiopia's INSA (Information Network Security Administration) cybersecurity requirements. The specific directive text isn't included in this document — treat this as a reminder to apply defense-in-depth practices consistent with a national-security-adjacent deployment (strict input validation, least-privilege access, encryption at rest and in transit, comprehensive audit logging of every state-changing action, no hardcoded secrets, secure defaults on every config) and to flag anywhere a design choice might need sign-off against the actual INSA standard rather than silently picking the common industry default. When in doubt on a specific control, ask rather than assume compliance.

---

## 2. What Budget Management Service is responsible for

Budget Management is the **planning and control layer** of the finance module. It owns budget plans, approvals, allocations, transfers, revisions, commitments (encumbrances), and the budget ledger that tracks allocated vs. committed vs. actual vs. available amounts. It does **not** own accounting entries, cash, vendor, payroll, or asset data — it reads signals from the services that do, and answers a single core question other services need: *"is there budget available for this spend?"*

In-scope responsibilities (from the org's finance microservices spec):
- Budget Planning, Request, Approval
- Budget Allocation, Transfer, Transfer Approval
- Budget Revision
- Budget Utilization, Budget Control
- Cash Requirement Estimate, Cash Requirement Estimate Approval
- Commitment Registration, Adjustment, Liquidation
- Budget Ledger
- Budget Reports

Explicitly **out of scope** (owned by other services, Budget Management only reads/reacts): Chart of Accounts, Fiscal Year/Period, GL posting, supplier invoices, payroll, asset acquisition, cash position, financial statements, audit trail storage.

---

## 3. Cross-service dependencies (who this service talks to and why)

| Service | Relationship | What's needed |
|---|---|---|
| Finance Administration | hard, bootstrap | Fiscal Year, Accounting Period, Chart of Accounts (account/cost center/department/fund source/project), Financial Settings, Approval Workflow Config — cached locally, never queried live for every validation |
| General Ledger | hard, operational | Posted actuals by period/COA, period open/closed status — feeds the "actual" side of the budget ledger |
| Accounts Payable | hard, control | Supplier invoice commitments — feeds commitment registration/liquidation |
| Employee Finance | hard, control | Payroll postings, advance/per diem/travel claim approvals — feeds commitment registration for personnel-related budget lines |
| Asset & Inventory Finance | soft, commitment | Asset acquisition commitments — feeds capital budget line commitments |
| Cash & Banking | soft, informational | Cash position/forecast — advisory input to Cash Requirement Estimates only, never blocking |
| Accounts Receivable | soft, informational | Revenue collection — only relevant for receipt-funded budgets |
| Financial Reporting | downstream consumer | Budget Management publishes approved/allocated/revised/utilization data; never queried synchronously |
| Audit & Compliance | downstream consumer | Budget Management publishes approval history, revisions, control violations as events; pure subscriber, not queried |

**Data ownership rule to enforce everywhere in code:** Budget Management owns the plan and the control ledger. It never owns or writes the transactions that consume the budget (invoices, payroll, asset postings) — those stay in their source services.

---

## 4. Integration patterns

- **Synchronous (REST via Feign):** Only for calls that block a user-facing or transaction-blocking decision — validating fiscal year/period/COA against Finance Administration, and the one heavily-used cross-service contract: `POST /budget-availability/check`, which AP/Employee Finance/Asset services call inline before finalizing a commitment. Keep this endpoint fast — it reads from the local `budget_ledger` table, never live from GL.
- **Asynchronous (RabbitMQ):** Everything else — budget approvals, allocations, transfers, revisions, commitment registration/liquidation, and all fan-out to Financial Reporting and Audit & Compliance. If a process can tolerate seconds-to-minutes of lag, it's an event, not a Feign call.
- **Reference-data bootstrap from Finance Administration:** event-driven local cache (subscribe to Finance Administration's fiscal-year/period/COA/settings events, replicate into `ref_*` tables) + REST fallback on cache miss or startup, with periodic reconciliation. Don't make every budget validation a network call.

---

## 5. RabbitMQ event contracts

Routing key convention: `budget.<entity>.<action>` for what this service publishes; consumed events follow each source service's own `<service>.<entity>.<action>` convention. Every message uses a shared envelope: `eventId`, `eventType`, `occurredAt`, `tenantId`, `correlationId`, `version`, plus a `payload` object.

**Publishes:**
- `budget.plan.created`, `budget.plan.approved`, `budget.plan.rejected`
- `budget.allocation.created`
- `budget.transfer.requested`, `budget.transfer.approved`
- `budget.revision.created`
- `budget.commitment.registered`, `budget.commitment.liquidated`
- `budget.control.violation`
- `budget.utilization.snapshot`

**Subscribes to:**
- Finance Administration: `finance-admin.fiscal-year.*`, `finance-admin.period.*`, `finance-admin.coa.*`, `finance-admin.settings.*`
- General Ledger: `general-ledger.actual.posted`, `general-ledger.period.closed`
- Accounts Payable: `accounts-payable.invoice.committed`, `accounts-payable.commitment.reversed`
- Employee Finance: `employee-finance.payroll.posted`, `employee-finance.advance.approved`, `employee-finance.travel-claim.approved`
- Asset & Inventory Finance: `asset-finance.acquisition.committed`
- Cash & Banking: `cash-banking.position.updated`

**Reliability requirements:** publisher confirms, manual ack on listeners, dead-letter exchange/queue per consumer queue, idempotent handlers keyed on `eventId` (at-least-once delivery), transactional outbox pattern for anything this service publishes (write to `outbox_event` table in the same DB transaction as the domain change, separate relay process publishes to RabbitMQ).

**Note:** the actual exchange/queue names, connection settings, and RabbitMQ bean wiring live in `config/RabbitMqConfig` — that file is already correct for this project and must not be modified. Use it, don't recreate it.

---

## 6. Database entities (domain model)

Every entity extends the template's `Base` class (shared ID/timestamp fields) and uses `BaseEntityListener` for audit fields — don't redefine these, just extend them.

**Core tables:** `BudgetPlan` (1—N `BudgetLine`), `BudgetLine`, `BudgetAllocation`, `BudgetTransfer`, `BudgetRevision`, `Commitment`, `BudgetLedger` (the allocated/committed/actual/available rollup — backs the availability-check endpoint), `CashRequirementEstimate`, `BudgetControlViolationLog`, `OutboxEvent`.

**Reference-cache tables** (read-only replicas from Finance Administration, under `model/refcache/`): `RefFiscalYear`, `RefAccountingPeriod`, `RefCoaAccount`, `RefCostCenter`, `RefDepartment`, `RefFundSource`, `RefProject` — each carries an `external_id` (the source-of-truth ID in Finance Administration) plus a `synced_at` timestamp. Keep these narrow; they're a cache, not a full mirror. Domain tables reference these by `external_id`, never by a live cross-service foreign key.

**Enums:** `BudgetPlanStatus` (DRAFT/SUBMITTED/APPROVED/REJECTED), `TransferStatus` (REQUESTED/APPROVED/REJECTED), `CommitmentStatus` (REGISTERED/ADJUSTED/LIQUIDATED), `SourceService` (AP/EMPLOYEE_FINANCE/ASSET_FINANCE), `ControlMode` (BLOCKED/WARNED).

Migrations are managed via Flyway under `resources/db/migration/` — never rely on `ddl-auto` to generate schema.

---

## 7. API surface

URL convention for this org: `/api/budget-management/{controller-name-plural}/{tenantId}`, with `tenantId` always resolved via `SecurityUtil.getTenantId()` — never trust a manually-supplied tenant ID without that check.

- `BudgetPlanController` → `/budget-plans` (CRUD + `/submit`, `/approve`, `/reject`)
- `BudgetAllocationController` → `/budget-allocations`
- `BudgetTransferController` → `/budget-transfers` (+ `/approve`, `/reject`)
- `BudgetRevisionController` → `/budget-revisions`
- `CommitmentController` → `/commitments` (+ `/adjust`)
- `BudgetLedgerController` → `/budget-ledger` (+ `/utilization`)
- `BudgetAvailabilityController` → `/budget-availability/check` (the key synchronous cross-service contract)
- `CashRequirementEstimateController` → `/cash-requirement-estimates` (+ `/approve`)

The API Gateway route for this service (already registered, don't recreate):
```yaml
- id: budget-management-service
  uri: lb://budget-management-service
  predicates:
    - Path=/api/budget-management/**
```

---

## 8. Service layer responsibilities

One interface + impl pair per responsibility, impls under `service/impl/`:
- `BudgetPlanService` — plan lifecycle, DRAFT→SUBMITTED→APPROVED/REJECTED state machine
- `BudgetApprovalService` — evaluates approval workflow rules from the Finance Administration cache, records decisions, publishes events
- `BudgetAllocationService` — creates allocations, validates COA combinations against the reference cache
- `BudgetTransferService` — validates source/target allocation compatibility, manages transfer approval state
- `BudgetRevisionService` — records revisions, recalculates downstream allocation impact
- `CommitmentService` — consumes inbound commitment events from AP/Employee Finance/Asset Finance, idempotent upsert keyed on `sourceService + sourceReferenceId`, triggers ledger recalculation
- `BudgetLedgerService` — owns the allocated/committed/actual/available rollup; consumes GL actual-posted events
- `BudgetAvailabilityService` — the synchronous decision service behind the availability-check endpoint; applies configured control mode (block vs. warn), logs violations
- `CashRequirementEstimateService` — estimate creation/approval, optional advisory cross-check against Cash & Banking
- `ReferenceDataSyncService` — keeps `ref_*` cache tables current from Finance Administration events, exposes manual reconciliation
- `BudgetEventPublisherService` — thin shared wrapper around `RabbitTemplate`; every publish goes through here so envelope construction and the outbox write happen in exactly one place

---

## 9. DTOs

Sorted per the org's mandatory subfolders:
- `dto/requestDto/`: `CreateBudgetPlanRequest`, `UpdateBudgetPlanRequest`, `ApprovalDecisionRequest`, `CreateAllocationRequest`, `CreateTransferRequest`, `CreateRevisionRequest`, `BudgetAvailabilityCheckRequest`, `CreateCashRequirementEstimateRequest`
- `dto/responseDto/` (every class extends `BaseResponse`): `BudgetPlanResponse`, `BudgetLineResponse`, `BudgetAllocationResponse`, `BudgetTransferResponse`, `BudgetRevisionResponse`, `BudgetLedgerResponse`, `BudgetAvailabilityCheckResponse`, `CommitmentResponse`
- `dto/eventDto/`: one class per RabbitMQ event listed in §5, split further into outbound/inbound if helpful
- `dto/clientDto/`: only for Feign-specific request/response shapes distinct from our own DTOs
- Shared: `CoaSegmentReference` (accountId, costCenterId, departmentId, fundSourceId, projectId — reused across almost every request/response instead of redefining these five fields repeatedly), `PageResponse<T>`, `ApiErrorResponse`

---

## 10. Folder structure (already agreed, don't restructure again — build within it)

```
com.saas.budgetmanagement/
├── client/        # Feign clients (Finance Administration, General Ledger reads, etc.)
├── config/        # DO NOT TOUCH — FeignClientConfig, KeycloakConfig, OpenApiConfig, RabbitMqConfig, RoleConverter, SecurityConfig
├── model/         # entities (§6), extend Base; refcache/ subpackage for Ref* entities
├── dto/           # requestDto/, responseDto/, clientDto/, eventDto/ (§9)
├── enums/         # §6
├── repository/    # Spring Data JPA, one per entity
├── service/       # interfaces + impl/ (§8)
├── mapper/        # MapStruct, entity ↔ DTO
├── utility/        # PermissionEvaluator, PermissionUtil, ResourceEventContext, SecurityUtil, ValidationUtil
├── data/          # domain-specific persistence helpers
└── controller/    # §7
```

---

## 11. Config files — never modify

`config/FeignClientConfig`, `config/KeycloakConfig`, `config/OpenApiConfig`, `config/RabbitMqConfig`, `config/RoleConverter`, `config/SecurityConfig`, `resources/application.yaml`, `resources/logback-spring.xml`, parent and service `pom.xml`. If a task seems to require touching one of these, stop and ask rather than editing it.

---

## 12. Org-wide coding conventions to follow

- Model classes start with a capital letter; controller names are plural (`/budget-plans` not `/budget-plan`).
- Every response DTO extends `BaseResponse`.
- Always propagate auth/authorization tokens between services (handled via `FeignClientConfig` — use it, don't reimplement token propagation).
- Tenant ID is always resolved server-side, never taken as a blindly trusted path/body parameter.
  - **`SecurityUtil.getTenantId()` is an instance method returning a `String`, not a static method returning `UUID`.** Every service that needs it must inject `SecurityUtil` via constructor (`@RequiredArgsConstructor` + `private final SecurityUtil securityUtil;`) and convert explicitly: `UUID tenantId = UUID.fromString(securityUtil.getTenantId());`. Never write `SecurityUtil.getTenantId()` as a static call — it will not compile.
  - **Code that runs outside an authenticated HTTP request — RabbitMQ listeners, `@Scheduled` jobs, JPA entity listeners like `BaseEntityListener` — has no security context, so `SecurityUtil` cannot be used at all in those places.** For listeners, `tenantId` must come from the inbound event envelope instead. For scheduled/background jobs, `tenantId` must be passed in explicitly (e.g., looped over from a tenant registry), never resolved via `SecurityUtil`. `ReferenceDataSyncService`'s methods all take `UUID tenantId` as an explicit first parameter for exactly this reason — follow that pattern for any new service method that might be called from a non-request context.
- **Never use an unscoped repository method (`findAll(...)`, `findById(...)`, or any other default `JpaRepository` method without a `tenantId` condition) in service code.** These compile fine and look correct, but silently return or match data across every tenant — a serious security bug in this multi-tenant system. Always use (or add, if missing) a repository method that filters by `tenantId` explicitly, e.g. `findByTenantIdAndId(...)`, `findByTenantId(...)`. This mistake has already slipped in twice during this build (`BudgetAllocationService.getById` originally used `findById(id).filter(...)` instead of a proper scoped query; `BudgetTransferService.list` originally fell back to `findAll(pageable)` on its unfiltered branch) — treat any bare `findAll`/`findById` call as a bug to flag, not a convenience to use.
- Use MapStruct for entity↔DTO mapping — no manual field-by-field mapping in services.
- Keep controllers thin: no business logic in controllers, only in `service/`.
- RabbitMQ queue names for this service follow the pattern `create-budget-resource-queue`, `delete-budget-resource-queue`, `change-budget-resource-status-queue` where applicable, and must be registered in `auth-service`'s queue config — that registration is a manual step outside this repo, not something to generate here.
- New service module must be registered in the parent `pom.xml` and the API Gateway route table — both already done for this service per the setup steps; don't regenerate them.
- Cross-service reference data (COA segments) is validated via the shared `CoaSegmentValidator` component, and enriched with display names via `CoaSegmentDisplayResolver.enrichCoaSegments(...)` for any response DTO implementing `CoaSegmentDisplayAware` — reuse these rather than writing per-service validation or name-lookup logic.

---

## 13. Owned by other teams — do not modify, but flag findings here

Security, Keycloak, and tenant management infrastructure (`SecurityUtil`, `PermissionUtil`, `RoleConverter`, `KeycloakConfig`, `SecurityConfig`, and the concept of `tenantId` itself) are owned and developed by a separate team responsible for the shared ERP-wide auth layer, not by this service. This service is a **consumer** of that infrastructure — it calls into it correctly, but does not modify, patch, or work around it, even when a gap is found.

**Current placeholder assumptions this service has been built and tested against, pending confirmation from that team:**
- JWT tenant claim name: `"tenantId"` (read by `SecurityUtil.getTenantId()`)
- JWT role claim used for admin checks: a `GrantedAuthority` with value `"admin"` (checked by `PermissionUtil.isAdmin()`/`hasPermission(...)`)
- `PermissionUtil.hasPermission(tenantId, resourceName)`'s `resourceName` parameter is currently **not** used for real per-permission logic — every call is effectively just an admin-or-not check regardless of which resource string is passed. Real per-action permission granularity does not exist yet.
- Placeholder tenant UUID used throughout local dev/testing: `3fa85f64-5717-4562-b3fc-2c963f66afa6`

**Known gap to raise with the security/Keycloak team, not fix locally:** `SecurityUtil.getTenantId()` currently falls back to a hardcoded UUID (`3fa85f64-5717-4562-b3fc-2c963f66afa6`, the same placeholder used in local testing) if the JWT's tenant claim is missing, instead of throwing. This means a malformed or misconfigured token could silently attribute newly created records to that placeholder tenant rather than failing the request. This is flagged here as a finding for that team to evaluate — not something to patch from within this service.

Before production, this service's actual behavior should be re-verified against that team's real Keycloak realm/client configuration once available — claim names, role strings, and the tenant-resolution fallback behavior above should all be explicitly confirmed, not assumed to still match what's listed here.

## How to use this context

When I ask you to implement a specific class (e.g., "implement `BudgetPlanService.submit()`"), use this document to know: which other services/events are involved, what the entity/DTO shapes should look like, which folder it belongs in, and which conventions (tenant resolution, DTO base classes, mapper usage, event publishing via the outbox) must be followed. Don't re-derive architecture decisions already made here — implement against them.