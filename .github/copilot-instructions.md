1. What this project is

This is a production SaaS ERP system intended to be hosted for national-level organizations in Ethiopia — a real deployment target, not a training exercise. Treat security, data integrity, and compliance requirements as hard constraints. This service — Audit & Compliance Service — is one of ten microservices inside the system's Finance module, alongside Finance Administration, General Ledger, Cash & Banking, Accounts Payable, Accounts Receivable, Employee Finance, Asset & Inventory Finance, Financial Reporting, and Budget Management Service (a sibling service already built — its patterns and conventions are the baseline for this one).

Every service in this ERP is cloned from a shared startup-service template and follows the same folder structure, naming conventions, and config setup: client/config/model/dto/enums/repository/service/mapper/utility/data/controller.

Stack: Spring Boot · MySQL · RabbitMQ · Keycloak · API Gateway · MapStruct · Flyway.

Compliance & regulatory constraints (Ethiopia-specific — apply everywhere)
Data residency: all data, backups, and any caching layer must stay within Ethiopia. Never suggest or default to foreign cloud regions or external logging/monitoring services.
INSA cybersecurity alignment: defense-in-depth practices throughout (strict input validation, least-privilege access, encryption at rest/in transit, no hardcoded secrets). Flag anything that may need sign-off against the real INSA standard rather than assuming industry-default compliance is sufficient.
This service specifically exists to satisfy audit/compliance requirements for the whole ERP — its own data-integrity bar is stricter than any other service's. See §6 and §12.
2. What this service is responsible for

Audit & Compliance is the passive observer and system of record for the entire Finance module. It does not perform business transactions and does not validate or gate anyone else's business logic — it watches what every other finance service does, records it immutably, evaluates it against compliance rules, and answers questions about it later for auditors, compliance officers, and regulators.

In scope: Audit Trail, Financial Audit (query/reporting over the trail), Compliance Monitoring (active rule evaluation), Internal Controls tracking, Approval History (a filtered view, not separately stored), User Activity, Change History, Exception Reporting, data access/read-audit logging, log immutability & tamper-evidence, retention & archival, segregation-of-duties (SoD) violation detection, event-ingestion completeness monitoring, anomaly/fraud signal detection.

Explicitly out of scope:

"Profitable analysis" (costing method, performed activity, review) — decided out of scope; treated as a Financial Reporting responsibility, not this service's.
Polished/formatted report generation ("Financial Compliance Reports," "Government Reports") — this service exposes accurate, queryable data; Financial Reporting Service builds all formatted/presentation output. Same split used for Budget Management's "Budget Reports."
Enforcing or blocking any other service's business transaction. If this service is ever asked to reject, modify, or gate another service's action, that's scope creep — flag it rather than implement it. This service only ever observes and records.
3. Cross-service dependencies
Service	Relationship	What's needed
Every other finance service (all 9)	hard, one-way, event-only	Every significant state-changing action, published as an event — this is this service's entire data source
Keycloak / Security	soft, display-resolution	Resolve actorId → human-readable name/role for display. Advisory only — never blocks recording the underlying event
Finance Administration	soft, context-enrichment	Organization structure, cached locally (same reference-cache pattern as Budget Management), for grouping/filtering audit views
Financial Reporting Service	downstream consumer	Pulls compliance/audit data via bulk read endpoints; never queried synchronously the other direction

Data ownership rule: this service owns audit records, violations, and ingestion-health state. It never owns or influences the outcome of a business transaction in any other service.

