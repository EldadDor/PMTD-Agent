# Project index

Review before each task and update after it. Last reviewed: 2026-11-23 (Asia/Jerusalem).

- **Current:** [Phase 1E — Notifications and feedback](work_current_phase.md). All 27 PostgreSQL tests passed with V1–V5 in the approved disposable schema. V5 was applied and verified on the application database with user approval. [Evidence](line-feedback-proposal.md#verification-status).
- **Next:** [1F Deferred discovery, AI and MVP acceptance](next_phase.md) · [Later phases](proposed_next_phases.md). Revised sequence approved at G0 on 2026-11-21.
- **Work references:** [Product plan](../movie_tv_agent_codex_plan.md) · [Decisions](decisions.md) · [App setup](../README.md) · [Laptop deployment](laptop-deployment-handoff.md) · [PostgreSQL schema](persistence-manifest.md) · [Discovery design](discovery-persistence-proposal.md) · [TMDB preview](tmdb-discovery-proposal.md) · [TVmaze](tvmaze-discovery-proposal.md) · [LINE](line-api-setup.md)

## Completed

- **P1D-02:** Added IMDb audience ratings and manual review. The user approved G3 and a read-only comparison on 2026-11-22. Offline verification and the comparison passed. Pushed `62b5102` and verified it as the `origin/master` branch tip. [Evidence](deterministic-ranking-proposal.md#p1d-02-implementation).

- **P1D-01 delivery:** Delivered `f76ae3c` and `bb93b80` to `origin/master` with G3 approval. The user authorized a one-time read-only preview on 2026-11-22. It returned five recommendations for November 1–21, which did not meet the user's expectations. [Verification](deterministic-ranking-proposal.md#verification).

- **SHR:** Installed the global session-handoff-readiness skill and AGENTS.md reference on 2026-11-21; synthetic handoff and stale-resume checks passed.

- **Phase 1C:** User approved G4 on 2026-11-21 with discovery and scheduling carry-over to 1F. [Closure evidence](archive/complete_phases.md#phase-1c--discovery-and-polling).

- **P1C-05:** The user approved V4 and real-history enrichment. Of 885 eligible titles, 882 matched, one was unmatched, and two had identity conflicts; none remain unattempted. [Verification](rated-title-enrichment-proposal.md#verification)

- **P1C-03:** The bounded TMDB import passed 40 offline and 20 disposable PostgreSQL tests. Two live sample runs against the disposable catalog stored the same 77 candidates without duplicates. The user approved V3 on `pmtd_agent.pmtd`, and Flyway history and schema checks passed on 2026-11-21. User authorized commit and push (G3) and reported completing the laptop rollout. [Verification](tmdb-persistence-proposal.md#verification)

- **P1C-04:** Created the ignored root `.env` with PostgreSQL settings from local configuration. Verified the seven entries match and Git ignores the file.

- **P1C-02:** Read-only TMDB preview verified against the live API and disposable catalog. 39 offline tests passed. Committed in `a45f843`. [Validation evidence](tmdb-discovery-proposal.md#verification)

- **P1C-01:** G2 approved. 33 offline and 17 disposable PostgreSQL tests passed. Packaged public sample import was repeatable. User approved V2 on `pmtd_agent.pmtd`; Flyway history and both tables verified on 2026-11-21. No production discovery import performed. Implementation `29367dc`.

- **Phases 0, 1A and 1B:** G4 approved on 2026-11-20. The archived ledger records outcomes and carry-over.
- **P1B-01:** Reviewed import acceptance using P0-09/P0-10 evidence. Distribution went to P1B-02. Genre statistics await stored metadata.
- **P1B-02:** User approved disposable PostgreSQL verification. The packaged CLI showed ten zero buckets on an empty schema, then correct counts after importing three synthetic ratings. The disposable `pmtd_agent_test.pmtd_test_stats_c63d4c450cd2` schema remains for inspection. Earlier offline `test bootJar` passed 30 tests. Implementation in `744b334`.
- **P1B-03:** Published the manual laptop handoff and rating statistics changes (`744b334`, `f1bb40e`, G1/G3 approved). The new rollout awaits laptop execution.
- **P1B-04:** Reorganized the docs and archived retired files as requested (G1). Checked links and phase records. Documentation only.

## Archived

- **Phase and implementation history:** [Completed-phase ledger](archive/complete_phases.md) · [Implementation status](archive/implementation-status.md)
- **Completed plans:** [Phase 0 proposal](archive/phase-0-proposal.md) · [Foundation manifest](archive/foundation-manifest.md) · [AI foundation manifest](archive/ai-foundation-manifest.md) · [IMDb import proposal](archive/imdb-import-proposal.md)
- **Superseded runbook:** [Initial Docker instructions](archive/docker-instructions.md). Use the laptop deployment handoff for rollouts.
