# Audit & Compliance Service

## Current Status

The project is currently in the planning stage. The following describes the planned API and workflow based on the approved project architecture.

## Planned API Endpoints

Base format:

```text
/api/audit-compliance/{resource}/{tenantId}
```

Every endpoint is tenant-scoped, read-only, authenticated, and permission-protected.

### 1. Query Audit Records

```http
GET /api/audit-compliance/audit-records/{tenantId}
```

Queries the immutable audit trail for all Finance services.

Supported filters:

- Entity type and entity ID
- Actor/user ID
- Source service
- Event type
- Date range
- Correlation ID
- Pagination and sorting

Example:

```http
GET /api/audit-compliance/audit-records/{tenantId}?sourceService=BUDGET_MANAGEMENT&from=2026-01-01&to=2026-08-26&page=0&size=50
```

This endpoint shows what happened, who performed it, when it happened, which service produced it, and the original raw event payload.

### 2. Approval History

```http
GET /api/audit-compliance/approval-history/{tenantId}
```

Provides a filtered view of audit records where the event represents an approval or rejection.

Examples:

```text
budget.plan.approved
budget.plan.rejected
accounts-payable.invoice.approved
cash-banking.payment.rejected
```

There is no separate approval-history table. Approval history is derived from `AuditRecord`.

### 3. Compliance Violations

```http
GET /api/audit-compliance/compliance-violations/{tenantId}
```

Queries compliance violations, policy breaches, segregation-of-duties violations, and detected exceptions.

Supported filters:

- Violation type
- Severity
- Status
- Source service
- Actor
- Entity reference
- Date range
- Pagination and sorting

The project uses one `ComplianceViolation` table for both violations and exceptions. A separate exceptions table is not required unless the API design later needs a specialized view.

### 4. Financial Reporting Bulk Read

```http
GET /api/audit-compliance/audit-records/{tenantId}/summary
```

Provides paginated audit and compliance data to the Financial Reporting Service.

It is intended for:

- Compliance summaries
- Approval-history data
- Audit dashboards
- Financial and government report preparation

Audit & Compliance provides accurate queryable data. Financial Reporting owns formatted report generation.

## Endpoints We Will Not Build

There will be no REST write endpoints:

```http
POST   /audit-records
PUT    /audit-records
PATCH  /audit-records
DELETE /audit-records
```

There will also be no write endpoints for compliance violations.

All writes originate from RabbitMQ events. This prevents users or other services from manually changing audit evidence.

## Project Workflow

```text
Finance Service
      |
      | Publishes audit event
      v
RabbitMQ
      |
      v
Generic AuditEventListener
      |
      v
Validate event and identify source service
      |
      v
Deduplicate event
      |
      v
AuditRecordService
      |
      | Store immutable, hash-chained record
      v
AuditRecord
      |
      v
ComplianceMonitoringService
      |
      +--> No violation: processing completes
      |
      +--> Violation detected:
                    |
                    v
          ComplianceViolation
                    |
                    v
          Publish compliance event
```

## Step-by-Step Explanation

### 1. A Finance service performs an action

Examples include:

- Budget plan approval
- Invoice payment
- Journal posting
- Bank reconciliation
- Payroll approval
- Asset acquisition or disposal
- Accounts receivable activity

The originating Finance service publishes an event.

### 2. Audit & Compliance receives the event

The common event envelope is:

```json
{
  "eventId": "unique-event-id",
  "eventType": "budget.plan.approved",
  "occurredAt": "2026-08-26T10:15:00Z",
  "tenantId": "tenant-id",
  "correlationId": "operation-id",
  "version": 1,
  "payload": {}
}
```

The service uses one generic `AuditEventListener` for all nine Finance services.

### 3. The source service is identified

The event prefix identifies the originating service:

```text
finance-admin.*          -> Finance Administration
budget.*                 -> Budget Management
general-ledger.*         -> General Ledger
cash-banking.*           -> Cash & Banking
accounts-payable.*       -> Accounts Payable
accounts-receivable.*    -> Accounts Receivable
employee-finance.*       -> Employee Finance
asset-finance.*          -> Asset & Inventory Finance
financial-reporting.*    -> Financial Reporting
```

The payload is stored as raw JSON. The service does not deserialize every upstream business DTO.

### 4. Duplicate events are prevented

RabbitMQ can redeliver messages. The service uses `eventId` or `sourceEventId` with a real database unique constraint.

This ensures one business event creates only one audit record.

### 5. The audit record is created

Every `AuditRecord` must include:

- Tenant ID
- Source service
- Source event ID
- Event type
- Occurrence time
- Correlation ID
- Actor ID
- Entity reference
- Raw payload
- Record hash
- Previous record hash
- Retention date

Records are hash-chained per:

```text
tenantId + sourceService
```

This makes unauthorized modification detectable.

Every record also receives:

```text
retainUntil = creation time + 10 years
```

### 6. Compliance rules are evaluated

`ComplianceMonitoringService` checks the recorded event for:

- Segregation-of-duties violations
- Policy breaches
- Missing or invalid approval patterns
- Unusual activity
- Possible fraud signals
- Internal-control failures

This service reports violations but does not block or change the originating transaction.

### 7. Violations and exceptions are published

When a violation is detected:

1. Store it in `ComplianceViolation`.
2. Link it to the relevant audit record.
3. Publish:

```text
audit.compliance.violation.detected
```

For anomaly patterns, publish:

```text
audit.exception.flagged
```

## Ingestion-Completeness Workflow

The service also monitors whether events are being received from every source:

```text
Events from Finance services
              |
              v
SourceIngestionStatus
              |
              +--> Normal event flow
              |
              +--> Missing events, sequence gap, or DLQ buildup
                              |
                              v
              audit.ingestion.gap.detected
```

`SourceIngestionStatus` maintains one record for each:

```text
tenantId + sourceService
```

A missing event is important because it may create a gap in the official audit trail.

## Three Planned Database Tables

### `AuditRecord`

Immutable, append-only record of every received event.

### `ComplianceViolation`

Stores compliance violations, exceptions, anomalies, and policy breaches.

### `SourceIngestionStatus`

Tracks the health and completeness of event ingestion for each source service.

## Meeting Summary

> Audit & Compliance is a passive observer and system of record for the Finance module. It receives events from all nine Finance services, stores them immutably with hash-chain protection, evaluates them for compliance risks, monitors whether events are missing, and exposes read-only APIs for auditors, compliance officers, and Financial Reporting. It never creates, changes, approves, rejects, or blocks transactions owned by another service.
