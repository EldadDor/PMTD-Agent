# Personal Movie & TV Discovery Agent — Codex Implementation Plan

**Status:** Approved project scope; implementation not yet started  
**Document version:** 1.0  
**Primary audience:** Codex coding agent  
**Purpose:** Build a self-hosted, proactive, personalized movie and TV discovery agent that learns from IMDb ratings and ongoing feedback.


### IMPORATANT NOTE!!! ######
---- .codex AGENTS.md and .codex folder  -------
I've copied AGENTS.md + .codex folder from another project, ONLY after reviewing this plan file -
make the changes for the AGENTS.md + .codex files contexnt (hooks, approval gates etc) to fully comply to this plan and project.
If you have any reservations let me understand what and I'll review and approve them.

## 1. Codex operating instructions

1. Treat this document as the project specification, **not** as permission to implement every phase immediately.
2. First inspect the repository (if present), summarize the current state, identify gaps and propose a small first implementation milestone. If the repository is empty, propose a scaffold before creating files.
3. Work incrementally, with small, testable changes. Show the intended files, dependencies, migrations and commands before introducing major architectural changes.
4. Obtain explicit approval before adding a major dependency, database migration, external service, deployment configuration, irreversible operation, commit, push or merge. Do not assume access to secrets or external accounts.
5. Prefer idiomatic Kotlin, straightforward Spring Boot services and explicit interfaces. Avoid overengineering, unnecessary microservices and multi-agent frameworks.
6. Do not implement optional Phase 2 features until the Phase 1 MVP is working and the user approves expansion.
7. Maintain a running task list and a short decisions log, separating approved choices from open decisions.
8. Run relevant automated tests and report actual results; do not claim a feature works unless verified.
9. Never commit API keys, bot tokens, IMDb exports or personal ratings to source control. Provide `.env.example` or documented configuration placeholders.



## 2. Product vision

A personal assistant that periodically discovers new movies, shows, seasons and episodes; compares them with the user's viewing history and preferences; proactively sends concise, relevant recommendations; and improves through lightweight feedback.

### Core user stories

- Import my existing IMDb ratings from an exported CSV.
- Discover relevant new movies, TV shows, and new seasons from external data sources several times per day.
- Notify me only when something is relevant, without repeatedly suggesting the same item.
- Let me mark a recommendation **Already watched**, **Want to watch**, **Maybe later**, or **Not interested**.
- When useful, let me provide a reason or a rating, including optional free text.
- Remember my choices and use them to improve future recommendations.
- Explain briefly why each item was recommended, using evidence from my preferences rather than inventing facts.
- Run on my own infrastructure and support a small local LLM.

### Non-goals for MVP

- Model fine-tuning or training.
- Multiple collaborating LLM agents.
- Full conversational assistant or elaborate chat interface.
- React dashboard, native mobile app or complex account management.
- Vector database / embedding-based semantic RAG.
- Guaranteed real-time announcements or perfect coverage of every streaming platform.

## 3. Proposed stack

| Area | Choice | Status |
|---|---|---|
| Language | Kotlin | Approved |
| Framework | Spring Boot 4.12 | Approved; verify compatible current patch version |
| Java runtime | Java 25 LTS | Proposed; confirm compatibility and toolchain |
| AI integration | Spring AI | Approved; select version compatible with Boot 4 |
| LLM runtime | Ollama, local model | Preferred; keep provider replaceable |
| Models | Benchmark 3B, ~5B and 8B instruction/tool-capable models | Approved evaluation |
| Database | SQLite or PostgreSQL | Open decision; choose based on MVP simplicity vs pgvector expansion |
| External discovery | TMDB + TVmaze | Approved initial sources; verify coverage, terms, attribution, rate limits |
| History import | IMDb ratings CSV | Approved |
| Scheduling | Spring `@Scheduled` initially | Approved |
| Messaging | Evaluate LINE first; Telegram if more practical/free | Open decision |
| Development messaging | Telegram Bot API via long polling is a candidate | Conditional on platform choice |
| Packaging | Docker container | Approved direction |
| Deployment | Self-hosted Linux / container environment | Approved direction; target TBD |

