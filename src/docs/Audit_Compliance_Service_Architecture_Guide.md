# Audit & Compliance Service — Architectural Guide

**Ecosystem:** SaaS ERP Finance Microservices — National Organizations (Ethiopia)
**Stack:** Spring Boot · MySQL · RabbitMQ (event bus) · Keycloak
**Scope:** How Audit & Compliance integrates with the other nine finance services — dependencies, integration patterns, data flow, event contracts, and an implementation checklist. Companion to `Audit_Compliance_Service_Scope_and_Responsibilities.md` — read that first if you haven't; this guide assumes its conclusions.

---

## 1. Microservice Dependencies & Relationships

This service inverts the dependency shape every other service in this ERP has had so far. Budget Management had a handful of specific dependencies with clear reasons; Audit & Compliance has **one dependency on nearly every other service, all for the same reason**.

### 1.1 Every other finance service — *hard, one-way, event-only dependency*
- **Data required:** every significant state-changing action — creation, update, approval, rejection, deletion, control violation — published as an event carrying actor, timestamp, entity reference, and (where feasible) a before/after diff.
- **Why:** this is this service's entire reason to exist. Without these events, there is no audit trail.
- **Business context:** unlike every dependency Budget Management had, this isn't about validating a business rule — it's about *completeness*. A missing event isn't a minor bug here, it's a hole in a legal/regulatory record.

### 1.2 Keycloak / Security service — *soft, display-resolution dependency*
- **Data required:** human-readable user name/role, resolved from a user ID carried in inbound audit events.
- **Why:** an audit record that says `actorId: 5b151f84-...` is legally sufficient but operationally useless to a human auditor. Same reasoning as Budget Management resolving COA segment IDs into names via `CoaSegmentDisplayResolver` — this service will want an equivalent for user identities.
- **Business context:** advisory/display-only, never blocking — if identity resolution fails or is slow, the underlying audit record must still be recorded correctly; a missing display name is a UX gap, not a data-integrity gap.

### 1.3 Finance Administration — *soft, context-enrichment dependency*
- **Data required:** organization structure (which department/cost center a record belongs to), for grouping and filtering audit views.
- **Why:** "show me every audit record for the Finance department" is a realistic query shape; without organizational context cached the same way Budget Management cached COA segments, that query isn't answerable.
- **Business context:** same reference-cache pattern as Budget Management — event-driven local replica, not a live call per query.

### 1.4 Financial Reporting Service — *downstream consumer, not a dependency*
- **Data required from Audit & Compliance:** compliance status, violation summaries, approval-history data for "Government Reports" and "Financial Compliance Reports" style output.
- **Why:** same boundary as Budget Management's relationship with Financial Reporting — Audit & Compliance exposes accurate, queryable data; Financial Reporting builds the polished, formatted output. Audit & Compliance doesn't format reports for end users.

---

## 2. Integration Patterns

### 2.1 Asynchronous (RabbitMQ) — the dominant, near-exclusive pattern
Nearly everything this service does starts with an inbound event. Unlike Budget Management, there is no meaningful "publish outward for other services to act on" traffic in the normal case — this service mostly terminates event chains rather than continuing them. The one exception is publishing a small number of its own alert/notification events (see §4.2).

### 2.2 Synchronous (REST) — narrow, read-only, human/reporting-facing
Reserve REST endpoints for:
- Auditor/compliance-officer queries through a UI (browse audit trail, approval history, violations, filtered by entity/user/date range)
- Financial Reporting Service pulling compliance data in bulk (mirroring the `budget-ledger/summary` pattern from Budget Management — a paginated bulk-read endpoint, not a per-record lookup)

**There should be no create/update/delete REST endpoints on audit records themselves.** Every audit record originates from an inbound event, never from a direct API call — this is a deliberate architectural constraint, not an oversight, and it's the single most important rule for this service's controller layer.

### 2.3 Configuration bootstrap
Much lighter than Budget Management's — this service needs org-structure and user-identity reference caches, not fiscal year/COA validation, since it never validates business rules, only records what already happened. Same event-cache + reconciliation-fallback pattern applies where needed.

---

## 3. Data Flow Architecture

### 3.1 Ingestion → recording → (optional) evaluation
```
Every finance service --(audit-relevant events)--> Audit & Compliance
                                                          |
                                                          v
                                          AuditRecordService: append immutable record
                                          (hash-chained, tenant-scoped, source event preserved)
                                                          |
                                                          v
                                    ComplianceMonitoringService: evaluate against policy rules
                                          (SoD checks, control-violation correlation,
                                           anomaly signals)
                                                          |
                                                          v
                                    On violation: ExceptionRecord created +
                                          audit.compliance.violation.detected published
```

