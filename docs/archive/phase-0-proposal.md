# Phase 0 proposal

Last reviewed: 2026-11-19 (Asia/Jerusalem)
Status: P1A-01 approved on 2026-11-16, including the exact toolchain in
`foundation-manifest.md`. On 2026-11-18 the user approved Spring AI 2.0.2,
PostgreSQL on their laptop and desktop Windows/IntelliJ operation; intends LINE
with a reported account. Models/downloads, scheduling, feedback and deployment
work are deferred. `decisions.md`, `ai-foundation-manifest.md`,
`persistence-manifest.md` and `line-api-setup.md` contain the current direction.
Original proposals below are retained as context; superseded recommendations do
not override these choices. The user subsequently approved the JDBC/Flyway
dependencies and V1 migration creation in `persistence-manifest.md`. On
2026-11-19, V1 was applied to the PMTD laptop database and all eight integration
tests passed on the separate test database.

## Architecture

One Kotlin/Spring Boot application, one approved relational database, separately
managed local Ollama. Use cohesive feature packages; avoid empty module scaffolds.

`manual trigger / scheduler -> source adapters -> normalized catalog + release
events -> deterministic ranking -> optional grounded explanation -> delivery
outbox -> messenger -> feedback transaction -> future ranking`

Introduce CatalogSource, NotificationGateway, RecommendationEngine and
LanguageModelGateway when first needed. Prefer Spring HTTP client facilities over
additional bot frameworks. No hosted AI fallback, vectors, microservices or
multi-agent framework in MVP.

Review schema before migrations: Title, ReleaseEvent, ImportedRating,
UserTitleState, PreferenceObservation, Recommendation, NotificationDelivery,
FeedbackEvent, DiscoveryCursor. Preserve trusted source IDs, provenance and unique
constraints; separate explicit/inferred preferences. Avoid fuzzy title merges.
Commit feedback/state and checkpoints transactionally. Advance polling offsets
only after persistence. Distinguish failed and uncertain sends; local uniqueness
does not guarantee exactly-once delivery across a remote crash window.

Authorize incoming user/chat IDs, bind callback actions to persisted recommendations,
and validate provider authenticity. Metadata/feedback are untrusted data. Ranking
works without AI; explanations use verified metadata and stored preferences.

## Small first milestone: P1A-01

Approve a minimal health/config/logging scaffold with a startup/health test,
without database, messaging or AI wiring. This is a prerequisite, not the product's
end-to-end slice. Docker is deferred until deployment configuration approval, so
this milestone alone does not complete Phase 1A.

Intended files after approval:

- `settings.gradle.kts`, `build.gradle.kts`, optional `gradle.properties`;
  `gradlew`, `gradlew.bat`, `gradle/wrapper/*` with distribution checksum.
- `src/main/kotlin/<approved-package>/MovieTvAgentApplication.kt`.
- `src/main/resources/application.yml`, local/test profiles only if needed.
- `src/test/kotlin/<approved-package>/ApplicationHealthTest.kt`.
- `.gitignore`, `.env.example` placeholders, `README.md`, status/decision updates.

Proposed dependencies: Kotlin support, Boot web MVC, Actuator, Jackson Kotlin
support and Boot test support. Present exact plugin/BOM versions for approval
before creation. AI, persistence and messaging dependencies are later additions.

Intended commands: `.\gradlew.bat test`, `.\gradlew.bat bootJar`; with permission
to start the local app, `.\gradlew.bat bootRun` and a loopback health request.
Wrapper/dependency downloads need approved versions and available access. Diagnose
the existing Gradle native-library failure first; a wrapper is not a proven fix.
No migrations or deployment files in this milestone. Alternative: non-web Boot,
but it omits the requested HTTP health check. Before real data, rollback removes
only newly generated scaffold files, preserving these planning records.

## Smallest end-to-end vertical slice

With persistence validated on PostgreSQL, the POC sequence is to import a small
private IMDb sample, manually
discover a bounded set from one approved source, persist trusted IDs, rank
deterministically and inspect the result locally using fixtures first. Models,
scheduling and feedback wait for the user's later decisions. The eventual MVP
slice adds a recommendation to the authorized LINE user, persists one
of the four button actions, then rerank and show its effect. Verify repeated
import/discovery/feedback and restart persistence using fixtures/fake gateways
first. Obtain explicit authorization before a real send.