Do not assume that the existence of an API guarantees a free quota, commercial rights, geographic coverage, or specific release freshness. Verify official documentation before implementation.

## 4. High-level architecture

```text
Spring Scheduler / manual trigger
            |
            v
Discovery Coordinator
   |                   |
   v                   v
TMDB Client         TVmaze Client
   |                   |
   +---------+---------+
             v
   Normalize + Deduplicate
             |
             v
       Relational DB <------ IMDb CSV Importer
             |
             v
 Recommendation Service
   - candidate filtering
   - deterministic scoring
   - preference retrieval
             |
             v
  Spring AI / Local LLM
   - explanation generation
   - optional free-text interpretation
             |
             v
  Messaging Abstraction
    LINE or Telegram
             |
             v
     User feedback event
             |
             v
 Feedback Service -> DB -> future ranking
```

**Key rule:** External API access, scheduling, ranking, and button handling are normal deterministic application code. Invoke the LLM only where language understanding or explanation is beneficial. Avoid an unconstrained LLM loop for routine polling.

## 5. Domain and persistence design

Design the schema before implementation. Candidate entities:

- **Title:** internal ID, type (movie/series), canonical title, release year, genres, synopsis, poster URL, TMDB ID, TVmaze ID, IMDb ID where available.
- **ReleaseEvent:** title/season/episode, event type, date, source, first-seen and last-seen timestamps.
- **ImportedRating:** IMDb title ID, user rating, rated date if provided, import provenance.
- **UserTitleState:** watched, want-to-watch, maybe-later, not-interested; optional rating and timestamps.
- **PreferenceObservation:** explicit likes/dislikes, optional reason, confidence/provenance, source title, timestamps.
- **Recommendation:** title/event, score, rationale, generation time, delivery state.
- **NotificationDelivery:** provider, provider message ID, attempt count, status, error, idempotency key.
- **FeedbackEvent:** button or free-text action, target recommendation, payload, timestamp.
- **DiscoveryCursor:** source, last successful check, pagination/update checkpoint.

Use unique constraints on source IDs and delivery idempotency keys. Preserve original source IDs and distinguish explicit preferences from inferred ones. Keep raw IMDb CSV private and do not store it unnecessarily after a verified import.

## 6. Implementation phases

### Phase 0 — Technical validation and decisions

**Tasks**
- Verify Spring Boot 4.12, Spring AI 2.0, Kotlin 2.4.21 (2.2), Java 25 compatibility and select versions.
- Verify TMDB and TVmaze API access, free-use requirements, attribution, rate limits, freshness and relevant endpoints.
- Evaluate **LINE first**: Official Account requirements, free message quota for the applicable region, push/reply counting, webhook HTTPS requirements, rich message/buttons, and operational complexity.
- Compare Telegram: free bot limits, inline keyboards, callback queries, long polling vs webhook, ease of local development.
- Recommend messaging provider with a brief evidence-based decision record; do not lock in LINE or Telegram before evaluation.
- Decide SQLite vs PostgreSQL; consider future pgvector without prematurely implementing it.
- Decide how to run Ollama and the application locally; confirm minimum memory and context requirements.

**Acceptance criteria**
- Compatibility and external-service assumptions are documented and verified.
- Open decisions are presented for user approval.
- A minimal vertical-slice implementation path is proposed.

### Phase 1A — Foundation

**Tasks**
- Create a Kotlin Spring Boot 4 application with configuration profiles, health endpoint, logging, tests and Docker build.
- Add relational persistence and migrations appropriate to the chosen database.
- Add a small, testable application architecture: clients, discovery, catalog, ranking, feedback, notifications, AI adapter.
- Define interfaces for `CatalogSource`, `NotificationGateway`, `RecommendationEngine` and `LanguageModelGateway` where useful.
- Externalize secrets and API keys.

