### [cobre-assessment-001] Bootstrap Spring Boot Service Skeleton
- **Description**: Create a Java 21 Maven Spring Boot application for the notification capability with modules/packages for API, domain, persistence, messaging, worker, security, and observability.
- **Acceptance Criteria**:
  - Maven project builds with Spring Boot Web, Validation, Security, Data JPA, Flyway, AMQP, Actuator, PostgreSQL driver, Testcontainers, JUnit 5, and WireMock or MockWebServer dependencies.
  - Application starts with an empty health endpoint and package structure ready for independent API, worker, and relay components.
- **Depends on**: None
- **Blocks**: [cobre-assessment-002], [cobre-assessment-003], [cobre-assessment-006], [cobre-assessment-008]

### [cobre-assessment-002] Add Local Docker Compose Infrastructure
- **Description**: Add Docker Compose topology for local development with PostgreSQL, RabbitMQ, mock webhook receiver, and optional OpenTelemetry collector.
- **Acceptance Criteria**:
  - `docker-compose.yml` starts PostgreSQL and RabbitMQ with durable volumes and service health checks.
  - Mock webhook receiver is reachable from the application network and can return configurable `2xx`, `4xx`, `5xx`, and timeout responses.
- **Depends on**: [cobre-assessment-001]
- **Blocks**: [cobre-assessment-003], [cobre-assessment-012], [cobre-assessment-032]

### [cobre-assessment-003] Configure Application Profiles And Runtime Settings
- **Description**: Configure Spring profiles for local and test execution, including database, RabbitMQ, Flyway, security, webhook timeout, retry, and retention settings.
- **Acceptance Criteria**:
  - Local profile reads PostgreSQL, RabbitMQ, signing, encryption, and rate-limit settings from environment variables with safe defaults for Docker Compose.
  - Test profile supports Testcontainers and deterministic clocks for retry, retention, and lease-expiration tests.
- **Depends on**: [cobre-assessment-001], [cobre-assessment-002]
- **Blocks**: [cobre-assessment-004]

### [cobre-assessment-004] Create Database Migrations For Notification Storage
- **Description**: Add Flyway migrations for `subscription`, `notification_event`, `delivery_attempt`, `replay_job`, `delivery_outbox`, and `idempotency_key` tables in PostgreSQL.
- **Acceptance Criteria**:
  - Migrations include unique constraints for active subscription per `(client_id, event_type)`, `(source_event_id, client_id, event_type)`, `(notification_event_id, delivery_execution_id, attempt_number)`, and retained `(client_id, idempotency_key)`.
  - Migrations include indexes for `(client_id, created_at DESC, notification_event_id DESC)`, `(client_id, delivery_status, created_at DESC)`, `replay_job(notification_event_id, created_at DESC)`, and `delivery_outbox(status, available_at)`.
- **Depends on**: [cobre-assessment-003]
- **Blocks**: [cobre-assessment-005], [cobre-assessment-007], [cobre-assessment-026], [cobre-assessment-028]

### [cobre-assessment-005] Add Local Seed Data And Fixture Loader
- **Description**: Add seed data for sample notification events, active subscriptions, endpoint snapshots, and delivery attempts used by API examples and local demos.
- **Acceptance Criteria**:
  - Seed data covers `CLIENT001`, `CLIENT002`, and `CLIENT003`, including completed and failed notification examples referenced by the API design.
  - Seed data can be loaded in local profile without overwriting existing rows and is disabled by default in production-like profiles.
- **Depends on**: [cobre-assessment-004]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-006] Implement Domain Types And State Enums
- **Description**: Implement domain types for notification events, delivery attempts, replay jobs, outbox jobs, subscriptions, payload classification, and legal delivery/replay states.
- **Acceptance Criteria**:
  - Domain enums include `pending`, `in_progress`, `retrying`, `completed`, and `failed` for delivery status, plus separate replay statuses.
  - Domain model explicitly represents `lock_version`, `leased_by`, `lease_expires_at`, `current_attempt_id`, `endpoint_snapshot`, and payload redaction metadata.
- **Depends on**: [cobre-assessment-001]
- **Blocks**: [cobre-assessment-007], [cobre-assessment-009], [cobre-assessment-015]

### [cobre-assessment-007] Implement Persistence Repositories
- **Description**: Add Spring Data JPA repositories and query methods for tenant-scoped notification events, delivery attempts, subscriptions, replay jobs, outbox jobs, and idempotency records.
- **Acceptance Criteria**:
  - Repository methods never accept unscoped event lookup without `client_id` for self-service API paths.
  - Outbox and worker queries support optimistic locking and claim-by-status semantics without full table scans.
