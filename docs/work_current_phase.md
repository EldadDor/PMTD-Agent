# Current phase

Last reviewed: 2026-11-20 (Asia/Jerusalem)

Phase 0 — Technical validation and decisions. Phase 1A foundation work already
has implementation evidence, which is preserved in the implementation history.

| ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed |
| --- | --- | --- | --- | --- | --- |
| P0-01 | Save the supplied plan and adapt repository setup | Completed | — | Setup committed in `1818702`. | 2026-11-20 |
| P0-02 | Inspect the workspace and tools | Completed | — | See initial setup checks in the implementation history. | 2026-11-20 |
| P0-03 | Prepare architecture, backlog and validation checklist | Completed | — | Recorded in `phase-0-proposal.md`. | 2026-11-20 |
| P0-04 | Resolve remaining technical and provider decisions | In progress | — | Desktop hosting, PostgreSQL and Spring AI approved. LINE account ready for later webhook validation. Spark is the first Ollama trial, with final model choice pending. Scheduling and feedback remain deferred. See `decisions.md`. | 2026-11-20 |
| P0-05 | Verify approved foundation toolchain | Completed | — | Build, HTTP checks and packaging recorded for `1818702`. | 2026-11-20 |
| P0-06 | Verify Spring AI with models disabled | Completed | — | `992e7f2`; three tests passed and packaging succeeded. | 2026-11-20 |
| P0-07 | Document LINE setup | Completed | — | Setup and pending verification in `line-api-setup.md`. | 2026-11-20 |
| P0-08 | Adopt the supplied instructions template and phase files | Completed | — | User explicitly authorized this scope. Verified the full template in `.codex/AGENTS.md`, the root pointer, documentation links and `git diff --check`. Documentation only. | 2026-11-20 |
| P0-09 | Implement IMDb importer | Completed | — | G2 approved. 23 offline and 14 isolated PostgreSQL tests passed. Packaged CLI imported a synthetic file and skipped its repeat. Real export import requires separate authorization. | 2026-11-20 |
| P0-10 | Import the supplied IMDb ratings export into `pmtd_agent` | Completed | — | User explicitly approved the import. Packaged CLI inserted 1,085 ratings with zero invalid, updated, unchanged or skipped. A read-only PostgreSQL query confirmed the batch totals and 1,085 stored ratings. | 2026-11-20 |
| P0-11 | Validate Spark-X2.6-4B on the laptop Ollama server | Completed | — | G1 authorized by the user's trial and connection request. `curl` `/api/version`, `/api/tags` and `/api/chat` passed on Ollama 0.40.16. Two synthetic JSON chats passed: native API (25.46 s including 5.35 s load) and Spring AI 2.0.2 via a Java probe (5.98 s). Updated README and decisions. No Gradle suite run for this focused check. P1D-02 adapter and bake-off remain pending. | 2026-11-20 |
| P0-12 | Prepare first discovery slice | Completed | — | G2 approved. Implemented TVmaze client, CLI, README and fixture tests. `gradlew --offline --no-daemon test bootJar` passed 28 offline tests, including five discovery fixtures. Packaged CLI rejected invalid input and, after the user authorized proceeding with TVmaze, returned three attributed episodes for the public US sample on 2014-12-01 (`--tvmaze-limit=3`). The authorized live smoke check covered packaged success. Phase 1C closure remains subject to its prerequisites. | 2026-11-20 |
| P0-13 | Record TVmaze and watched history decision and checkpoint current work | Completed | — | User chose free TVmaze and local PostgreSQL watched status and explicitly authorized a local commit of current changes. See D08. Check whitespace and review staged paths and credential candidates. Existing test evidence is recorded in P0-09 through P0-12. | 2026-11-20 |
| P0-14 | Review Phase 0 acceptance and carry-over | Awaiting G4 | G4 | G4 requested to close Phase 0 with carry-over. Verified stack, V1 PostgreSQL, IMDb import, TVmaze public discovery, and Spark inference. Carry TMDB access and release region into discovery, LINE quota and webhook checks into messaging, and Ollama hardware measurement and model choice into evaluation. Scheduling, feedback and deployment remain deferred. Phase 1A acceptance follows separately. | 2026-11-20 |
