# Self-Service API Design

## Authentication And Tenant Scope

All endpoints require authentication. The authenticated principal determines the caller's `client_id`; `client_id` is not accepted as a query parameter. Returning `404` for missing or cross-client resources avoids leaking whether another client's event exists.

Recommended authorization:

- OAuth2 client credentials or signed JWTs for machine-to-machine API clients.
- Scopes such as `notification-events:read` and `notification-events:replay`.
- Per-client rate limits for list and replay endpoints.

## Resource Shape

```json
{
  "notification_event_id": "EVT003",
  "event_type": "credit_transfer",
  "content": "Bank transfer received from Account #4567 for $1,500.00",
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

For the sample JSON, `event_id` maps to `notification_event_id`, `delivery_date` maps to `created_at`, and `delivery_status` is either `completed` or `failed`.

## `GET /notification_events`

Lists notification events for the authenticated client.

Query parameters:

| Name | Required | Description |
| --- | --- | --- |
| `created_from` | No | Inclusive ISO-8601 timestamp lower bound. |
| `created_to` | No | Inclusive ISO-8601 timestamp upper bound. |
| `delivery_status` | No | One of `pending`, `in_progress`, `completed`, `retrying`, `failed`. |
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
      "content": "Bank transfer received from Account #4567 for $1,500.00",
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
  }
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
  "content": "Bank transfer received from Account #4567 for $1,500.00",
  "created_at": "2024-03-15T11:20:18Z",
  "delivery_status": "failed",
  "client_id": "CLIENT002",
  "attempt_count": 5,
  "delivery_attempts": [
    {
      "attempt_number": 5,
      "started_at": "2024-03-15T12:20:18Z",
      "finished_at": "2024-03-15T12:20:21Z",
      "result": "failed",
      "http_status": 500,
      "latency_ms": 3000
    }
  ],
  "final_error_code": "HTTP_500",
  "final_error_message": "Webhook endpoint returned server error"
}
```

Status codes:

| Status | Meaning |
| --- | --- |
| `200` | Event found and belongs to caller. |
| `401` | Missing or invalid authentication. |
| `403` | Authenticated but missing required scope. |
| `404` | Event does not exist or belongs to another client. |

## `POST /notification_events/{notification_event_id}/replay`

Requests a replay for a terminal failed notification event.

Example:

```http
POST /notification_events/EVT003/replay
Authorization: Bearer <token>
Idempotency-Key: 3ee2e36e-84ab-4a76-86fb-c6d31115ef12
```

Response:

```json
{
  "notification_event_id": "EVT003",
  "replay_id": "RPL-20240315-0001",
  "delivery_status": "pending",
  "message": "Replay accepted"
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
| `409` | Replay already in progress for this notification. |
| `429` | Rate limit exceeded. |

Replay rules:

- Only `failed` events are replayable.
- Completed events such as `EVT001`, `EVT002`, and `EVT010` are rejected with `400`.
- Failed events such as `EVT003`, `EVT005`, and `EVT009` are accepted only for the owning client.
- The replay request is idempotent when the same `Idempotency-Key` is repeated by the same client.

## Validation Rules

- `created_from` and `created_to` must be valid ISO-8601 timestamps.
- `created_from` must be less than or equal to `created_to`.
- `delivery_status` must be from the allowed enum.
- `size` must be between `1` and `100`.
- `notification_event_id` must match the supported identifier format and be looked up with tenant scope.

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

Error responses should be stable and avoid exposing internal stack traces, webhook secrets, private endpoint details, or another client's identifiers.