- **Depends on**: [cobre-assessment-004], [cobre-assessment-006]
- **Blocks**: [cobre-assessment-010], [cobre-assessment-011], [cobre-assessment-015], [cobre-assessment-019], [cobre-assessment-020], [cobre-assessment-021], [cobre-assessment-028]

### [cobre-assessment-008] Implement Tenant Authentication, Scopes, And Error Shape
- **Description**: Configure Spring Security resource-server support for signed JWTs or local test tokens, deriving `client_id` and scopes from the authenticated principal.
- **Acceptance Criteria**:
  - Requests expose authenticated `client_id`, scopes, and correlation ID to service layers without allowing `client_id` query overrides.
  - API errors use the documented `{ "error": { "code", "message", "correlation_id" } }` shape and never include stack traces or internal identifiers.
- **Depends on**: [cobre-assessment-001]
- **Blocks**: [cobre-assessment-020], [cobre-assessment-021], [cobre-assessment-022], [cobre-assessment-023], [cobre-assessment-024]

### [cobre-assessment-009] Implement Payload Encryption, Hashing, And Redaction
- **Description**: Add payload handling that classifies fields, encrypts restricted raw payload data, stores `payload_hash`, and returns redacted summaries by default.
- **Acceptance Criteria**:
  - Restricted payload fields are encrypted before persistence using an application encryption service backed by environment-provided keys.
  - API response mappers return `content_summary` and `payload_redacted=true` unless the caller has `notification-events:payload:read` and requests `include_payload=true`.
- **Depends on**: [cobre-assessment-006]
- **Blocks**: [cobre-assessment-010], [cobre-assessment-011], [cobre-assessment-014], [cobre-assessment-020], [cobre-assessment-021], [cobre-assessment-026], [cobre-assessment-028]

### [cobre-assessment-010] Implement Subscription Lookup And Webhook URL Guard
- **Description**: Implement subscription lookup for one active endpoint per `(client_id, event_type)` plus SSRF-safe webhook URL validation.
- **Acceptance Criteria**:
  - Subscription validation accepts only `https://` URLs and blocks private, loopback, link-local, multicast, and cloud metadata IP ranges.
  - DNS is resolved at subscription validation time and the validation component exposes a reusable delivery-time recheck method.
- **Depends on**: [cobre-assessment-007], [cobre-assessment-009]
- **Blocks**: [cobre-assessment-011], [cobre-assessment-014], [cobre-assessment-019], [cobre-assessment-031]

### [cobre-assessment-011] Implement Notification Ingestion With Transactional Outbox
- **Description**: Implement platform event ingestion that validates subscriptions and persists `notification_event` plus first `delivery_outbox` row in the same database transaction.
- **Acceptance Criteria**:
  - Duplicate source events are suppressed by `(source_event_id, client_id, event_type)` and do not create duplicate notifications.
  - A committed notification always has a recoverable pending outbox row, even if queue publication has not happened yet.
- **Depends on**: [cobre-assessment-007], [cobre-assessment-009], [cobre-assessment-010]
- **Blocks**: [cobre-assessment-013], [cobre-assessment-019]

### [cobre-assessment-012] Define RabbitMQ Messaging Contracts
- **Description**: Define RabbitMQ exchanges, queues, routing keys, dead-letter configuration, and message payloads for delivery and replay executions using Spring AMQP.
- **Acceptance Criteria**:
  - Delivery command messages include `notification_event_id`, `delivery_execution_id`, job type, attempt number, and correlation ID.
  - Queue configuration includes durable queues and dead-letter routing for poison messages.
- **Depends on**: [cobre-assessment-002]
- **Blocks**: [cobre-assessment-013], [cobre-assessment-016]

### [cobre-assessment-013] Implement Outbox Relay
- **Description**: Implement a background relay that claims available `delivery_outbox` rows, publishes RabbitMQ delivery commands, and marks rows as published.
- **Acceptance Criteria**:
  - Relay safely retries failed publishes by recording `last_error` and incrementing outbox `attempt_count`.
  - Relay emits a metric or log signal for stale unpublished outbox rows.
- **Depends on**: [cobre-assessment-011], [cobre-assessment-012]
- **Blocks**: [cobre-assessment-019], [cobre-assessment-025], [cobre-assessment-030]