LLM explanations, the second discovery source, scheduling and free text follow;
their full MVP obligations remain. This proposed narrow sequence across phases
1A–1E requires approval and does not close or remove any phase.

## Ordered small-task backlog

| ID | Task | Acceptance evidence |
| --- | --- | --- |
| P0-04 | Resolve decisions and scaffold manifest | Explicit choices and authorization |
| P0-05 | Diagnose toolchain; resolve approved versions | Dependency graph and smoke build |
| P1A-01 | Minimal health/config/logging scaffold | Health test and packaged jar |
| P1A-02 | Review schema, keys and transaction/resend semantics | Approved migration/rollback plan |
| P1A-03 | Add approved database + migration | Constraint and restart tests |
| P1B-01 | Inspect private CSV headers; implement import | Counts/errors, missing vs zero, idempotent reimport |
| P1C-01 | One source adapter + manual dry run | Pagination/429/outage fixtures, trusted-ID matching |
| P1D-01 | Deterministic ranking | Watched/rejected suppression and evidence tests |
| P1E-01 | Approved gateway, one send, four buttons | Authorization/replay tests and changed reranking |
| P1C-02 | Second source, release events, scheduling | Event distinctions, checkpoints, no restart flood |
| P1D-02 | AI adapter + fixed ~30-case bake-off | Correctness, hallucinations, latency and peak RAM/VRAM |
| P1E-02 | Optional rating/reasons and approved free text | Provenance, validated interpretation |
| P1F-01 | Caps, quiet hours, recovery, logs and metrics | Failure/restart tests and several-day observation |
| P1A-04 / P1F-02 | Approved Docker packaging + operator docs | Build, backup/restore and full workflow demonstration |

Rows are proposed bounded tasks, not execution permission. Record actual files,
checks, limitations and commit IDs only when applicable. Phase 2 remains deferred.

## Phase 0 verification checklist

Checked boxes mean documentation evidence, not successful integration.

### Versions

