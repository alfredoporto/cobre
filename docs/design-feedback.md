# Design Feedback

## Executive Summary

The proposed Cobre notification architecture is healthy for a home assessment and the stated volume of about 100k events per day. The core direction is correct: asynchronous ingestion, durable notification state, tenant-scoped API access, webhook signing, retry with backoff, replay, auditability, and OWASP-oriented controls.

The most important gaps are not raw database scale. At the assumed volume, a single relational database with good indexes is enough. The higher-risk areas are correctness and operability: atomic event persistence, duplicate suppression, replay semantics, worker crash recovery, sensitive data handling, and explicit state transitions.

The design should keep the initial infrastructure simple and Docker Compose friendly, but document a clear growth path: transactional outbox first, then queue isolation and read replicas, then table partitioning, then service/database decomposition only when measured growth justifies it.

## Assumptions Applied

- Average volume is about 100k notification events per day.
- Initial database scaling should avoid partitioning and sharding; document those only as future growth options.
- Delivery behavior should follow a Stripe-like standard: first attempt promptly, automatic retries for up to three days with exponential backoff, manual replay support, and no hard ordering guarantee.
- Event ordering is not mandatory for the first version; it can become a future enhancement.
- Local topology is fully Dockerized and runnable with Docker Compose.
- No test, staging, or production deployment topology is required for the assessment.
- Security should follow OWASP guidance and reasonable data protection controls for sensitive financial data.
- Subscription management should be the simplest useful model.
- Multi-region, disaster recovery, RPO, and RTO are deployment concerns handled by cloud vendor capabilities later, but the architecture should preserve compatibility with that path.

Reference used for the Stripe-like baseline: https://docs.stripe.com/webhooks

## Critical Flaws & Risks

### 1. Persist-then-enqueue can lose deliveries

The current flow persists a `notification_event` and then enqueues a delivery command as separate steps. If the process crashes after the database write but before queue publish, the event remains `pending` without a delivery job.

Why this matters:

- The system can silently violate delivery guarantees even at low volume.
- Retries do not help if the first delivery command was never created.
- Support teams will see stale `pending` events without a precise failure cause.

Improvement:

- Use a transactional outbox.
- Persist `notification_event` and `delivery_outbox` rows in the same database transaction.
- Run an outbox relay process that publishes pending outbox rows to the delivery queue and marks them as published.
- Add an operational metric for old unpublished outbox rows.

Trade-off:

- This adds one table and one background process.
- It is still simple enough for Docker Compose and prevents a class of lost-delivery bugs that are expensive to diagnose later.

### 2. Delivery state transitions are under-specified

The current statuses are directionally useful, but the design does not define legal transitions, concurrency rules, worker leases, or stale `in_progress` recovery.

Why this matters:

- A worker may send a webhook successfully and crash before recording `completed`.
- Another worker may retry the same event, causing duplicate delivery.
- Manual replay may race with automatic retry.
- A permanently stuck `in_progress` row can block the event forever.

Improvement:

- Define an explicit state machine:
  - `pending -> in_progress -> completed`
  - `pending -> in_progress -> retrying -> pending`
  - `pending -> in_progress -> failed`
  - `failed -> replay_requested -> replay_in_progress -> replay_completed`
  - `failed -> replay_requested -> replay_failed`
- Add `lock_version`, `leased_by`, `lease_expires_at`, and `current_attempt_id`.
- Use optimistic locking on updates.
- Add a recovery job that returns expired `in_progress` work to `pending` or `retrying`.

Trade-off:

- More fields and more test cases.
- Much clearer behavior under crashes and concurrent workers.

### 3. Replay semantics blur original event state

The current replay response returns a pending delivery status for a failed notification. That makes it unclear whether replay mutates the original failed event, creates a new attempt, or creates a separate replay job.

Why this matters:

- Audit history becomes ambiguous.
- Clients may see a failed event become pending, then completed, losing the fact that the original delivery failed.
- Automatic retry and manual replay can race without a clear ownership model.

Improvement:

- Keep `notification_event` immutable after terminal delivery.
- Add a separate `replay_job` or `delivery_execution` entity.
- Make replay an explicit resource:
  - `POST /notification_events/{notification_event_id}/replays`
  - `GET /notification_events/{notification_event_id}/replays/{replay_id}`