### [cobre-assessment-014] Implement Signed Webhook HTTP Client
- **Description**: Implement HTTPS webhook delivery client with SSRF delivery-time DNS recheck, strict timeouts, bounded body size, no unsafe redirects, and HMAC signing.
- **Acceptance Criteria**:
  - Client sends `X-Cobre-Event-Id`, `X-Cobre-Notification-Id`, `X-Cobre-Delivery-Attempt`, `X-Cobre-Timestamp`, and `X-Cobre-Signature` headers.
  - HMAC signature is computed over timestamp plus raw request body, and secrets are referenced by `signing_secret_ref` without logging secret values.
- **Depends on**: [cobre-assessment-009], [cobre-assessment-010]
- **Blocks**: [cobre-assessment-016], [cobre-assessment-025], [cobre-assessment-031]

### [cobre-assessment-015] Implement Delivery State Machine And Lease Service
- **Description**: Implement delivery state transitions, optimistic locking, worker leases, and terminal-state protection for notification delivery.
- **Acceptance Criteria**:
  - Only legal transitions are allowed: `pending -> in_progress -> completed`, `pending -> in_progress -> retrying -> pending`, and `pending -> in_progress -> failed`.
  - State updates require expected `lock_version` and set or clear `leased_by`, `lease_expires_at`, and `current_attempt_id` consistently.
- **Depends on**: [cobre-assessment-007]
- **Blocks**: [cobre-assessment-016], [cobre-assessment-019], [cobre-assessment-027]

### [cobre-assessment-016] Implement Webhook Delivery Worker
- **Description**: Implement RabbitMQ consumer workers that claim delivery jobs, send signed webhooks, persist `delivery_attempt` records, and update notification status.
- **Acceptance Criteria**:
  - `2xx` responses mark notifications `completed`; permanent failures mark them `failed`; transient failures record attempt details for retry scheduling.
  - Worker crash or duplicate message handling cannot overwrite terminal state or create duplicate attempt numbers for the same delivery execution.
- **Depends on**: [cobre-assessment-012], [cobre-assessment-014], [cobre-assessment-015]
- **Blocks**: [cobre-assessment-017], [cobre-assessment-025], [cobre-assessment-030]

### [cobre-assessment-017] Implement Retry Backoff Scheduler
- **Description**: Implement retry scheduling for transient webhook failures with a Stripe-like three-day horizon, exponential backoff, and jitter.
- **Acceptance Criteria**:
  - Transient failures include timeouts, DNS failure, `429`, and `5xx`; permanent failures include blocked URL policy, TLS validation failure, `400`, `401`, `403`, `404`, and `410`.
  - Retry schedule starts promptly and uses approximately 1 minute, 5 minutes, 30 minutes, 2 hours, then progressive spacing until the three-day horizon.
- **Depends on**: [cobre-assessment-016]
- **Blocks**: [cobre-assessment-018], [cobre-assessment-027], [cobre-assessment-030]

### [cobre-assessment-018] Implement Lease Recovery And Dead-Letter Handling
- **Description**: Add recovery job for expired `in_progress` leases and operational handling for dead-lettered delivery messages.
- **Acceptance Criteria**:
  - Expired leases are returned to `pending` or `retrying` without losing attempt history.
  - Dead-lettered messages are inspectable through logs or metrics with correlation ID, notification ID, and failure classification.
- **Depends on**: [cobre-assessment-017]
- **Blocks**: [cobre-assessment-030]

### [cobre-assessment-019] Implement Replay Service And Idempotency
- **Description**: Implement replay creation as a separate `replay_job` resource with idempotency records, current active endpoint lookup, endpoint snapshotting, and outbox enqueueing.
- **Acceptance Criteria**:
  - Only terminal `failed` notifications owned by the authenticated client can create replay jobs.
  - Same `(client_id, Idempotency-Key, request_hash)` returns the stored replay response, while key reuse with a different request body returns `409`.
- **Depends on**: [cobre-assessment-010], [cobre-assessment-011], [cobre-assessment-013], [cobre-assessment-015]
- **Blocks**: [cobre-assessment-022], [cobre-assessment-023], [cobre-assessment-025], [cobre-assessment-027]

### [cobre-assessment-020] Implement List Notification Events API
- **Description**: Add `GET /notification_events` with tenant scope, filters, page/size pagination, redacted payload summaries, and correlation IDs.
- **Acceptance Criteria**:
  - Supports `created_from`, `created_to`, `delivery_status`, `include_payload`, `page`, and `size` validation according to `docs/api-design.md`.
  - Results are always scoped to authenticated `client_id`; cross-client events never appear in list responses.