4. Integration patterns
Asynchronous (RabbitMQ) is the dominant pattern — nearly everything this service does starts from an inbound event. Unlike Budget Management, there's very little outbound-event-triggers-more-events chaining; this service mostly terminates event flows.
Synchronous (REST) is narrow and read-only: auditor/compliance-officer UI queries, and a bulk-read endpoint for Financial Reporting (mirroring Budget Management's budget-ledger/summary pattern).
No create/update/delete REST endpoints on audit data, ever. This is a hard architectural rule, not a style preference. Every audit record originates from an inbound event, never from a direct API call. If a task seems to require a write endpoint on AuditRecord/ComplianceViolation, stop and flag it rather than building it.
5. Event contracts

Envelope shape, consistent with the rest of the ERP: {eventId, eventType, occurredAt, tenantId, correlationId, version, payload}.

Ingestion is a single generic listener, not nine per-service classes. AuditEventListener binds to all nine upstream queues and extracts sourceService from the eventType prefix (e.g. budget.* → BUDGET_MANAGEMENT). The payload is captured as raw JSON, not deserialized into a business-specific DTO — this service records that something happened, it doesn't need to understand each source service's business shape, and raw capture keeps ingestion resilient to upstream schema changes that would otherwise silently break it. Do not build nine separate listener classes or nine separate typed inbound DTOs — that was considered and deliberately rejected.

Publishes (small, deliberate set — not a pure sink, not a general broadcaster):

audit.compliance.violation.detected — a compliance rule evaluation fails (SoD violation, policy breach)
audit.exception.flagged — an anomaly/exception pattern detected
audit.ingestion.gap.detected — this service detects it may have missed events from a source (dead-letter buildup, sequence gap) — this service monitoring its own completeness

Subscribes to: all nine sibling finance services' relevant routing keys (finance-admin.*, budget.*, general-ledger.*, cash-banking.*, accounts-payable.*, accounts-receivable.*, employee-finance.*, asset-finance.*, financial-reporting.*). Exact queue/exchange names are owned by DevOps (see §11) — this service only declares the property keys it needs.

Idempotency matters more here than elsewhere: a duplicate audit record from redelivery doesn't just waste space, it undermines the record's credibility as evidence. Dedup on eventId/sourceEventId with a real unique constraint, not just application-level best-effort.

6. Database entities — six tables total (updated as the build progressed)
AuditRecord — core, immutable, hash-chained. One row per ingested event. Fields include sourceService, sourceEventId, eventType, occurredAt, tenantId, correlationId, actorId, entityType, entityId, raw payload (JSON text), recordHash, previousRecordHash, retainUntil.
ComplianceViolation — unifies "violation" and "exception" via a type/severity field. Immutable, same repository restrictions as AuditRecord.
ComplianceViolationReview — the deliberate mutable exception: case-management metadata (status, reviewer, resolution notes) for a violation, 1:1 via a unique constraint on violationId. The violation finding itself never changes; the response to it is tracked here instead. Every status change here must itself produce a new AuditRecord (sourceService AUDIT_COMPLIANCE) via AuditRecordService.ingest(...) — this service auditing its own case-management activity, already wired in ComplianceViolationReviewServiceImpl.
SourceIngestionStatus — one row per (tenantId, sourceService); mutable by design (tracks current health, not history). Backs audit.ingestion.gap.detected.
OutboxEvent — same transactional-outbox pattern as Budget Management; mutable by design (relay bookkeeping).
RefOrganizationUnit (in model/refcache/) — cached org structure from Finance Administration, includes parentExternalId for hierarchy (unlike Budget Management's equivalent cache entities, which deferred hierarchy — this service has a concrete, immediate need for it).

Deliberately no separate Approval History table. It's a filtered read over AuditRecord (eventType matching %.approved/%.rejected), not a second stored copy of the same fact.

Hash-chain scope: per (tenantId, sourceService), not one chain per tenant. Isolates the blast radius of any single source's gap/corruption to just that stream.

Retention: 10 years, fixed policy for now (not yet per-tenant configurable). Every AuditRecord carries a retainUntil = creation time + 10 years, set explicitly at creation — never left null or computed lazily.

Partitioning: deliberately deferred. Strong composite indexes now (tenantId + sourceService + createdAt, retainUntil); a documented year-based partitioning migration is a planned pre-production task once real volume data exists — don't partition speculatively.

Immutability is enforced at two layers, both required, neither optional on its own:

Repository interface: AuditRecordRepository and ComplianceViolationRepository extend the bare Spring Data Repository<T, ID> marker interface, not JpaRepository — this removes delete()/deleteById()/deleteAll() from existing at all, not just "unused."
Entity columns: every business field on AuditRecord and ComplianceViolation (not just some of them) carries updatable = false, so even a mistaken save() on a mutated managed entity cannot produce an UPDATE. Apply updatable = false to every business field on any future immutable entity in this service, not selectively.

If a task seems to need an update or delete method on either of these two repositories — including for retention/archival purposes — that is a signal to stop and flag it, not a normal repository method to add. See §8's retention note for the specific, deliberate scope boundary on how archival is actually meant to work.

7. API surface

Read-only. URL convention matches the rest of the ERP: /api/audit-compliance/{controller-plural}/{tenantId}.

GET /audit-records — filterable by entity type, entity ID, actor, date range
GET /approval-history — filtered view over AuditRecord, not a separate stored resource
GET /compliance-violations — filterable by type/severity/source service
A bulk/summary endpoint for Financial Reporting (pagination required — this table will be large)

No POST/PUT/PATCH/DELETE on any audit resource.

8. Service layer
AuditRecordService — ingestion/recording core; append-only, hash-chained writes
ComplianceMonitoringService — pluggable rule evaluation (ComplianceRule interface, one @Component per rule, auto-collected via List<ComplianceRule>) against incoming records; each detected violation is persisted and published in its own REQUIRES_NEW transaction so one rule's or one violation's failure can never roll back the underlying AuditRecord save or block another rule/violation in the same pass
ComplianceViolationReviewService — the deliberate mutable case-management layer over ComplianceViolation findings (see §6). Every status change must call back into AuditRecordService.ingest(...) with sourceService = AUDIT_COMPLIANCE — this service auditing its own activity
IngestionHealthService — tracks per-(tenant, source-service) event flow via SourceIngestionStatus, detects gaps on a scheduled sweep, triggers audit.ingestion.gap.detected

RetentionService — deliberately read-only, not a deletion service. Its job is reporting how many records are past their retainUntil date (getRetentionSummary(...)), nothing more. Physical archival/deletion of audit data is intentionally NOT implemented anywhere in this service's normal code path — it's treated as a separate, privileged, deliberately-out-of-band operation (standard practice for tamper-evident audit logs), not something reachable through a Spring service method or a scheduled job in this codebase. If a task asks you to "implement the archival job" or "make retention actually delete old records," the correct response is to flag that this is a deliberate architectural boundary requiring a real decision (a separate privileged tool/process, sign-off requirements, a cold-storage target) — not to add a delete() method to AuditRecordRepository to get it done. Adding that method would silently undo the entire immutability guarantee this service exists to provide.

Every write path in IngestionHealthService's recordEventReceived(...) must go through the pessimistic-lock repository method (findForUpdateByTenantIdAndSourceService), never the plain unlocked read — this table is updated at high frequency from concurrent listener threads, and the lock is what prevents a lost-update race on eventsReceivedCount.

Every service method callable from a non-request context (anything AuditEventListener or a @Scheduled job might reach, directly or transitively) takes tenantId — and where relevant, actorId/reviewerId — as an explicit parameter. Never resolve it via SecurityUtil inside these methods; that dependency only works from a real authenticated HTTP request.

9. Folder structure — same org convention as Budget Management
com.saas.auditcompliance/
├── client/        # Feign clients (Finance Administration org-structure reads, Keycloak identity resolution)
├── config/        # DO NOT TOUCH — same config classes as every service (FeignClientConfig, KeycloakConfig, OpenApiConfig, RabbitMqConfig, RoleConverter, SecurityConfig)
├── model/         # AuditRecord, ComplianceViolation, SourceIngestionStatus — extend Base
├── dto/           # requestDto/ (read filters only), responseDto/, eventDto/inbound (generic envelope only, no per-service typed DTOs), eventDto/outbound (3 events)
├── enums/         # ViolationType/Severity, SourceService, etc.
├── repository/    # No update/delete methods on AuditRecord/ComplianceViolation repositories
├── service/       # §8
├── mapper/        # MapStruct, entity ↔ DTO
├── utility/       # PermissionEvaluator, PermissionUtil, ResourceEventContext, SecurityUtil, ValidationUtil, hash-chaining helper
├── data/
└── controller/    # Read-only endpoints only (§7)
10. Config files — never modify

Same list as every service in this ERP: config/FeignClientConfig, config/KeycloakConfig, config/OpenApiConfig, config/RabbitMqConfig, config/RoleConverter, config/SecurityConfig, resources/application.yaml, resources/logback-spring.xml, parent and service pom.xml. If a task seems to require touching one of these, stop and ask.

11. Owned by other teams — do not modify, but flag findings here

Same ownership boundary as Budget Management:

Security/Keycloak team owns SecurityUtil, PermissionUtil, RoleConverter, auth config. This service is a consumer, not a modifier.
DevOps team owns RabbitMQ exchange/queue/binding topology. This service only declares property-key placeholders for the queue names it needs; it never declares exchanges/queues/bindings in code.
Placeholder tenant UUID used in local dev/testing: 3fa85f64-5717-4562-b3fc-2c963f66afa6 (same as Budget Management, for consistency across local testing).
Known finding carried over from Budget Management, likely still true here: SecurityUtil.getTenantId() may fall back to a hardcoded UUID if the JWT's tenant claim is missing, instead of throwing. If this service's testing surfaces the same behavior, it's a finding for the security team, not something to patch locally.
12. Org-wide + service-specific coding conventions

Carried over from Budget Management (apply identically here):

Tenant ID is always resolved server-side via injected SecurityUtil (UUID.fromString(securityUtil.getTenantId())), never a static call, never trusted from a path/body parameter directly.
Code outside an authenticated request (RabbitMQ listeners, scheduled jobs, entity listeners) cannot use SecurityUtil — tenant ID must come from the event envelope or be passed explicitly.
Never use an unscoped repository method (findAll, findById without a tenant condition). Always use/add a findByTenantIdAnd... method. This bug has recurred multiple times across this ERP's services — treat any bare findAll/findById as a bug to flag.
Use MapStruct for entity↔DTO mapping. Keep controllers thin — no business logic in controllers.
New service module must be registered in the parent pom.xml and the API Gateway route table.

New, specific to this service:

No update/delete methods on AuditRecordRepository or ComplianceViolationRepository, ever — not even ones that happen to be unused. This is stricter than the general "don't add unused methods" guidance; it's a data-integrity rule.
No REST write endpoints on audit resources — see §4 and §7.
**This rule applies specifically to `AuditEventListener`'s ingestion path, not the whole service.** Do not deserialize the nine sibling services' business events into typed DTOs for audit-trail recording — `AuditRecord.payload` stays raw JSON, consistent with §5. However, `ReferenceDataSyncService` is a deliberate, narrow exception: it consumes Finance Administration's organization-structure sync events via a separate, dedicated listener (`OrganizationStructureSyncListener`, mirroring Budget Management's `FinanceAdminEventListener` pattern) and DOES deserialize into a typed `OrganizationUnitSyncEvent` DTO, because the `RefOrganizationUnit` cache needs real, queryable fields (`code`, `name`, `parentExternalId`), not a JSON blob. If a task involves audit-trail ingestion, raw JSON is correct. If a task involves the `RefOrganizationUnit` reference cache specifically, a typed inbound DTO is correct. Don't apply one rule where the other belongs.
Every write to AuditRecord must compute and store recordHash/previousRecordHash as part of the same write — never insert a record without chain fields populated. The chain is scoped per (tenantId, sourceService); when computing previousRecordHash, query the latest record for that same (tenantId, sourceService) pair, not the latest record overall.
Retention (retainUntil) must be set on every AuditRecord at creation time — creation timestamp + 10 years — not left null or computed lazily later.
How to use this context

When asked to implement or debug a specific class, use this document to know: which other services/events are involved, what the entity/DTO shapes should look like, which folder it belongs in, and which conventions (immutability, raw-payload capture, hash-chaining, tenant resolution) must be followed. Don't re-derive architecture decisions already made here — implement and debug against them. If a bug or task seems to require violating one of the hard rules above (no write endpoints, no update/delete on audit tables, no per-service typed DTOs), treat that as a signal to stop and flag it rather than a signal the rule doesn't apply this time.