- Return replay status separately from the original event status.

Trade-off:

- Slightly more complex API.
- Better auditability and easier support investigations.

### 4. Idempotency is acknowledged but not fully designed

The design mentions idempotency headers, but it does not define database uniqueness, idempotency-key retention, duplicate platform event handling, or repeated replay request behavior.

Why this matters:

- At-least-once delivery means duplicates are expected.
- A duplicated source event could create multiple notification rows.
- A client retrying `POST /replay` could create multiple replay jobs.

Improvement:

- Add unique constraint on `(source_event_id, client_id, event_type)`.
- Add unique constraint on `(client_id, idempotency_key)` for replay requests.
- Store idempotency responses for a bounded retention period.
- Include `X-Cobre-Event-Id`, `X-Cobre-Notification-Id`, and `X-Cobre-Delivery-Attempt` on every webhook.
- Document that clients must deduplicate by `X-Cobre-Notification-Id`.

Trade-off:

- Requires idempotency storage and cleanup.
- Removes ambiguity from duplicate request handling.

### 5. Sensitive data exposure needs stronger boundaries

The example API returns human-readable financial content. The security doc correctly avoids logging full payloads, but the API contract still needs rules for what sensitive data can be returned to clients.

Why this matters:

- Notification payloads may contain account numbers, names, transaction references, payment amounts, or other regulated data.
- A tenant-scoped API can still overexpose data to a valid but overly broad token.
- Audit logs and traces can accidentally preserve sensitive payloads for too long.

Improvement:

- Classify payload fields as public, client-confidential, restricted, or secret.
- Store raw payload encrypted at rest.
- Return only the minimum fields required by the self-service use case.
- Add optional `include_payload=true` only for tokens with an elevated read scope if needed.
- Redact account identifiers and personal data in logs, traces, errors, and dashboard labels.
- Define retention periods for payload, attempts, response excerpts, and audit logs.

Trade-off:

- More product and compliance decisions upfront.
- Lower risk of leaking financial data through a convenience API.

### 6. Offset pagination is acceptable for the assessment but weak as the event table grows

The API uses `page` and `size` with total counts. This is acceptable for the assessment and 100k daily events, but it will become inefficient and unstable for large histories.

Why this matters:

- Offset queries get slower as offsets grow.
- New events can cause clients to miss or duplicate records while paging.
- Exact total counts can become expensive.

Improvement:

- Keep page/size for the home assessment if implementation speed matters.
- Document cursor pagination as the production path:
  - Stable order by `(created_at DESC, notification_event_id DESC)`.
  - Return `next_cursor`.
  - Avoid mandatory exact totals.

Trade-off:

- Page/size is easier to implement and test quickly.
- Cursor pagination is better for production event streams.

### 7. Metrics may create high cardinality

The observability plan mentions metrics by client and event type. This is useful, but raw `client_id` labels can overload metrics systems as the client base grows.

Why this matters:

- High-cardinality metrics increase cost and can degrade monitoring performance.
- The issue tends to appear after adoption, when dashboards are already depended upon.

Improvement:

- Use low-cardinality global metrics for core alerts.
- Put `client_id` in structured logs and traces.
- Maintain per-client health views through log queries or curated top-N metrics.
- Allow dedicated per-client metrics only for high-value or high-volume clients.

Trade-off:

- Slightly less convenient for generic metrics dashboards.
- More sustainable observability cost and performance.

## Deep-Dive Trade-off Analysis

### Initial relational database vs partitioning or sharding

Recommendation:

- Start with one relational database, normalized tables, and indexes.

Why:

- 100k events per day is not a sharding problem.
- Assuming 90 days of hot data, the system stores about 9 million notification rows plus attempts. That is well within normal relational database capability if indexes are deliberate and payload storage is controlled.
- Sharding now would increase implementation complexity, test burden, and operational risk without solving the current bottleneck.

Initial indexes:

- `notification_event(client_id, created_at DESC, notification_event_id DESC)`
- `notification_event(client_id, delivery_status, created_at DESC)`
- `notification_event(source_event_id, client_id, event_type)` unique
- `delivery_attempt(notification_event_id, attempt_number)` unique
- `replay_job(notification_event_id, created_at DESC)`
- `idempotency_key(client_id, idempotency_key)` unique
- `delivery_outbox(status, next_attempt_at)`

