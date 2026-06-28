# Prompt Log

Detailed record of AI-assisted work used for the Cobre notification events challenge.

## 2026-06-28 - Challenge Intake And Planning

Purpose: Convert the challenge statement into a delivery plan for a docs-only solution.

Prompt:

```text
Background
In Cobre we have a transactional, cloud-native, event driven and microservices platform...
Candidate tasks
Task 1: System Design
Task 2: API implementation
Task 3: Security
...
/Users/alfredos/Downloads/notification_events.json
```

Output used:

- Inspected the empty workspace and the provided `notification_events.json` sample file.
- Confirmed the dataset contains 10 events across `CLIENT001`, `CLIENT002`, and `CLIENT003`.
- Proposed a docs-only plan covering architecture, API contracts, security, and AI usage documentation.

Human validation:

- Selected a docs-only deliverable.
- Selected a self-contained demo implementation assumption only as future fallback context.

Related files:

- `README.md`
- `docs/system-design.md`
- `docs/api-design.md`
- `docs/security.md`
- `AI_USAGE.md`

## 2026-06-28 - Documentation Implementation

Purpose: Create the documentation package from the accepted plan.

Prompt:

```text
Implement the plan.
```

Output used:

- Created the root README with navigation, executive summary, assumptions, and references.
- Created system design documentation with context, delivery flow, data model, retry, replay, observability, scalability, and resiliency decisions.
- Created API documentation for `GET /notification_events`, `GET /notification_events/{notification_event_id}`, and `POST /notification_events/{notification_event_id}/replay`.
- Created security documentation for OWASP-related risks and mitigation controls.
- Created an AI usage summary.

Human validation:

- Verified file inventory.
- Checked there were no `TODO`, `TBD`, `FIXME`, or placeholder markers.
- Adjusted the first Mermaid diagram to use portable `flowchart` syntax.

Related files:

- `README.md`
- `docs/system-design.md`
- `docs/api-design.md`
- `docs/security.md`
- `AI_USAGE.md`

## 2026-06-28 - First Commit Preparation

Purpose: Initialize Git and commit only the first docs-only package.

Prompt:

```text
use this commit message convention Use the <type>(<optional scope>): <subject> format
initialize git repo and add the first docs only - do not add design-feedback.md in the first commit
since it will go in a second commit
```

Output used:

- Initialized the Git repository.
- Staged only the first documentation package.
- Excluded `docs/design-feedback.md` and `.DS_Store`.
- Created the first commit using the requested format.

Human validation:

- Confirmed the first commit contents with `git show --stat --oneline --name-only HEAD`.
- Confirmed `docs/design-feedback.md` remained untracked for a later commit.

Related commit:

```text
6b9edb1 docs: initialize notification events documentation
```

Related files:

- `README.md`
- `AI_USAGE.md`
- `docs/system-design.md`
- `docs/api-design.md`
- `docs/security.md`

## 2026-06-28 - Prompt Log Addition

Purpose: Add a dedicated file to track prompts used across the implementation.

Prompt:

```text
should we create another md file to track the prompts use across the implementation?
```

Follow-up prompt:

```text
add it
```

Output used:

- Added this `docs/prompt-log.md` file.
- Linked the prompt log from `README.md`.
- Linked the detailed prompt history from `AI_USAGE.md`.

Human validation:

- This file should be reviewed before the second commit to confirm the prompt summaries are detailed enough for the presentation expectations.

Related files:

- `docs/prompt-log.md`
- `README.md`
- `AI_USAGE.md`
