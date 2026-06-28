# Security Proposal

The public API and webhook delivery capability should be designed against the OWASP Top 10. The current OWASP project reference is https://owasp.org/www-project-top-ten/.

## 1. Broken Access Control

Risk:

- A client could query or replay another client's notification event by guessing `notification_event_id`.
- Filtering by a user-provided `client_id` could expose cross-tenant data.
- Replay could be abused to trigger deliveries for events the caller does not own.
- A valid token with broad read access could overexpose sensitive payload data.

Mitigations:

- Derive `client_id` exclusively from the authenticated principal, not from request parameters.
- Apply tenant scope in every repository query: lookup by `notification_event_id` and authenticated `client_id`.
- Return `404` for missing or cross-client events.
- Require granular scopes: `notification-events:read`, `notification-events:payload:read`, and `notification-events:replay`.
- Return redacted event summaries by default. Production can add explicit payload access behind an elevated scope.
- Audit every detail lookup, payload access, and replay request with caller, client, event ID, decision, and correlation ID.
- Add authorization tests for cross-client list, detail, payload, and replay attempts.

## 2. Sensitive Data Exposure

Risk:

- Notification payloads may contain account identifiers, names, transaction references, payment amounts, or regulated financial data.
- Logs, traces, response excerpts, dashboards, and support tooling can accidentally preserve sensitive fields.
- Long retention of raw payloads increases breach impact.

Payload classification:

| Class | Examples | Handling |
| --- | --- | --- |
| Public | Event type, timestamps, delivery status | Safe for standard API responses and metrics. |
| Client-confidential | Client IDs, endpoint health, delivery metadata | Tenant scoped; safe for authenticated client support views. |
| Restricted | Names, account identifiers, transaction references, raw payload fields | Encrypt at rest; redact by default; require elevated scope for access. |
| Secret | Tokens, webhook signing secrets, private keys | Store only in a secret manager; never return or log. |

Mitigations:

- Store raw payloads encrypted at rest, or store `payload_ref` pointing to encrypted object storage.
- Store `payload_hash` for troubleshooting duplicate payloads without exposing content.
- Return only minimum metadata and redacted summaries in standard API responses.
- Redact account identifiers, personal data, secrets, tokens, private URLs, and full response bodies in logs, traces, errors, alerts, and dashboard labels.
- Store webhook response excerpts as hashes or tightly bounded redacted snippets.
- Prevent stack traces, SQL details, private network details, secrets, and raw payloads from appearing in API errors.

Retention defaults:

| Data | Retention |
| --- | --- |
| Raw notification payload | 90 days |
| Delivery attempts and replay jobs | 180 days |
| Idempotency keys and stored responses | 24 hours |
| Audit logs | 1 year |

## 3. Security Misconfiguration And SSRF In Webhook Delivery

Risk:

- Webhook delivery accepts client-controlled URLs, creating SSRF exposure if private IP ranges, metadata services, or internal hostnames are reachable.
- Weak TLS settings or permissive redirects could leak payloads or credentials.
- DNS rebinding could pass setup validation but resolve to a blocked address at delivery time.
- Misconfigured error responses could expose internal network details.

Mitigations:

- Accept only `https://` webhook URLs during subscription setup.
- Resolve DNS and block private, loopback, link-local, multicast, and cloud metadata IP ranges before subscription activation.
- Re-resolve and re-check the destination address at delivery time.
- Disable redirects, or re-validate every redirect target with the same policy before following it.
- Use a dedicated egress network path with firewall rules that cannot reach internal services.
- Enforce TLS certificate validation and modern protocol versions.
- Keep webhook signing secrets in a secret manager, not in application config or logs.
- Log only normalized failure classifications, not full internal connection details.

Note: SSRF is explicitly listed in OWASP Top 10:2021 as A10. Even when using the current Top 10 categories, webhook URL handling should still be treated as a first-class design risk.

## 4. Injection

Risk:

- Query filters, sort parameters, IDs, and payload fields could be used for SQL, NoSQL, log, or expression injection.
- Unvalidated webhook responses could poison logs or dashboards.
- Error messages could reflect untrusted input without encoding.

Mitigations:

- Use parameterized queries or repository criteria APIs only.
- Validate `delivery_status` against an enum.
- Validate timestamps with strict ISO-8601 parsing.
- Restrict page size and reject unknown or unsupported sort fields.
- Validate event IDs, replay IDs, and idempotency keys with allowlisted patterns and length limits.
- Use structured logging with encoded fields; do not concatenate raw user input into log messages.
- Avoid evaluating client-provided expressions, templates, or callback data.
- Use schema validation for platform event payloads before persistence and delivery.
- Normalize and redact webhook response details before storing any diagnostic excerpt.

## 5. Identification And Authentication Failures

Risk:

- Weak machine-to-machine authentication could allow unauthorized event access or replay.
- Long-lived tokens without scope boundaries increase the impact of credential leakage.
- Webhook receivers need a reliable way to authenticate that Cobre sent the request.

Mitigations:

- Use OAuth2 client credentials or signed JWTs for API access.
- Require short token lifetimes and granular scopes.
- Sign webhook requests with HMAC, for example `X-Cobre-Signature`, over `X-Cobre-Timestamp` plus the raw request body.
- Require clients to reject signatures outside a short freshness window.
- Include `X-Cobre-Event-Id`, `X-Cobre-Notification-Id`, and `X-Cobre-Delivery-Attempt` on every webhook.
- Rotate webhook signing secrets; future support can allow overlapping active secrets during rotation.
- Never log tokens, signing secrets, authorization headers, or generated signatures.

## 6. Security Logging And Monitoring Failures

Risk:

- Missing logs and alerts would delay incident response for cross-tenant access attempts, replay abuse, delivery outages, stale outbox rows, or webhook attack patterns.
- Excessive payload logging could create a confidentiality incident.
- High-cardinality metric labels such as raw `client_id` can degrade monitoring systems as usage grows.

Mitigations:

- Emit audit logs for authentication failures, authorization denials, payload access, replay requests, subscription changes, delivery terminal failures, and idempotency conflicts.
- Include correlation IDs across API, ingestion, outbox relay, queue, and delivery worker logs.
- Alert on repeated authorization failures, replay spikes, delivery failure-rate anomalies, dead-letter queue growth, and stale unpublished outbox rows.
- Use low-cardinality global metrics for core alerts.
- Put `client_id` in structured logs and traces; use curated top-N or dedicated metrics only for high-value or high-volume clients.
- Redact sensitive payload fields, webhook signing secrets, tokens, and full response bodies.
- Retain audit logs for 1 year by default.

## 7. Idempotency And Duplicate Handling

Risk:

- At-least-once delivery means duplicate webhook delivery is expected.
- A duplicate source platform event could create multiple notification rows.
- A client retrying replay requests could create multiple replay jobs.

Mitigations:

- Add a unique constraint on `(source_event_id, client_id, event_type)`.
- Require `Idempotency-Key` for replay requests.
- Add a unique constraint on `(client_id, idempotency_key)` for retained idempotency records.
- Store the replay request hash and response for 24 hours.
- Return the stored response when the same key and request hash are repeated.
- Return `409` when the same key is reused with a different request body.
- Document that clients must deduplicate webhooks by `X-Cobre-Notification-Id`.

## Additional Controls

- Rate limit the public API per client and per token.
- Use per-client delivery throttles so one failing endpoint cannot consume all worker capacity.
- Use worker leases, optimistic locking, and expired lease recovery for crash-safe delivery.
- Run dependency scanning and container image scanning in CI.
- Use least-privilege service accounts for database, broker, and secret access.
- Protect administrative subscription changes with stronger authorization and audit review.
- Review retention settings with compliance before production deployment.

## Security Acceptance Tests

- `CLIENT001` cannot list, fetch, request payloads for, or replay `CLIENT002` events.
- Replay without `notification-events:replay` scope returns `403`.
- Production raw-payload access without an elevated payload scope returns `403`.
- Replay for a completed event returns `400`.
- Reusing the same replay idempotency key with the same request returns the stored response.
- Reusing the same replay idempotency key with a different request returns `409`.
- Webhook URLs pointing to `localhost`, RFC1918 ranges, link-local ranges, multicast ranges, or metadata endpoints are rejected.
- DNS is rechecked at delivery time before connecting to the webhook endpoint.
- Invalid `delivery_status`, malformed timestamps, malformed IDs, and oversized page sizes return `400`.
- API errors never include stack traces, secrets, SQL details, private network information, full payloads, or another client's identifiers.
- Logs, traces, response excerpts, and dashboard labels redact restricted and secret fields.