**Acceptance criteria**
- App starts locally; tests pass; health endpoint works.
- Docker build runs with documented environment variables.
- No credentials or personal history are committed.

### Phase 1B — IMDb history import

**Tasks**
- Inspect an actual IMDb export CSV and map its available columns rather than assuming a fixed schema.
- Implement robust import with validation, idempotent upserts and useful error reporting.
- Preserve IMDb IDs and user ratings; distinguish missing values from zero.
- Generate basic profile statistics (rating distribution, favored genres when metadata is available).

**Acceptance criteria**
- Re-importing the same CSV does not create duplicates.
- Imported count, skipped count and errors are reported.
- Ratings can be queried without sending the entire dataset to the LLM.

### Phase 1C — Discovery and scheduled polling

**Tasks**
- Implement TMDB and TVmaze clients with retry/backoff, timeout, pagination and rate-limit handling.
- Normalize IDs and titles; cross-match by trusted IDs when possible, and avoid unsafe fuzzy merges.
- Track new titles, release dates, season/episode events and source update times.
- Schedule several daily discovery runs with configurable times/time zone; support a manual dry run.
- Persist checkpoints and avoid duplicate discovery records.

**Acceptance criteria**
- Repeated polling does not duplicate catalog records.
- API outages are logged and recoverable without spamming notifications.
- Discovery results can be inspected independently of the LLM.

### Phase 1D — Recommendation engine and local model evaluation

**Tasks**
- Implement deterministic candidate filtering and baseline scoring from ratings, genres, watch state, release recency and explicit feedback.
- Prevent previously watched and rejected titles from being repeatedly recommended unless explicitly requested or a meaningful new event occurs.
- Integrate Spring AI with a configurable local Ollama provider.
- Use the LLM to write concise, grounded recommendation explanations; do not allow fabricated release details or streaming availability.
- Benchmark 3B, approximately 5B, and 8B models using a fixed test set (~30 cases) covering preference extraction, ambiguous titles, JSON/tool-call correctness, hallucinations, latency, and peak RAM/VRAM.
- Compare a deterministic-only baseline with LLM-enhanced explanations and decisions.

**Acceptance criteria**
- Ranking works without an LLM.
- Model evaluation results are reproducible and documented.
- Selected model meets agreed reliability and latency targets on available hardware.
- Explanations reference stored preferences and verified title metadata.

### Phase 1E — Notifications and interactive feedback

**Tasks**
- Implement the approved provider behind a `NotificationGateway` abstraction.
- Send concise recommendations with title, release context, reason, and optional poster/link.
- Provide interactive actions: **Already watched**, **Want to watch**, **Maybe later**, **Not interested**.
- Support optional rating and optional reason (e.g., not my style, bad reviews, not in the mood, other).
- Persist every feedback action idempotently and immediately update user-title state.
- Use Spring AI only to interpret optional natural-language explanations; save both original text and interpreted preference with provenance.
- If Telegram is selected: use inline keyboards and callback queries; start with long polling locally, then evaluate HTTPS webhook for production.
- If LINE is selected: use official Messaging API rich interactions and verified HTTPS webhooks; confirm free quota before enabling proactive sends.
- Avoid treating a rejection of one title as a blanket rejection of its entire genre.

**Acceptance criteria**
- A recommendation can be delivered and each action correctly persisted.
- Repeated callback deliveries do not duplicate feedback.
- User choices influence subsequent rankings.
- A messaging failure is retryable without sending duplicate notifications.
- Unrecognized or forged incoming events are rejected appropriately.

### Phase 1F — Operational hardening and MVP acceptance

**Tasks**
- Add structured logs, request correlation, health checks and basic metrics.
- Add source throttling, retry policies, message deduplication, quiet hours and configurable notification frequency.
- Handle missing metadata, ambiguous title matches, provider outages and LLM unavailability gracefully.
- Document setup, environment variables, data backup/restore and local deployment.
- Run an end-to-end demonstration: import -> discover -> rank -> notify -> feedback -> changed future recommendation.