- **Depends on**: [cobre-assessment-007], [cobre-assessment-008], [cobre-assessment-009]
- **Blocks**: [cobre-assessment-021], [cobre-assessment-024], [cobre-assessment-025], [cobre-assessment-029], [cobre-assessment-031]

### [cobre-assessment-021] Implement Notification Event Detail API
- **Description**: Add `GET /notification_events/{notification_event_id}` returning event metadata, delivery attempts, redacted payload behavior, and tenant-scoped `404` behavior.
- **Acceptance Criteria**:
  - Response includes attempt count, delivery attempts, final error fields, payload redaction flag, and correlation ID.
  - Missing or cross-client event IDs return `404`; missing payload scope with `include_payload=true` returns `403`.
- **Depends on**: [cobre-assessment-007], [cobre-assessment-008], [cobre-assessment-009], [cobre-assessment-020]
- **Blocks**: [cobre-assessment-024], [cobre-assessment-025], [cobre-assessment-029], [cobre-assessment-031]

### [cobre-assessment-022] Implement Create Replay API
- **Description**: Add `POST /notification_events/{notification_event_id}/replays` using replay service, tenant scope, replay scope, idempotency key, and documented response shape.
- **Acceptance Criteria**:
  - Successful replay requests return `202` with `notification_event_id`, `replay_id`, `replay_status`, `created_at`, message, and correlation ID.
  - Completed events return `400`, missing replay scope returns `403`, cross-client IDs return `404`, and idempotency conflicts return `409`.
- **Depends on**: [cobre-assessment-008], [cobre-assessment-019]
- **Blocks**: [cobre-assessment-024], [cobre-assessment-025], [cobre-assessment-029], [cobre-assessment-031]

### [cobre-assessment-023] Implement Replay Status API
- **Description**: Add `GET /notification_events/{notification_event_id}/replays/{replay_id}` with tenant scope and replay status response.
- **Acceptance Criteria**:
  - Response includes replay status, requested/start/finish timestamps, attempt count, final error code, and correlation ID.
  - Missing, mismatched, or cross-client replay resources return `404`.
- **Depends on**: [cobre-assessment-008], [cobre-assessment-019]
- **Blocks**: [cobre-assessment-024], [cobre-assessment-025], [cobre-assessment-029], [cobre-assessment-031]

### [cobre-assessment-024] Implement API Rate Limiting
- **Description**: Add per-client and per-token rate limiting for list, detail, and replay endpoints using a Spring-compatible limiter such as Bucket4j.
- **Acceptance Criteria**:
  - Rate limit keys include authenticated client and token identity without using user-supplied `client_id`.
  - Limit violations return `429` with the standard error shape and correlation ID.
- **Depends on**: [cobre-assessment-008], [cobre-assessment-020], [cobre-assessment-021], [cobre-assessment-022], [cobre-assessment-023]
- **Blocks**: [cobre-assessment-029], [cobre-assessment-031]

### [cobre-assessment-025] Add Observability Instrumentation
- **Description**: Add structured logging, Micrometer metrics, and OpenTelemetry tracing across API requests, ingestion, outbox relay, workers, and replay flows.
- **Acceptance Criteria**:
  - Logs include correlation ID, notification ID, source event ID, event type, delivery status, HTTP status, outbox ID, and replay ID while redacting restricted and secret fields.
  - Metrics avoid raw high-cardinality `client_id` labels and include global success rate, retry depth, webhook timeout count, dead-letter count, replay requests, and stale outbox rows.
- **Depends on**: [cobre-assessment-013], [cobre-assessment-014], [cobre-assessment-016], [cobre-assessment-019], [cobre-assessment-020], [cobre-assessment-021], [cobre-assessment-022], [cobre-assessment-023]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-026] Implement Retention Cleanup Jobs
- **Description**: Add scheduled cleanup jobs for raw payloads, delivery attempts, replay jobs, idempotency records, and audit logs using documented retention defaults.
- **Acceptance Criteria**:
  - Raw payload retention is 90 days, attempts and replay jobs are 180 days, idempotency keys are 24 hours, and audit logs are 1 year by default.
  - Cleanup preserves searchable metadata and does not delete active pending, in-progress, retrying, or replay-in-progress work.
