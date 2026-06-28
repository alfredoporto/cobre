# Security Proposal

The public API and webhook delivery capability should be designed against the OWASP Top 10. The current OWASP project reference is https://owasp.org/www-project-top-ten/.

## 1. Broken Access Control

Risk:

- A client could query or replay another client's notification event by guessing `notification_event_id`.
- Filtering by a user-provided `client_id` could expose cross-tenant data.
- Replay could be abused to trigger deliveries for events the caller does not own.

Mitigations:

- Derive `client_id` exclusively from the authenticated principal, not from request parameters.
- Apply tenant scope in every repository query: lookup by `notification_event_id` and authenticated `client_id`.
- Return `404` for missing or cross-client events.
- Require granular scopes: `notification-events:read` and `notification-events:replay`.
- Audit every detail lookup and replay request with caller, client, event ID, decision, and correlation ID.
- Add authorization tests for cross-client list, detail, and replay attempts.

## 2. Security Misconfiguration And SSRF In Webhook Delivery

Risk:

- Webhook delivery accepts client-controlled URLs, creating SSRF exposure if private IP ranges, metadata services, or internal hostnames are reachable.
- Weak TLS settings or permissive redirects could leak payloads or credentials.
- Misconfigured error responses could expose internal network details.

Mitigations:

- Accept only `https://` webhook URLs during subscription setup.
- Resolve DNS and block private, loopback, link-local, multicast, and cloud metadata IP ranges before delivery.
- Re-check the resolved address at delivery time to reduce DNS rebinding risk.
- Disable redirects or re-validate every redirect target with the same policy.
- Use a dedicated egress network path with firewall rules that cannot reach internal services.
- Enforce TLS certificate validation and modern protocol versions.
- Keep webhook signing secrets in a secret manager, not in application config or logs.
- Log only normalized failure classifications, not full internal connection details.

Note: SSRF is explicitly listed in OWASP Top 10:2021 as A10. Even when using the current Top 10 categories, webhook URL handling should still be treated as a first-class design risk.

## 3. Injection

Risk:

- Query filters, sort parameters, IDs, and payload fields could be used for SQL, NoSQL, log, or expression injection.
- Unvalidated webhook responses could poison logs or dashboards.

Mitigations:

- Use parameterized queries or repository criteria APIs only.
- Validate `delivery_status` against an enum.
- Validate timestamps with strict ISO-8601 parsing.
- Restrict page size and reject unknown or unsupported sort fields.
- Validate event IDs with an allowlisted pattern.
- Use structured logging with encoded fields; do not concatenate raw user input into log messages.
- Avoid evaluating client-provided expressions, templates, or callback data.
- Use schema validation for platform event payloads before persistence and delivery.

## 4. Security Logging And Monitoring Failures

Risk:

- Missing logs and alerts would delay incident response for cross-tenant access attempts, replay abuse, delivery outages, or webhook attack patterns.
- Excessive payload logging could create a confidentiality incident.

Mitigations:

- Emit audit logs for authentication failures, authorization denials, replay requests, subscription changes, and delivery terminal failures.
- Include correlation IDs across API, ingestion, queue, and delivery worker logs.
- Alert on repeated authorization failures, replay spikes, delivery failure-rate anomalies, and dead-letter queue growth.
- Redact sensitive payload fields, webhook signing secrets, tokens, and full response bodies.
- Retain audit logs according to compliance and support requirements.

## Additional Controls

- Rate limit the public API per client and per token.
- Require an `Idempotency-Key` for replay requests.
- Sign webhook requests with HMAC, for example `X-Cobre-Signature`, over timestamp plus request body.
- Include a timestamp header and require clients to reject old signatures.
- Rotate webhook signing secrets.
- Run dependency scanning and container image scanning in CI.
- Use least-privilege service accounts for database, broker, and secret access.
- Protect administrative subscription changes with stronger authorization and audit review.

## Security Acceptance Tests

- `CLIENT001` cannot list, fetch, or replay `CLIENT002` events.
- Replay without `notification-events:replay` scope returns `403`.
- Replay for a completed event returns `400`.
- Webhook URLs pointing to `localhost`, RFC1918 ranges, link-local ranges, or metadata endpoints are rejected.
- Invalid `delivery_status`, malformed timestamps, and oversized page sizes return `400`.
- API errors never include stack traces, secrets, SQL details, or private network information.