Growth path:

1. Add read replicas for self-service query traffic.
2. Move old payloads to cold/object storage while retaining searchable metadata.
3. Partition notification tables by month when retention and query volume require it.
4. Isolate high-volume clients into separate queues or worker pools.
5. Consider sharding only when a single database can no longer meet measured write, read, or maintenance requirements.

### Exponential retry window

Recommendation:

- Adopt a Stripe-like delivery target:
  - First delivery attempt as soon as possible after event acceptance.
  - Automatic retries for up to three days.
  - Exponential backoff with jitter.
  - Manual replay available after terminal failure.

Suggested schedule:

- Attempt 1: immediate.
- Attempt 2: about 1 minute.
- Attempt 3: about 5 minutes.
- Attempt 4: about 30 minutes.
- Attempt 5: about 2 hours.
- Attempt 6 and beyond: progressively spaced until the three-day retry horizon is reached.

Why:

- Three days is long enough to cover common client incidents and maintenance windows.
- Exponential backoff protects client endpoints and Cobre infrastructure.
- Jitter avoids synchronized retry spikes.

Trade-off:

- Longer retry windows keep more events active and require careful queue visibility.
- Shorter retry windows reduce operational backlog but produce more manual support/replay work.

### Event ordering

Recommendation:

- Do not guarantee ordering in version 1.
- Document that clients must handle out-of-order and duplicate events.
- Preserve enough metadata to add ordering later.

Why:

- Strict ordering reduces worker parallelism and creates head-of-line blocking when one event fails.
- For webhook systems, eventual delivery and idempotency are usually more valuable than global ordering.

Future option:

- Partition by `(client_id, aggregate_id)` for event types that need per-account or per-transaction ordering.
- Add sequence numbers per aggregate if the platform event stream can provide them.

Trade-off:

- No ordering guarantee is simpler and more scalable.
- Future per-aggregate ordering adds complexity only where business semantics require it.

### Docker Compose topology

Recommendation:

- Keep the local topology explicit and complete:
  - API service
  - Worker service
  - Outbox relay service
  - PostgreSQL
  - Queue broker, such as RabbitMQ or Redis Streams
  - Optional mock webhook receiver
  - Optional observability stack, such as OpenTelemetry collector plus local logs

Why:

- This makes the assessment runnable without cloud dependencies.
- It forces clear service boundaries without needing production infrastructure.

Trade-off:

- Docker Compose does not prove cloud resilience.
- It is appropriate for local development and architecture demonstration.

### Simple subscription model

Recommendation:

- Use one active endpoint per `(client_id, event_type)` for version 1.
- Store `webhook_url`, `signing_secret_ref`, `status`, `enabled_event_type`, and timestamps.
- Validate subscriptions at event creation and snapshot the endpoint used for each delivery execution.

Why:

- It is enough for the challenge and avoids building an admin product prematurely.
- It keeps tenant isolation simple.

Future option:

- Multiple endpoints per client.
- Per-endpoint event filters.
- Secret rotation with overlapping active secrets.
- Endpoint health status and automatic pause.

Trade-off:

- One endpoint per event type is less flexible.
- It is substantially easier to reason about and test.

### Multi-region, DR, RPO, and RTO

Recommendation:

- Keep these as deployment architecture notes rather than local implementation requirements.
- Ensure the data model and queue behavior are compatible with managed cloud capabilities later.

Future deployment notes:

- Use managed database backups, point-in-time recovery, and cross-zone replication.
- Use a managed broker with durable queues.
- Define RPO/RTO when business requirements are known.
- Avoid active-active webhook delivery until duplicate delivery and regional ownership are explicitly designed.

Trade-off:

- Avoids overengineering the assessment.
- Prevents the local design from blocking future cloud resilience.

## Actionable Recommendations

### Phase 1: Make the assessment architecture internally consistent

1. Add a transactional outbox to the system design.
2. Replace ambiguous replay status with a dedicated `replay_job` model.
3. Define the delivery state machine and legal transitions.
4. Add idempotency and uniqueness constraints to the data model.
5. State explicitly that event ordering is not guaranteed.
6. Define the Stripe-like three-day automatic retry horizon.
7. Keep the initial database unpartitioned and unsharded.

### Phase 2: Tighten the API contract

