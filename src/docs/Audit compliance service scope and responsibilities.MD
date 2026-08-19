Audit & Compliance Service — Scope & Responsibilities

Status: Planning/discussion document — precedes architecture and folder-structure design (next session). Context: Second microservice in the Finance module of the national ERP, following Budget Management Service (core functionality complete, paused). Same stack and conventions apply (Spring Boot, RabbitMQ, MySQL, Keycloak, the shared startup-service template structure).

1. What this service actually is

Every other service in the Finance module is defined by what it lets people do — plan a budget, pay a supplier, run payroll. Audit & Compliance is different: it doesn't let anyone do anything. Its entire job is to watch everything the other nine services do, record it immutably, and answer questions about it later — for internal reviewers, external auditors, and (given this is a national-organization deployment) government/regulatory bodies.

Three things follow from that framing, and they matter for every design decision later:

It is almost entirely a consumer, not a producer. Every other service in this ERP publishes events; Audit & Compliance subscribes to nearly all of them. It rarely, if ever, needs another service to call it synchronously — the exception is when a human (an auditor, a compliance officer) queries it directly through its own API.
Its data must be trustworthy in a stronger sense than any other service's. A budget figure can be corrected with a revision. An audit record of what actually happened must never be editable after the fact — if it can be quietly changed, it has no value as evidence. This pushes the whole design toward append-only, tamper-evident storage, more strictly even than the append-only pattern we used for Commitment in Budget Management.
It's the one service where "who did this and were they allowed to" is the entire point, not a side concern. Every other service asks "is this a valid business transaction?" This service asks "is this a valid, authorized, policy-compliant action, and can we prove it later?"
2. Walking through the PDF's listed responsibilities
Audit Trail

The core function: an immutable, chronological record of every significant action across every finance service — who did it, what entity it touched, when, and (ideally) what changed. This is the backbone everything else in this service is built on top of.

Financial Audit

Support for formal audit processes — not just storing records, but producing the structured views an internal or external auditor actually works from: "show me every transaction touching account X between these dates," "show me every approval this user made last quarter." This is largely a querying/reporting layer over the Audit Trail, not a separate data store.

Compliance Monitoring

Distinct from Audit Trail in an important way: Audit Trail is passive recording ("this happened"). Compliance Monitoring is active evaluation ("this happened, and it violates policy X"). This is where automated rule-checking lives — e.g., flagging a transaction that bypassed a required approval step, or a user acting outside their normal role pattern.

Internal Controls

