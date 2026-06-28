# Self-Service API Design

## Authentication And Tenant Scope

All endpoints require authentication. The authenticated principal determines the caller's `client_id`; `client_id` is not accepted as a query parameter. Returning `404` for missing or cross-client resources avoids leaking whether another client's event exists.

Recommended authorization:

- OAuth2 client credentials or signed JWTs for machine-to-machine API clients.
- Scopes such as `notification-events:read`, `notification-events:payload:read`, and `notification-events:replay`.
- Per-client and per-token rate limits for list, detail, and replay endpoints.
- A `correlation_id` in every response and error response.

## Resource Shape

Notification event responses return operational metadata by default. Raw notification payloads can contain financial and personal data, so the API only returns redacted summaries unless the caller passes `include_payload=true` and has the elevated `notification-events:payload:read` scope.

Default event shape:

```json
{
  "notification_event_id": "EVT003",
  "event_type": "credit_transfer",
  "content_summary": "Bank transfer received from Account ****4567 for $1,500.00",
  "payload_redacted": true,
  "created_at": "2024-03-15T11:20:18Z",
  "delivery_status": "failed",
  "client_id": "CLIENT002",
  "attempt_count": 5,
  "last_attempt_at": "2024-03-15T12:20:18Z",
  "next_retry_at": null,
  "final_error_code": "HTTP_500",
  "final_error_message": "Webhook endpoint returned server error"
}
```

Elevated payload response shape:

```json
{
  "notification_event_id": "EVT003",
  "event_type": "credit_transfer",
  "payload": {
    "amount": "1500.00",
    "currency": "USD",
    "account_last4": "4567",
    "counterparty_name": "Redacted in default responses"
  },
  "payload_redacted": false,
  "created_at": "2024-03-15T11:20:18Z",
  "delivery_status": "failed",
  "client_id": "CLIENT002"
}
```

For the sample JSON, `event_id` maps to `notification_event_id`, `delivery_date` maps to `created_at`, and `delivery_status` is either `completed` or `failed`.

Delivery status values:

- `pending`: event is accepted and waiting for delivery.
- `in_progress`: a worker is actively delivering it.
- `retrying`: a transient failure occurred and another automatic attempt is scheduled.
- `completed`: the webhook endpoint returned a successful response.
- `failed`: retries were exhausted or a permanent failure occurred.

Replay status is tracked separately from delivery status.

## Pagination

The home assessment API supports `page` and `size` because it is simple to implement and test at the expected scale. A production event-stream API should move to cursor pagination when histories grow large.

Cursor pagination production path:

- Sort by `(created_at DESC, notification_event_id DESC)`.
- Return `next_cursor` instead of mandatory exact totals.
- Keep cursor results stable while new events are created.

## `GET /notification_events`

Lists notification events for the authenticated client.

Query parameters:

| Name | Required | Description |
| --- | --- | --- |
| `created_from` | No | Inclusive ISO-8601 timestamp lower bound. |
| `created_to` | No | Inclusive ISO-8601 timestamp upper bound. |
| `delivery_status` | No | One of `pending`, `in_progress`, `completed`, `retrying`, `failed`. |
| `include_payload` | No | Defaults to `false`; requires `notification-events:payload:read` when `true`. |
| `page` | No | Zero-based page number. Default `0`. |
| `size` | No | Page size. Default `20`, maximum `100`. |

Example:

```http
GET /notification_events?delivery_status=failed&created_from=2024-03-15T00:00:00Z&created_to=2024-03-15T23:59:59Z&page=0&size=20
Authorization: Bearer <token>
```

Response:

```json
{
  "data": [
    {
      "notification_event_id": "EVT003",
      "event_type": "credit_transfer",
      "content_summary": "Bank transfer received from Account ****4567 for $1,500.00",
      "payload_redacted": true,
      "created_at": "2024-03-15T11:20:18Z",
      "delivery_status": "failed",
      "client_id": "CLIENT002"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "total_elements": 1,
    "total_pages": 1
  },
  "correlation_id": "9c75299d4b3e4ff5"
}
```

Expected behavior:

- `CLIENT001` sees `EVT001`, `EVT002`, `EVT007`, and `EVT010`.
- `CLIENT002` sees `EVT003`, `EVT004`, and `EVT008`.
- `CLIENT003` sees `EVT005`, `EVT006`, and `EVT009`.
- Filtering by `delivery_status=failed` returns `EVT003` for `CLIENT002` and `EVT005`, `EVT009` for `CLIENT003`.

## `GET /notification_events/{notification_event_id}`

Returns details for one notification event owned by the authenticated client.

Example:

```http
GET /notification_events/EVT003
Authorization: Bearer <token>
```

Response:

```json
{
  "notification_event_id": "EVT003",
  "event_type": "credit_transfer",
  "content_summary": "Bank transfer received from Account ****4567 for $1,500.00",
  "payload_redacted": true,
  "created_at": "2024-03-15T11:20:18Z",
  "delivery_status": "failed",
  "client_id": "CLIENT002",
  "attempt_count": 5,
  "delivery_attempts": [
    {
      "attempt_id": "ATT-20240315-0005",
      "attempt_number": 5,
      "delivery_execution_id": "DEX-20240315-0003",
      "started_at": "2024-03-15T12:20:18Z",
      "finished_at": "2024-03-15T12:20:21Z",
      "result": "failed",
      "http_status": 500,
      "latency_ms": 3000,
      "response_excerpt_hash": "sha256:4c03..."
    }
  ],
  "final_error_code": "HTTP_500",
  "final_error_message": "Webhook endpoint returned server error",
  "correlation_id": "9c75299d4b3e4ff5"
}
```

Status codes:

| Status | Meaning |
| --- | --- |
| `200` | Event found and belongs to caller. |
| `401` | Missing or invalid authentication. |
| `403` | Authenticated but missing required scope. |
| `404` | Event does not exist or belongs to another client. |

## `POST /notification_events/{notification_event_id}/replays`

Requests a replay for a terminal failed notification event. Replay is modeled as a separate resource so the original notification's failed delivery history remains immutable and auditable.

The request requires `Idempotency-Key`. The same client using the same key for the same request receives the original replay response. The same key with a different request body returns `409`.

Example:

```http
POST /notification_events/EVT003/replays
Authorization: Bearer <token>
Idempotency-Key: 3ee2e36e-84ab-4a76-86fb-c6d31115ef12
```

Response:

```json
{
  "notification_event_id": "EVT003",
  "replay_id": "RPL-20240315-0001",
  "replay_status": "pending",
  "created_at": "2024-03-15T12:45:00Z",
  "message": "Replay accepted",
  "correlation_id": "9c75299d4b3e4ff5"
}
```

Status codes:

| Status | Meaning |
| --- | --- |
| `202` | Replay accepted. |
| `400` | Event is not in a replayable state, for example `completed`. |
| `401` | Missing or invalid authentication. |
| `403` | Missing replay scope. |
| `404` | Event does not exist or belongs to another client. |
| `409` | Replay already in progress, or idempotency key reused with a different request body. |
| `429` | Rate limit exceeded. |

Replay rules:

- Only `failed` events are replayable.
- Completed events such as `EVT001`, `EVT002`, and `EVT010` are rejected with `400`.
- Failed events such as `EVT003`, `EVT005`, and `EVT009` are accepted only for the owning client.
- Replay uses the current active subscription endpoint so clients can fix a broken endpoint before replaying.
- Each replay attempt stores an endpoint snapshot for auditability.
- Automatic retry and manual replay cannot own the same delivery execution at the same time.

Replay status values:

- `pending`: replay has been accepted and is waiting for delivery.
- `in_progress`: a worker is actively delivering the replay.
- `completed`: replay delivery succeeded.
- `failed`: replay delivery exhausted its attempts or hit a permanent failure.

## `GET /notification_events/{notification_event_id}/replays/{replay_id}`

Returns the replay job status for one replay owned by the authenticated client.

Example:

```http
GET /notification_events/EVT003/replays/RPL-20240315-0001
Authorization: Bearer <token>
```

Response:

```json
{
  "notification_event_id": "EVT003",
  "replay_id": "RPL-20240315-0001",
  "replay_status": "completed",
  "requested_at": "2024-03-15T12:45:00Z",
  "started_at": "2024-03-15T12:45:02Z",
  "finished_at": "2024-03-15T12:45:04Z",
  "attempt_count": 1,
  "final_error_code": null,
  "correlation_id": "9c75299d4b3e4ff5"
}
```

## Webhook Delivery Headers

Every webhook delivery includes stable identifiers so clients can deduplicate at-least-once deliveries:

| Header | Description |
| --- | --- |
| `X-Cobre-Event-Id` | Source platform event ID. |
| `X-Cobre-Notification-Id` | Notification event ID; clients should deduplicate by this value. |
| `X-Cobre-Delivery-Attempt` | Attempt number for the notification or replay execution. |
| `X-Cobre-Timestamp` | Unix timestamp used for signature freshness checks. |
| `X-Cobre-Signature` | HMAC signature over timestamp plus raw request body. |

## Validation Rules

- `created_from` and `created_to` must be valid ISO-8601 timestamps.
- `created_from` must be less than or equal to `created_to`.
- `delivery_status` must be from the allowed enum.
- `include_payload=true` requires `notification-events:payload:read`.
- `size` must be between `1` and `100`.
- `notification_event_id` and `replay_id` must match supported identifier formats and be looked up with tenant scope.
- `Idempotency-Key` is required for replay requests and is unique per client for its retention period.

## Error Shape

```json
{
  "error": {
    "code": "INVALID_FILTER",
    "message": "delivery_status must be one of pending, in_progress, completed, retrying, failed",
    "correlation_id": "9c75299d4b3e4ff5"
  }
}
```

Error responses should be stable and avoid exposing internal stack traces, webhook secrets, private endpoint details, full payloads, or another client's identifiers.