- [x] [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
  currently show 4.1.2 and support Java 17–27, including Java 25.
- [x] [Spring AI](https://docs.spring.io/spring-ai/reference/getting-started.html)
  currently shows 2.0.2; 2.0.x supports Boot 4.0/4.1.
- [x] [Kotlin support](https://docs.spring.io/spring-boot/reference/features/kotlin.html)
  requires Kotlin 2.2.x or newer and manages a suitable version.
- [x] Foundation only: exact Kotlin, plugin, BOM and Gradle patches approved in
  `foundation-manifest.md` and committed in `1818702`; recorded compilation,
  2 passing HTTP tests and bootJar build in `implementation-status.md`.
  Spring AI 2.0.2 was subsequently resolved/compiled and verified with models
  disabled on 2026-11-18 (3 passing tests and bootJar); inference is deferred.
  Original requirement: consult the
  [Gradle Java matrix](https://docs.gradle.org/current/userguide/compatibility.html)
  and Kotlin target support. Boot's accepted Gradle versions do not all necessarily
  run on Java 25. Approve the manifest, resolve artifacts and compile/test it.

### TMDB / TVmaze

- [x] [TMDB FAQ](https://developer.themoviedb.org/docs/faq): noncommercial use
  with attribution, account/key setup, logo/notice requirements; commercial use
  requires separate licensing. Plan credits presentation before delivery.
- [x] [Rate guidance](https://developer.themoviedb.org/docs/rate-limiting): changing
  approximate upper limit, not guaranteed quota; respect 429 and backoff.
- [x] [Discover movie](https://developer.themoviedb.org/reference/discover-movie)
  supports release/region filters; [movie changes](https://developer.themoviedb.org/reference/changes-movie-list)
  reports edited IDs, not new-release announcements. Its bounded windows need checkpoints.
- [x] [TVmaze API](https://www.tvmaze.com/api): free public endpoints include
  trusted-ID lookup, schedules, seasons/episodes and show updates. General caching
  is up to one hour (endpoint exceptions exist); guidance is at least 20 calls per
  10 seconds/IP with graceful 429 backoff. CC BY-SA requires credit and compliance
  with ShareAlike; link back to source records.
- [ ] Map exact TV details/external-ID/season/episode endpoints, pagination,
  endpoint caches and representative payloads before coding adapters.
- [ ] Confirm release region/timezone, attribution/image presentation and actual
  license obligations. Do not assume complete coverage or streaming availability.
- [ ] With approved access, smoke-check TMDB and TVmaze sample IDs; never use or
  expose personal history as public test data. No credentialed call has run yet.

### Messaging — LINE first

| Consideration | LINE | Telegram |
| --- | --- | --- |
| Setup | Official Account + Messaging API channel | Bot token + authorized chat initiation |
| Proactive sending | Region/plan-specific allowance; push counts, reply does not | Ordinary bot messages free within rate limits; no paid broadcasts needed |
| Local inbound | Reachable HTTPS webhook with signature validation | getUpdates long polling, no public inbound endpoint |
| Actions | Rich interactions/postback | Inline keyboard/callback queries |
| Proposed choice | Viable if preferred and regional quota is sufficient | Simpler local MVP; awaiting approval |

Sources: [LINE pricing](https://developers.line.biz/en/docs/messaging-api/pricing/),
[LINE inbound](https://developers.line.biz/en/docs/messaging-api/receiving-messages/),
[LINE actions](https://developers.line.biz/en/docs/messaging-api/actions/),
[Telegram FAQ](https://core.telegram.org/bots/faq),
[Telegram API](https://core.telegram.org/bots/api).

- [ ] Confirm LINE account billing region/plan and actual free monthly allowance;
  compare expected digest counts. Timezone is insufficient evidence of region.
- [ ] Verify rich action payload limits and HTTPS/signatures for LINE, or callback
  limits, authorized IDs and persisted offsets for Telegram.
- [ ] Account for Telegram's 24-hour incoming-update retention and mutually
  exclusive long polling/webhook modes. Persist events before offset advancement.
- [x] User intends LINE and reports PMTD-Agent account creation on 2026-11-18;
  see `line-api-setup.md` for the remaining activation/quota/ingress work.
- [ ] Authorize account access and controlled real-send validation when ready.

### Database

- [x] User chose existing laptop PostgreSQL at `10.100.102.12` on 2026-11-18;
  version/port/database/schema/TLS and credentials later. No vectors.
- [ ] Validate driver/migration support with the approved stack and design keys,
  locks, transactional feedback, delivery uncertainty and backup/restore.
- [x] JDBC/Flyway dependencies and V1 migration creation approved on 2026-11-18.
  The local build packages V1 and passed five offline tests.
- [x] Applied V1 to the dedicated laptop database and passed eight integration
  tests on the authorized disposable database on 2026-11-19.

### Local Ollama

- [x] [Context guidance](https://docs.ollama.com/context-length) confirms larger
  context increases memory; parameter count alone cannot establish minimum RAM.
- [ ] Confirm RAM/VRAM/CPU/disk and host placement. Keep the runtime private;
  container networking requires a separately approved explicit URL/configuration.
- [ ] Deferred by user: model tags/licenses, quantization and benchmark resources.
  **Do not download models.** Chat/embedding disabled; pull strategy `never`.
- [ ] Fix context and concurrency for fair comparison; measure peak RAM/VRAM,
  cold/warm latency and offload. No minimum hardware claim without measurements.
- [ ] Agree reliability/latency targets before the reproducible ~30-case
  3B/~5B/8B comparison: extraction, ambiguity, JSON/tool correctness, hallucinations,
  languages and deterministic baseline/fallback. Record exact tags/digests.

Phase 0 remains incomplete. Foundation dependency resolution, compilation,
HTTP tests and packaging are recorded as verified in `1818702`. On 2026-11-18,
Spring AI 2.0.2 dependency resolution, compilation, disabled-model startup tests
and packaging also passed (uncommitted). Live integrations, actual inference and
model/hardware evaluation remain unverified/deferred. No phase closed.