- **Depends on**: [cobre-assessment-004], [cobre-assessment-009]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-027] Add Domain Unit Tests
- **Description**: Add unit tests for state transitions, retry classification, backoff horizon, replay idempotency, and payload redaction behavior.
- **Acceptance Criteria**:
  - Tests cover illegal delivery transitions, terminal-state protection, expired lease decisions, and duplicate replay idempotency keys.
  - Tests verify three-day retry horizon, jitter bounds, and transient versus permanent failure classification.
- **Depends on**: [cobre-assessment-015], [cobre-assessment-017], [cobre-assessment-019]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-028] Add Repository And Migration Tests
- **Description**: Add Testcontainers-backed PostgreSQL tests for Flyway migrations, constraints, indexes, tenant-scoped queries, and encrypted payload persistence.
- **Acceptance Criteria**:
  - Tests prove duplicate `(source_event_id, client_id, event_type)` and replay idempotency keys cannot create duplicate rows.
  - Tests prove tenant-scoped queries do not return cross-client notification events or replay jobs.
- **Depends on**: [cobre-assessment-004], [cobre-assessment-007], [cobre-assessment-009]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-029] Add API Integration Tests
- **Description**: Add Spring Boot integration tests for list, detail, create replay, replay status, validation errors, authorization, and rate-limit behavior.
- **Acceptance Criteria**:
  - Tests cover `CLIENT001`, `CLIENT002`, and `CLIENT003` visibility, failed-status filters, malformed timestamps, invalid status, oversized page size, and redacted payload defaults.
  - Tests cover `403`, `404`, `409`, and `429` scenarios with the documented error shape and correlation ID.
- **Depends on**: [cobre-assessment-020], [cobre-assessment-021], [cobre-assessment-022], [cobre-assessment-023], [cobre-assessment-024]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-030] Add Worker And Outbox Integration Tests
- **Description**: Add integration tests for outbox relay, RabbitMQ delivery messages, webhook worker outcomes, retry scheduling, lease recovery, and dead-letter behavior.
- **Acceptance Criteria**:
  - Tests prove a persisted notification without an initially published message is eventually published by the outbox relay.
  - Tests prove `2xx`, `429`, `5xx`, timeout, `4xx`, blocked URL, duplicate message, and expired lease scenarios produce the expected state and attempt history.
- **Depends on**: [cobre-assessment-013], [cobre-assessment-016], [cobre-assessment-017], [cobre-assessment-018]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-031] Add Security Acceptance Tests
- **Description**: Add security-focused tests for tenant isolation, replay scope enforcement, payload scope enforcement, SSRF protection, signature headers, and sensitive-data redaction.
- **Acceptance Criteria**:
  - Tests prove `CLIENT001` cannot list, fetch, request payloads for, or replay `CLIENT002` events.
  - Tests prove localhost, RFC1918, link-local, multicast, and metadata webhook URLs are rejected and logs/errors do not expose secrets, raw payloads, SQL details, or private network details.
- **Depends on**: [cobre-assessment-010], [cobre-assessment-014], [cobre-assessment-020], [cobre-assessment-021], [cobre-assessment-022], [cobre-assessment-023], [cobre-assessment-024]
- **Blocks**: [cobre-assessment-032]

### [cobre-assessment-032] Add Docker Compose End-To-End Smoke Test
- **Description**: Add a local smoke test script that starts Docker Compose, loads seed data, sends a sample platform event, verifies webhook delivery attempts, and triggers replay.
- **Acceptance Criteria**:
  - Script validates API service, worker service, outbox relay, PostgreSQL, RabbitMQ, and mock webhook receiver are all functional locally.
  - Script verifies the no-ordering guarantee is documented while duplicate delivery handling works through `X-Cobre-Notification-Id`.
- **Depends on**: [cobre-assessment-002], [cobre-assessment-005], [cobre-assessment-025], [cobre-assessment-026], [cobre-assessment-027], [cobre-assessment-028], [cobre-assessment-029], [cobre-assessment-030], [cobre-assessment-031]
- **Blocks**: [cobre-assessment-033]

### [cobre-assessment-033] Document Local Runbook And Implementation Notes
- **Description**: Update project documentation with local commands for starting the stack, running tests, sending sample events, inspecting attempts, and triggering replay.
- **Acceptance Criteria**:
  - Runbook documents Docker Compose startup, Maven test execution, sample event ingestion, delivery-attempt inspection, and replay API examples.
  - Notes explicitly keep test, staging, production topology, multi-region DR, RPO, RTO, partitioning, and sharding out of initial implementation scope.
- **Depends on**: [cobre-assessment-032]
- **Blocks**: None
