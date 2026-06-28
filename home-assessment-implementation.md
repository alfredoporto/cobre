# Cobre Home Assessment Implementation Plan

## Summary

Implement a balanced runnable slice plus production architecture documentation. The code proves the required API and webhook replay behavior locally; the docs and diagram explain the scalable outbox/RabbitMQ design.

## Key Deliverables

- Spring Boot Java 21 service using hexagonal boundaries.
- Required endpoints:
  - `GET /notification_events`
  - `GET /notification_events/{notification_event_id}`
  - `POST /notification_events/{notification_event_id}/replay`
- Fixture-backed repository using `notification_events.json`.
- Local header auth with `X-Client-Id` and `X-Scopes`.
- Webhook delivery adapter with request headers, timeouts, transient/permanent classification, bounded retry, and stored attempts.
- API, webhook, and security tests.
- README, API docs, OpenAPI, security notes, AI usage log, and architecture diagram.

## Implementation Choices

- Local storage is in-memory and seeded from `src/main/resources/notification_events.json`.
- Replay is synchronous in the local slice so it is easy to demo and test.
- Production replay should become an async `replay_job` behind transactional outbox and workers.
- Local auth is header-based; production should use OAuth2 client credentials or signed JWTs.
- Full PostgreSQL/RabbitMQ/Flyway/outbox relay implementation remains design-only for this assessment submission.

## Acceptance Checklist

- `mvn test` passes.
- README no longer describes a docs-only solution.
- Singular replay route is implemented.
- Cross-client list/detail/replay access is blocked.
- Failed events can be replayed and store a new attempt.
- Completed events cannot be replayed.
- OpenAPI documents the implemented routes.
- Security document covers OWASP risks and mitigations.
- AI usage is documented.
- Commit history separates implementation, tests, docs, and cleanup.
