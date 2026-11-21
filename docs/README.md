# Project index

Review before each task and update after it. Last reviewed: 2026-11-21 (Asia/Jerusalem).

- **Current:** [Phase 1C — Discovery and polling](work_current_phase.md). TVmaze persistence and the TMDB preview are verified. The next gap is persisted TMDB discovery and trusted catalog links. Schedule times follow a manual full run.
- **Next:** [1D Ranking and AI](next_phase.md) · [Later phases](proposed_next_phases.md). G0 approved the Phase 1C sequence and roadmap arrangement.
- **Work references:** [Product plan](../movie_tv_agent_codex_plan.md) · [Decisions](decisions.md) · [App setup](../README.md) · [Laptop deployment](laptop-deployment-handoff.md) · [PostgreSQL schema](persistence-manifest.md) · [Discovery design](discovery-persistence-proposal.md) · [TMDB preview](tmdb-discovery-proposal.md) · [TVmaze](tvmaze-discovery-proposal.md) · [LINE](line-api-setup.md)

## Completed

- **P1C-02:** Read-only TMDB preview verified against the live API and disposable catalog. 39 offline tests passed. [Validation evidence](tmdb-discovery-proposal.md#verification)

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
