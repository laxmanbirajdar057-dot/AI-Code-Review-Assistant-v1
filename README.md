# AI Code Review Assistant

An AI-powered, automated code review platform for GitHub pull requests. When a
PR is opened or updated, the app fetches the diff, runs it through a
deterministic rule engine *and* an LLM (Gemini), combines both sets of
findings, scores the change, and stores the result for later history,
statistics, and comparison.

## Architecture

```text
Developer opens/updates a PR
        |
        v
GitHub webhook -> Spring Boot (/webhooks/github)
        |  signature validated, event stored, idempotency checked
        v
Async review job (ReviewWorkerService)
        |
        +--> fetch PR diff (public, or with a stored GitHub token for private repos)
        |
        +--> split into per-file chunks
        |
        |        +---------------------+       +------------------+
        +------->| StaticAnalysisEngine|       |  Gemini (LLM)    |
        |        | (deterministic)     |       |  (LLM findings)  |
        |        +---------------------+       +------------------+
        |                    \                     /
        |                     v                   v
        |               ReviewAggregatorService (combine + dedupe)
        |                            |
        |                            v
        |                     ScoringService (weighted score + risk level)
        v
     MySQL (User, Repository, PullRequest, Review, ReviewComment, WebhookEvent)
        |
        v
REST API -> Thymeleaf/JS frontend (repos, review detail, snippet playground)
```

## Tech stack

- Java 17, Spring Boot 4.1
- Spring Security (JWT, role-based access: ADMIN / DEVELOPER / VIEWER)
- Spring Data JPA + MySQL 8
- Spring WebFlux's `WebClient` for outbound calls (GitHub diff fetch, Gemini)
- Gemini API (`gemini-flash-latest`) for the AI review layer
- Thymeleaf + vanilla JS frontend
- JUnit 5 + Mockito

## Features

- **GitHub webhook integration** — HMAC signature validation, idempotent event
  handling (no duplicate reviews on a redelivered webhook), async processing
  so the webhook response returns immediately.
- **Hybrid analysis** — a deterministic `StaticAnalysisEngine` (hardcoded
  secrets, SQL-injection-shaped string concatenation, debug print statements,
  TODO/FIXME markers, oversized diffs) runs alongside the LLM, so findings
  don't depend entirely on an external AI service.
- **Weighted scoring** — Security 30% / Code Quality 20% / Maintainability
  20% / Performance 15% / Reliability 15%, with a `CRITICAL` finding forcing
  `HIGH` risk regardless of the weighted average.
- **Review history & analytics** — `GET /repos/{id}/reviews`,
  `GET /repos/{id}/statistics` (average score, critical issue count, most
  common issue category, score trend).
- **Review comparison** — `GET /reviews/compare?repoId=&from=&to=` diffs two
  PRs' latest reviews (score delta, per-category breakdown).
- **Role-based access** — ADMIN (full access), DEVELOPER (manage own repos),
  VIEWER (read-only).
- **Private repo support** — an optional encrypted GitHub token per repo.
- **Snippet playground** — paste a snippet and get REVIEW / EXPLAIN / FORMAT
  from the LLM without connecting a repo.

## Database schema

```text
User (1) --- (N) Repository (1) --- (N) PullRequest (1) --- (N) Review (1) --- (N) ReviewComment
                     |
                     +--- (N) WebhookEvent
```

- `PullRequest` exists as its own entity (rather than a raw PR number on
  `Review`) so a PR reviewed multiple times — e.g. once per `synchronize`
  webhook event as new commits land — groups those reviews together for
  history/comparison.
- `Repository.webhookSecret` and `Repository.githubToken` are both encrypted
  at rest (AES-256-GCM) — see `EncryptionService`.

## Setup

### 1. Environment variables

Copy `.env.example` to `.env` and fill in real values:

| Variable | Purpose |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | MySQL credentials |
| `JWT_SECRET` | Signs auth tokens |
| `GEMINI_API_KEY` | From https://aistudio.google.com/apikey |
| `WEBHOOK_SECRET_ENCRYPTION_KEY` | `openssl rand -base64 32` |
| `APP_BASE_URL` | Public URL of this deployment (used to build webhook URLs) |
| `APP_ALLOWED_ORIGINS` | Comma-separated frontend origin(s) allowed for CORS |

### 2. Run locally (without Docker)

Requires a running MySQL instance.

```bash
export $(cat .env | xargs)   # or set the vars another way
./mvnw spring-boot:run
```

### 3. Run with Docker

```bash
docker compose up --build
```

This brings up MySQL and the app together; the app waits for MySQL's
healthcheck before starting. The app is then available at
`http://localhost:8080`.

### 4. Connect a repository

1. Register/log in via the UI (or `POST /auth/register`, `POST /auth/login`).
2. `POST /repos` with `{ "repoUrl": "...", "webhookSecret": "...", "githubToken": "..." }`
   (`githubToken` optional — only needed for private repos).
3. Add the returned `webhookUrl` as a webhook in your GitHub repo settings,
   using the same secret, subscribed to "Pull requests".

## API overview

```text
POST   /auth/register
POST   /auth/login

POST   /repos
GET    /repos
DELETE /repos/{id}
GET    /repos/{id}/reviews
GET    /repos/{id}/statistics

POST   /webhooks/github

GET    /reviews/{repoId}/{prNumber}
GET    /reviews/compare?repoId=&from=&to=
PATCH  /reviews/comments/{id}

POST   /snippets/review

GET    /actuator/health
```

## Testing

```bash
./mvnw test
```

Covers: webhook signature validation and idempotency, diff chunking, AES-GCM
encryption round-trip, JWT role attachment, the static analysis rule set, and
the scoring engine's weighting/clamping/risk-level logic.

## Known limitations / next steps

- Review history/statistics/comparison endpoints exist but aren't yet wired
  into the frontend (API-only for now).
- No rate limiting/backoff on GitHub or Gemini API calls yet.
- `spring.jpa.hibernate.ddl-auto=update` is convenient for a portfolio
  project but isn't how you'd manage schema changes in production (a real
  migration tool like Flyway would replace it).
