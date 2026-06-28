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

## 2026-06-28 - Architecture Critic Review

Purpose: Critically review the documentation-only architecture as a senior software architecture critic and principal engineer.

Prompt:

```text
Act as a Senior Software Architecture Critic and Principal Engineer. I will provide you with the architecture documentation and/or code from a software repository. Your primary goal is to critically analyze the design, identify potential bugs or flaws, propose robust improvements, and provide well-reasoned arguments for technical trade-offs.
...
Please format your response using the following structure:
- Executive Summary
- Critical Flaws & Risks
- Deep-Dive Trade-off Analysis
- Actionable Recommendations
- Missing Context
```

Follow-up prompt:

```text
the arch doc are all the files present in the current directory and inside /docs folder too
```

Output used:

- Reviewed the root documentation and all files under `docs/`.
- Identified architecture risks around persist-then-enqueue delivery loss, state transition races, replay semantics, endpoint snapshot policy, deduplication, pagination, and sensitive payload exposure.
- Proposed improvements including transactional outbox, explicit delivery state machine, separate replay jobs, unique constraints, cursor pagination, and stronger data protection rules.

Human validation:

- Asked for missing-context assumptions to be incorporated into a reusable design feedback document for another agent.

Related files:

- `README.md`
- `AI_USAGE.md`
- `docs/system-design.md`
- `docs/api-design.md`
- `docs/security.md`

## 2026-06-28 - Design Feedback Refinement

Purpose: Reevaluate the architecture critique with explicit operating assumptions and produce a design feedback artifact for another agent.

Prompt:

```text
From the missing context, assume: event volume to be average ~ 100k events daily - to avoid thinking about db scaling either by partitioning or sharding - but keep it documented the scaling alternative when user growth can not longer be handle by our initial infrastructure; as for SLO use default one from Stripe as a good standard; let's keep event ordering not mandatory, but a nice feature to have in the future; as for ci/cd topology lets have everything dockerize and run with docker compose locally, this is a home assetment, so no need to deploy it in tst/prod envs; follow owasp and data regulations for sensitive data; assume the simplest suscription model; multi region, disaster recovery and RPO.RTO can be achieved by cloud vendor capabilities in the deployment set up - leave the thoughts as a arch doc

With that added context; reevaluate and improve your analysis and output it in a design-feedback.md file inside /doc for another agent consumption
```

Output used:

- Created `docs/design-feedback.md` because the repository uses `docs/`, not `doc/`.
- Reframed the critique for about 100k events per day and an initially unpartitioned, unsharded relational database.
- Documented the future growth path through indexes, read replicas, cold storage, table partitioning, queue isolation, and eventual sharding only after measured saturation.
- Added a Stripe-like webhook retry baseline: prompt first attempt, exponential backoff, automatic retries for up to three days, manual replay, and no hard ordering guarantee.
- Added Docker Compose local topology guidance.
- Preserved OWASP, sensitive data, simple subscription, and future cloud DR/RPO/RTO considerations.

Human validation:

- Requested staging and committing all documentation changes after this prompt log update.

Related files:

- `docs/design-feedback.md`
- `docs/prompt-log.md`