1. Change replay endpoint response to return `replay_status`, not overwrite `delivery_status`.
2. Add idempotency-key behavior:
   - Same client plus same key returns the same replay response.
   - Same key with different request body returns `409`.
3. Document cursor pagination as the future production API.
4. Add payload visibility rules and optional redaction.
5. Add `correlation_id` to every response.

### Phase 3: Strengthen security and compliance posture

1. Enforce HTTPS-only webhook URLs.
2. Block private, loopback, link-local, multicast, and metadata IPs.
3. Re-resolve DNS at delivery time to reduce DNS rebinding risk.
4. Disable redirects or revalidate redirect targets.
5. Sign webhook payloads with HMAC over timestamp plus raw body.
6. Require clients to reject old signatures.
7. Encrypt sensitive payloads at rest.
8. Redact sensitive fields in logs, traces, errors, dashboards, and response excerpts.
9. Define retention periods for payloads, attempts, idempotency keys, and audit logs.

### Phase 4: Define local run topology

1. Add `docker-compose.yml` services for API, worker, outbox relay, database, queue, and mock webhook.
2. Seed sample notification events and subscriptions.
3. Provide local commands for:
   - start stack
   - run tests
   - send sample platform event
   - inspect delivery attempts
   - trigger replay
4. Keep deployment to test/prod explicitly out of scope for the home assessment.

### Phase 5: Document future scale path

1. Start with indexes and retention.
2. Add read replicas if the self-service API becomes read-heavy.
3. Add queue isolation and per-client worker limits for noisy clients.
4. Add monthly table partitioning when retention and maintenance require it.
5. Consider sharding only after measured database saturation.

## Proposed Data Model Adjustments

### `subscription`

- `subscription_id`
- `client_id`
- `event_type`
- `webhook_url`
- `signing_secret_ref`
- `status`
- `created_at`
- `updated_at`

Constraint:

- Unique active subscription per `(client_id, event_type)`.

### `notification_event`

- `notification_event_id`
- `source_event_id`
- `client_id`
- `event_type`
- `payload_ref` or encrypted `payload`
- `payload_hash`
- `created_at`
- `delivery_status`
- `attempt_count`
- `last_attempt_at`
- `next_retry_at`
- `finalized_at`
- `final_error_code`
- `final_error_message`
- `lock_version`

Constraint:

- Unique `(source_event_id, client_id, event_type)`.

### `delivery_attempt`

- `attempt_id`
- `notification_event_id`
- `delivery_execution_id`
- `attempt_number`
- `started_at`
- `finished_at`
- `http_status`
- `result`
- `error_code`
- `latency_ms`
- `response_excerpt_hash`
- `endpoint_snapshot`

Constraint:

- Unique `(notification_event_id, delivery_execution_id, attempt_number)`.

### `replay_job`

- `replay_id`
- `notification_event_id`
- `client_id`
- `idempotency_key`
- `replay_status`
- `requested_at`
- `started_at`
- `finished_at`
- `requested_by`
- `endpoint_snapshot`
- `final_error_code`

Constraint:

- Unique `(client_id, idempotency_key)`.

### `delivery_outbox`

- `outbox_id`
- `notification_event_id`
- `job_type`
- `status`
- `available_at`
- `published_at`
- `attempt_count`
- `last_error`
- `created_at`

Index:

- `(status, available_at)`.

## Acceptance Criteria For Another Agent

- No delivery can be persisted without a recoverable delivery job.
- A worker crash after webhook send can cause a duplicate, but cannot corrupt state.
- Replay creates a separate replay resource and preserves original delivery history.
- The same replay idempotency key cannot create multiple replay jobs.
- Cross-tenant list, detail, and replay attempts return `404` or `403` as appropriate.
- Webhook URLs to private networks, localhost, link-local ranges, and metadata endpoints are rejected.
- The system can be started locally through Docker Compose.
- The architecture explicitly states that ordering is not guaranteed in version 1.
- The design documents the future path for partitioning or sharding without using it initially.

## Missing Context Still Worth Confirming

- Exact retention period for notification payloads and delivery attempts.
- Whether event payloads contain regulated personal data, bank account data, or payment credentials.
- Required manual replay availability window.
- Whether clients need one webhook endpoint per event type or one endpoint for all events.
- Whether the source platform provides aggregate IDs or sequence numbers for future ordering support.