### 3.2 Feeding Financial Reporting
Audit & Compliance exposes bulk, paginated, filterable read endpoints over its own data (audit records, approval history, violations) — Financial Reporting queries these to build its own report/dashboard views, the same relationship Budget Management has with Financial Reporting via `budget-ledger/summary`.

### 3.3 Data ownership boundaries

| Owned by Audit & Compliance | Read-only for Audit & Compliance (owned elsewhere) |
|---|---|
| Audit Trail records (immutable, append-only) | The actual business entities the events describe (a `BudgetPlan`, an `Invoice`, etc. — Audit & Compliance never touches source-of-truth business data) |
| Compliance violation / exception records | Organization structure (Finance Administration) |
| Approval history index | User identity/role display data (Keycloak/security) |
| Internal controls / SoD violation records | |
| Retention & archival metadata | |

**The rule, sharper here than in any other service so far:** Audit & Compliance never owns or influences the *outcome* of a business transaction — it only ever observes and records. If this service is ever tempted to reject, modify, or gate another service's action, that's a sign of scope creep; that responsibility belongs to the originating service (e.g., separation-of-duties enforcement ultimately belongs in each service's own approval logic, informed by data this service can supply, not enforced by this service directly).

---

## 4. Event-Driven Design with RabbitMQ

### 4.1 Exchange/topology convention
Same convention as the rest of the ERP: topic exchanges per domain, routing keys `<service>.<entity>.<action>`. This service's own outbound routing keys should follow `audit.<entity>.<action>`.

### 4.2 Events Audit & Compliance **publishes** — deliberately small

| Routing key | Triggered when | Key payload fields |
|---|---|---|
| `audit.compliance.violation.detected` | A compliance rule evaluation fails (SoD violation, policy breach) | violationId, sourceService, entityReference, ruleViolated, actorId, detectedAt |
| `audit.exception.flagged` | An anomaly/exception pattern is detected | exceptionId, sourceService, pattern, severity |
| `audit.ingestion.gap.detected` | This service detects it may have missed events from a source (dead-letter buildup, sequence gap) | sourceService, suspectedGapWindow, detectedAt |

The third one is worth calling out: it's this service monitoring *itself* and alerting when its own completeness is in doubt — directly implementing the "event ingestion completeness monitoring" responsibility flagged in the scope document.

### 4.3 Events Audit & Compliance **subscribes to** — from every other service

| Source service | Example routing keys (pattern) | Notes |
|---|---|---|
| Finance Administration | `finance-admin.*.created/updated/closed` | Config-change auditing — who changed a fiscal-year/COA setting matters for compliance too |
| Budget Management | `budget.plan.approved/rejected`, `budget.transfer.approved`, `budget.revision.created`, `budget.commitment.*`, `budget.control.violation` | Already publishing — this is the concrete, already-built upstream source described in the scope doc |
| General Ledger | `general-ledger.*.posted/adjusted/closed` | Journal-level audit trail |
| Cash & Banking | `cash-banking.payment.*`, `cash-banking.reconciliation.*` | Payment approval chain, reconciliation actions |
| Accounts Payable | `accounts-payable.invoice.*`, `accounts-payable.payment.*` | Vendor payment approval trail |
| Accounts Receivable | `accounts-receivable.*` | Customer credit/collection actions |
| Employee Finance | `employee-finance.payroll.*`, `employee-finance.advance.*` | Payroll approval trail — high sensitivity |
| Asset & Inventory Finance | `asset-finance.*.acquired/disposed/revalued` | Asset lifecycle actions |
| Financial Reporting | `financial-reporting.report.generated` (if published) | Record of who pulled/generated which report — reports on regulated financial data are themselves an auditable action |

**This is the largest fan-in of any service in the whole ERP** — nine listener classes (or one generic listener with source-service routing, worth deciding deliberately, see §5) versus Budget Management's four.

### 4.4 Message format & failure handling — stricter than elsewhere
- **Envelope:** reuse the same `{eventId, eventType, occurredAt, tenantId, correlationId, version, payload}` shape established in Budget Management, for ERP-wide consistency.
- **Preserve the original envelope, don't just extract the payload.** Unlike Budget Management's listeners (which transform an inbound event into new domain state), this service's core value is recording *the event itself* — `sourceEventId`, `sourceService`, and `occurredAt` from the original envelope should be stored as first-class fields on the audit record, not discarded after routing.
- **Idempotency matters even more here.** A duplicate audit record from redelivery isn't just redundant, it actively undermines the record's credibility as evidence. Same `eventId`-based dedup pattern as Budget Management, enforced with a unique constraint, not just application-level best-effort.
- **Retry/dead-letter, with a stricter failure posture.** In Budget Management, a failed event went to the DLQ for eventual manual review. Here, a message stuck in a DLQ for too long should itself trigger `audit.ingestion.gap.detected` — silence in the audit trail is a finding, not just an ops nuisance.
- **No outbox pattern needed for the *listener* side** — this service isn't publishing as a side effect of a business transaction the way Budget Management does; recording an audit event is the entire transaction. The outbox pattern is still worth using for the small set of outbound events in §4.2, for the same crash-safety reasons as before.

---

## 5. Implementation Checklist for Spring Boot

### 5.1 Data model considerations (new territory versus Budget Management)
- [ ] **Immutability enforced at the application layer at minimum** — `AuditRecordRepository` should expose no `update`/`delete` methods at all, not just "unused" ones. Consider also restricting DB-level grants for the application's DB user (no `UPDATE`/`DELETE` privilege on the audit tables) as defense in depth.
- [ ] **Hash-chaining** — each audit record stores a hash of its own content plus the previous record's hash (per tenant, or per entity stream), so any tampering breaks the chain and is detectable. This is what turns "an audit table" into "a tamper-evident audit trail."
- [ ] **Retention/archival fields** — `retainUntil` or equivalent, plus a defined archival job (move to cold storage after N years) rather than indefinite single-table growth. Confirm the actual retention period required before finalizing this (flagged as an open question in the scope doc).
- [ ] **Partitioning strategy** — likely by tenant + time period, given expected volume (every action across nine services, for potentially many national organizations, accumulating for years).

### 5.2 API contract requirements
- [ ] Read-only endpoints: `/audit-records` (filterable by entity type, entity ID, actor, date range), `/approval-history`, `/compliance-violations`, `/exceptions`
- [ ] A bulk/summary endpoint for Financial Reporting, matching the `budget-ledger/summary` pattern
- [ ] No write endpoints on audit data — every write path is event-driven only
- [ ] Explicit tenant scoping on every endpoint, same as every other service in this ERP

### 5.3 Service layer
- [ ] `AuditRecordService` — the ingestion/recording core; append-only, hash-chained writes
- [ ] `ComplianceMonitoringService` — rule evaluation against incoming records (SoD checks, policy rules)
- [ ] `RetentionService` — archival/purge logic per policy
- [ ] `IngestionHealthService` — tracks per-source-service event flow, detects gaps, triggers `audit.ingestion.gap.detected`
- [x] **Decided:** a single generic `AuditEventListener` (not nine per-service classes), bound to all nine upstream queues, extracting `sourceService` from the event envelope's `eventType` prefix. It captures the payload as **raw JSON, not a deserialized business DTO** — this service records that something happened, it doesn't need to understand each source service's business shape, and raw capture makes it resilient to upstream schema changes that would otherwise silently break ingestion.

### 5.3a Entity list — decided
Three tables, deliberately not more:
- **`AuditRecord`** — core, immutable, hash-chained. One row per ingested event. Carries `sourceService`, `sourceEventId`, `eventType`, `occurredAt`, `tenantId`, `correlationId`, `actorId`, `entityReference`, raw `payload`, plus `recordHash`/`previousRecordHash`.
- **`ComplianceViolation`** — unifies "compliance violation" and "exception" (a `type`/`severity` field distinguishes them) rather than two overlapping tables recording the same fact twice.
- **`SourceIngestionStatus`** — one row per (tenantId, sourceService); tracks last-received event/timestamp and gap-detection state, backing `audit.ingestion.gap.detected`.

**Deliberately no separate Approval History table** — it's a filtered read over `AuditRecord` (`eventType` matching `%.approved`/`%.rejected`), not its own stored copy, to avoid two sources of truth for the same fact.

### 5.3b Hash-chain scope — decided
Chained **per (tenantId, sourceService)**, not one chain per tenant. Isolates the blast radius of any single source's gap or corruption to just that stream, rather than making an unrelated service's records look unverifiable too.

### 5.3c Partitioning — decided
Deferred. Use strong composite indexes now (`tenantId + sourceService + createdAt`, `retainUntil`) against the single table; plan a documented year-based partitioning migration as a pre-production task once real volume data exists to size it against, rather than partitioning speculatively today.

### 5.4 Configuration management
- [ ] RabbitMQ queue bindings for all nine upstream services — by far the largest binding surface of any service in this ERP; worth confirming with DevOps early, given the audit findings from the Budget Management build (undeclared exchange/queue topology in code, provisioning owned externally)
- [ ] Retention/archival schedule as an externalized, tenant-configurable property, not hardcoded

---

## Summary

Audit & Compliance is structurally the inverse of every service built so far: almost all inbound, almost nothing outbound, no business-rule validation, and a data-integrity bar (immutability, tamper-evidence) stricter than anywhere else in the ERP. The design should optimize for **never silently losing an event** and **never allowing a recorded event to be altered** — those two properties are what make this service's output usable as actual audit evidence rather than just another activity log.
