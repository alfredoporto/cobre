# AI Usage

This repository was prepared with AI assistance as a productivity complement.

## Use Cases

- Interpreting the challenge statement and converting it into a documentation plan.
- Structuring the system design around event ingestion, subscription validation, durable delivery, retry, persistence, replay, and observability.
- Drafting Mermaid diagrams for context, container-level flow, and delivery sequence.
- Drafting REST API contracts and sample responses based on `notification_events.json`.
- Mapping API and webhook risks to OWASP-style security controls.

## Prompt Summary

Detailed prompt history is tracked in [docs/prompt-log.md](docs/prompt-log.md).

Initial prompt context included:

```text
Design and document a Cobre platform feature that delivers event notifications by webhook,
stores final delivery state, supports retry, exposes a self-service REST API, and identifies
OWASP Top 10 risks and mitigations.
```

Follow-up direction:

```text
Implement the docs-only plan in the empty /Users/alfredos/coding-box/cobre workspace.
```

## Human Review Notes

- The final architectural decisions should be reviewed against Cobre's actual platform standards for broker technology, database, identity provider, and observability stack.
- The retry timings and attempt limits are proposed defaults and should be tuned using real delivery volume, endpoint behavior, and support expectations.
- The API examples use the supplied JSON fields and should be aligned with the final domain naming used by Cobre.
- Webhook SSRF protections should be validated with the infrastructure and networking teams because application-level URL validation alone is not sufficient.

## Source Data

The sample event data came from:

```text
/Users/alfredos/Downloads/notification_events.json
```