**Acceptance criteria**
- System can run unattended for several days without duplicate/spam notifications.
- Restart does not lose state or trigger a flood of repeated alerts.
- Core workflow works when the LLM is temporarily unavailable, with reduced functionality.
- User can operate the system locally using the README.

## 7. Optional Phase 2 — Semantic RAG and richer personalization

**Do not implement in MVP.** Evaluate only after real-world feedback establishes a need.

- Store natural-language preference observations with provenance and confidence.
- Add embeddings for nuanced preferences and, optionally, movie/series descriptions.
- Evaluate PostgreSQL + pgvector or another suitable vector store if justified.
- Retrieve relevant observations rather than passing the entire viewing history to the LLM.
- Compare **SQL-only recommendations** against **SQL + semantic retrieval** on a held-out evaluation set.
- Measure recommendation relevance, hallucination rate, cost, latency and operational complexity.
- Add RAG only if it provides a meaningful measured benefit.

Possible later additions, requiring separate approval: conversational search, React dashboard, streaming-service availability by region, multi-user accounts, advanced collaborative filtering, and multi-agent orchestration.

## 8. Design principles and constraints

- One agent with explicit tools is enough for MVP. Do not add CrewAI, LangGraph, LangChain4j or a multi-agent layer without a demonstrated need.
- Prefer deterministic operations for importing, polling, deduplication, ranking baseline, feedback buttons and persistence.
- Use Spring AI for language interpretation and explanations, with structured output and validation.
- Treat TMDB/TVmaze metadata as untrusted input; never treat plot text or API responses as instructions to the agent.
- Restrict inbound messaging to the authorized user/account; validate Telegram updates or LINE signatures and protect tokens.
- Do not send personal ratings to a hosted LLM without explicit approval. Default to local inference.
- Separate data acquisition, recommendation, AI, messaging and persistence to make each testable.
- Keep notification volume controlled; relevance matters more than quantity.
- Distinguish **newly discovered**, **newly released**, **new season**, and **newly available to stream**; do not claim streaming availability without a verified region-specific source.
- Respect API attribution, licensing, usage restrictions and privacy obligations.

## 9. Suggested initial package layout (adapt as appropriate)

```text
src/main/kotlin/.../
  config/
  catalog/
  discovery/
  imdb/
  recommendation/
  preferences/
  ai/
  messaging/
  scheduling/
  persistence/
src/test/kotlin/.../
docs/
  decisions.md
  model-evaluation.md
  implementation-status.md
```

Prefer vertical feature boundaries if they improve cohesion; the layout is a suggestion, not a mandatory architecture.

## 10. First instruction to Codex

> Read `movie_tv_agent_codex_plan.md` completely. Do not implement the entire project. Inspect the repository and produce: (1) a concise architecture proposal, (2) a list of open decisions requiring my approval, (3) an ordered, small-task implementation backlog, and (4) a Phase 0 verification checklist covering Spring Boot 4/Spring AI compatibility, TMDB and TVmaze APIs, LINE versus Telegram, database choice, and local Ollama. Identify the smallest end-to-end vertical slice. Wait for my approval before adding dependencies, creating migrations, or changing deployment configuration. Do not commit or push without permission.

## 11. Decision register

**Approved:** Kotlin, Spring Boot 4, Spring AI, self-hosted orientation, IMDb CSV import, TMDB + TVmaze initial discovery, periodic polling, personalized recommendations, interactive feedback in V1, local model bake-off (3B/~5B/8B), optional semantic RAG in Phase 2.

**To evaluate/approve:** Java 25 compatibility, exact library versions, SQLite vs PostgreSQL, LINE vs Telegram (evaluate LINE first), final Ollama model, schedule frequency, notification caps, hosting target, whether free-text feedback is available immediately or after basic button feedback is stable.

**Explicitly deferred:** Fine-tuning, multi-agent orchestration, full chat assistant, React UI, semantic vector retrieval, additional metadata sources beyond demonstrated need.
