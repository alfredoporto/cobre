# Cobre Notification Events Solution

Runnable home-assessment implementation for the Cobre event notification challenge.

The repository contains a focused Spring Boot slice for the required self-service API and webhook replay flow, plus architecture documentation for the production-grade outbox design.

[System Design - Excali](https://excalidraw.com/#json=Of-ig1IP9xI7o15aebJuN,-CkbE_KP_zYC6F3tezEwBA)

## What Is Implemented

- Java 21 Spring Boot service using a hexagonal package structure.
- Fixture-backed notification storage loaded from `src/main/resources/notification_events.json`.
- Tenant-scoped local auth using `X-Client-Id` and `X-Scopes` headers.
- Required REST endpoints:
  - `GET /notification_events`
  - `GET /notification_events/{notification_event_id}`
  - `POST /notification_events/{notification_event_id}/replay`
- Webhook delivery adapter using Java `HttpClient`, strict timeouts, delivery headers, transient/permanent failure classification, and bounded local retries.
- Stored delivery attempt history visible from the detail endpoint.
- Integration and adapter tests for tenant isolation, filters, replay rules, error shape, and retry classification.

## What Is Design-Only

The production architecture uses PostgreSQL, transactional outbox, RabbitMQ, outbox relay, async workers, lease recovery, dead-letter handling, retention jobs, OAuth/JWT, and full observability. Those pieces are documented but intentionally not implemented in the local assessment slice.

## Run And Test

Prerequisites:

- Java 21
- Maven 3.9+

Run tests:

```bash
mvn test
```

Run the app:

```bash
COBRE_WEBHOOK_URL=https://example.com/webhook mvn spring-boot:run
```

The default webhook URL is `http://localhost:8089/webhook`. If no receiver is running there, replay requests are still accepted but the stored attempt is marked failed.

## Demo Requests

List events for `CLIENT002`:

```bash
curl -s 'http://localhost:8080/notification_events' \
  -H 'X-Client-Id: CLIENT002' \
  -H 'X-Scopes: notification-events:read'
```

Filter failed events:

```bash
curl -s 'http://localhost:8080/notification_events?delivery_status=failed' \
  -H 'X-Client-Id: CLIENT003' \
  -H 'X-Scopes: notification-events:read'
```

Fetch one event:

```bash
curl -s 'http://localhost:8080/notification_events/EVT003' \
  -H 'X-Client-Id: CLIENT002' \
  -H 'X-Scopes: notification-events:read'
```

Replay a failed event:

```bash
curl -s -X POST 'http://localhost:8080/notification_events/EVT003/replay' \
  -H 'X-Client-Id: CLIENT002' \
  -H 'X-Scopes: notification-events:read,notification-events:replay'
```

Cross-client access returns `404`:

```bash
curl -i 'http://localhost:8080/notification_events/EVT003' \
  -H 'X-Client-Id: CLIENT001' \
  -H 'X-Scopes: notification-events:read'
```

## Assessment Materials

- [System design](docs/system-design.md): scalable production architecture, outbox flow, retries, storage, and observability.
- [API design](docs/api-design.md): REST behavior and response examples.
- [OpenAPI contract](docs/openapi.yaml): machine-readable API contract for the implemented endpoints.
- [Architecture diagram](docs/architecture.svg): viewable production design visual.
- [Excalidraw source](docs/architecture.excalidraw): editable diagram source.
- [Security](docs/security.md): OWASP risks and mitigations.
- [Implementation plan](home-assessment-implementation.md): scoped plan used to build this submission.
- [AI usage](AI_USAGE.md): documented AI assistance.
- [Prompt log](docs/prompt-log.md): detailed prompt trail and human validation notes.

## Primary Assumptions

- The sample JSON timestamps are treated as event creation timestamps.
- The local slice uses header auth for demo speed; production should use OAuth2 client credentials or signed JWTs.
- The local repository is in-memory and resets on restart.
- The local replay path processes synchronously; production should enqueue replay work through the outbox and worker path.
- Webhook URLs are configurable because the final URL may be provided during presentation.