Tracking of the control mechanisms themselves and their effectiveness — records of segregation-of-duties enforcement, control violation logs (this is directly where Budget Management's BudgetControlViolationLog events, and the separation-of-duties gap we flagged in BudgetApprovalService, should ultimately feed).

Approval History

A specific, queryable slice of the Audit Trail focused on approval workflows specifically — every submit/approve/reject across every service, with the approver's identity and timestamp. Overlaps with Audit Trail but is worth keeping addressable on its own, since "show me this person's approval history" is a common, specific query shape.

User Activity

Broader than the Audit Trail's entity-change focus — this covers what a user did, including actions that didn't change any data (logins, failed login attempts, pages/records viewed, permission-denied events). This matters a lot for a national-security-adjacent deployment: knowing someone viewed sensitive data can matter as much as knowing they changed it.

Change History

Field-level, before/after diffs for entity changes — a more granular sibling of Audit Trail. Where Audit Trail might say "BudgetPlan X was updated by user Y," Change History says "requestedAmount changed from 250,000 to 300,000."

Exception Reporting

Reporting specifically on anomalies — failed approval attempts, control violations, unusual timing or volume patterns. This is the output side of Compliance Monitoring's detection work.

Financial Compliance Reports

Formatted, regulator-facing output. Worth flagging the same boundary question we raised with Budget Management's "Budget Reports" line: is this Audit & Compliance generating polished reports, or is Financial Reporting Service (which separately lists "Government Reports" and "IFRS Reports") the one that formats output, with Audit & Compliance just being a correct, queryable data source underneath? I'd expect the same pattern we settled on for Budget Management — this service exposes accurate, well-structured data; Financial Reporting builds the polished output — but this is worth confirming explicitly rather than assuming.

Profitable analysis (costing method, performed activity, review)

Worth flagging as the one item on this list that doesn't obviously belong here. Costing methods and profitability analysis are management-accounting concerns, conceptually closer to Financial Reporting's "Analytics/KPIs" responsibility than to audit/compliance. It's possible this line was included because profitability analysis has a compliance-review angle (auditing whether costing methods were applied correctly and consistently) rather than performing the profitability calculation itself. Worth raising as a scope question in your next team discussion rather than assuming which interpretation is correct.

Other related

An intentionally open catch-all — see the additions below for what I'd propose filling it with.

3. Responsibilities I'd propose adding

Given the compliance/national-deployment context established for this whole project, a few things are conspicuously absent from the PDF's list — gaps that a real audit service for a government-adjacent financial system would need:

Data access / read-audit logging — logging who viewed sensitive records, not just who changed them. Distinct from "User Activity" above in that it needs to be systematic and mandatory for designated sensitive data classes, not just a general activity log.
Log immutability & tamper-evidence — the actual mechanism that makes an audit trail trustworthy as evidence (append-only storage, hash-chaining between records, or a WORM-style guarantee). Without this, "Audit Trail" is just a regular table anyone with DB access could quietly edit — which defeats its purpose entirely.
Retention & archival policy management — financial audit records typically have mandated minimum retention periods (often 5–7+ years depending on jurisdiction). This service needs an explicit policy for how long records are kept and how/when they're archived, not indefinite growth in a single live table.
Segregation-of-duties (SoD) violation detection — explicit, cross-service checking of "did the same person request and approve this," "did someone act outside their assigned role." This is the natural home for closing the exact gap we flagged in Budget Management's BudgetApprovalService stub — that service should eventually call into (or be informed by) this one.
Event ingestion completeness monitoring — since this service's entire value depends on reliably receiving events from nine other services, it needs to monitor for gaps (a service that stopped publishing, a dead-lettered event that never got reprocessed) rather than silently having incomplete records and no one noticing.
Anomaly/fraud signal detection — proactive flagging of suspicious patterns (unusual after-hours activity, atypical transaction volume, rapid approve-then-reverse patterns) — a more analytical layer on top of Exception Reporting's more literal rule violations.
4. How this connects to the other nine services

This is the most structurally unusual service in the whole ecosystem, worth understanding clearly before we design it:

Every other finance service is an upstream publisher to this one. Finance Administration, Budget Management, General Ledger, Cash & Banking, Accounts Payable, Accounts Receivable, Employee Finance, Asset & Inventory Finance, and Financial Reporting should all publish audit-relevant events that this service consumes. In Budget Management's case specifically, several event types we already built are exactly what this service should be listening for: budget.plan.approved/rejected, budget.transfer.approved, budget.revision.created, budget.commitment.*, and especially budget.control.violation.
It very rarely calls any other service. Unlike Budget Management (which calls Finance Administration synchronously for validation), Audit & Compliance's job doesn't require querying other services for permission or data — it just needs to correctly receive what they publish. The one plausible exception: it might call Finance Administration or the security team's identity service to resolve a user ID into a human-readable name/role for report display, similar to how Budget Management resolves COA segment IDs into names.
It depends on the same two "other team" boundaries we already mapped for Budget Management — Keycloak/security (for whose identity is doing what) and RabbitMQ/DevOps (for the actual broker topology). Nothing about this service changes those ownership boundaries; if anything, this service's correctness depends on them even more directly, since "who did this" is its entire reason to exist.
Financial Reporting Service is its most likely downstream consumer, for the "Government Reports"/"Financial Compliance Reports" overlap flagged above — worth deciding in the same conversation whether Audit & Compliance publishes its own events onward (e.g., a monthly compliance summary) or whether Financial Reporting instead just queries Audit & Compliance's own read API directly.