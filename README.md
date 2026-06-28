# Cobre Notification Events Solution

Documentation-only response for the Cobre event notification challenge.

The proposal covers a scalable webhook notification capability and a self-service REST API for clients to query and replay notification events. It uses the sample file provided at `/Users/alfredos/Downloads/notification_events.json`, which contains 10 notification events for `CLIENT001`, `CLIENT002`, and `CLIENT003`.

## Contents

- [System design](docs/system-design.md): architecture, delivery flow, retry strategy, storage, and observability.
- [API design](docs/api-design.md): REST API contracts for listing, fetching, and replaying notification events.
- [Security](docs/security.md): OWASP risks and concrete mitigation controls.
- [AI usage](AI_USAGE.md): documented AI assistance for the challenge.

## Executive Summary

Cobre should model each platform-generated event as a durable `notification_event` owned by exactly one client. Delivery is asynchronous: events are accepted from the platform event stream, validated against an active subscription for the same `client_id`, persisted, and delivered to the subscribed HTTPS webhook endpoint by workers. Delivery attempts are tracked independently, retried with exponential backoff and jitter, and eventually marked as `completed` or terminal `failed`.

The self-service API is scoped by the authenticated client identity. Clients can list their notification events, inspect one event, and request replay only for terminal failed deliveries. Internal teams observe the capability through near real-time metrics, traces, structured logs, dashboards, and alerts.

## Primary Assumptions

- The sample JSON timestamps are treated as event creation or delivery timestamps for documentation examples.
- API authorization is tenant-scoped: the authenticated principal determines `client_id`; callers cannot choose another client through query parameters.
- Webhook endpoints are configured through a subscription management capability, not directly inside the replay endpoint.
- Delivery is at-least-once. Clients must handle duplicate webhook calls using an idempotency key.
- Current implementation detail is documentation-only. If code is later required, the recommended local stack is Java 21, Maven, Spring Boot, embedded persistence seeded from the JSON file, and HTTP test doubles for webhook delivery.

## References

- Spring Boot project: https://spring.io/projects/spring-boot
- OWASP Top 10 project: https://owasp.org/www-project-top-ten/
