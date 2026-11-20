# Completed phases

## Phase 0 — Technical validation and decisions

Closed on 2026-11-20 (Asia/Jerusalem). G4 approved by the user. The verified
toolchain and decision records, PostgreSQL V1, the IMDb importer, the public
TVmaze dry run and the Spark inference trial support the next phase.

Carry-over: TMDB access and release region go to discovery, LINE quota and
webhook validation go to messaging, and hardware measurements and final model
selection go to model evaluation. Scheduling, feedback and deployment remain
deferred. Watched status will use local PostgreSQL and still needs schema approval.

| ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed |
| --- | --- | --- | --- | --- | --- |
| P0-01 | Save the supplied plan and adapt repository setup | Completed | — | Setup committed in `1818702`. | 2026-11-20 |
| P0-02 | Inspect the workspace and tools | Completed | — | See initial setup checks in the implementation history. | 2026-11-20 |
| P0-03 | Prepare architecture, backlog and validation checklist | Completed | — | Recorded in `phase-0-proposal.md`. | 2026-11-20 |
| P0-04 | Resolve remaining technical and provider decisions | Deferred | — | Remaining decisions carried forward as approved above. | 2026-11-20 |
| P0-05 | Verify approved foundation toolchain | Completed | — | Build, HTTP checks and packaging recorded for `1818702`. | 2026-11-20 |
| P0-06 | Verify Spring AI with models disabled | Completed | — | `992e7f2`; three tests passed and packaging succeeded. | 2026-11-20 |
| P0-07 | Document LINE setup | Completed | — | Setup and pending verification in `line-api-setup.md`. | 2026-11-20 |
| P0-08 | Adopt the supplied instructions template and phase files | Completed | — | User explicitly authorized this scope. Verified the full template in `.codex/AGENTS.md`, the root pointer, documentation links and `git diff --check`. Documentation only. | 2026-11-20 |
| P0-09 | Implement IMDb importer | Completed | — | G2 approved. 23 offline and 14 isolated PostgreSQL tests passed. Packaged CLI imported a synthetic file and skipped its repeat. Real export import requires separate authorization. | 2026-11-20 |
| P0-10 | Import the supplied IMDb ratings export into `pmtd_agent` | Completed | — | User explicitly approved the import. Packaged CLI inserted 1,085 ratings with zero invalid, updated, unchanged or skipped. A read-only PostgreSQL query confirmed the batch totals and 1,085 stored ratings. | 2026-11-20 |
| P0-11 | Validate Spark-X2.6-4B on the laptop Ollama server | Completed | — | G1 authorized by the user's trial and connection request. `curl` `/api/version`, `/api/tags` and `/api/chat` passed on Ollama 0.40.16. Two synthetic JSON chats passed: native API (25.46 s including 5.35 s load) and Spring AI 2.0.2 via a Java probe (5.98 s). Updated README and decisions. No Gradle suite run for this focused check. P1D-02 adapter and bake-off remain pending. | 2026-11-20 |
| P0-12 | Prepare first discovery slice | Completed | — | G2 approved. Implemented TVmaze client, CLI, README and fixture tests. `gradlew --offline --no-daemon test bootJar` passed 28 offline tests, including five discovery fixtures. Packaged CLI rejected invalid input and, after the user authorized proceeding with TVmaze, returned three attributed episodes for the public US sample on 2014-12-01 (`--tvmaze-limit=3`). The authorized live smoke check covered packaged success. Phase 1C closure remains subject to its prerequisites. | 2026-11-20 |
| P0-13 | Record TVmaze and watched history decision and checkpoint current work | Completed | — | User chose free TVmaze and local PostgreSQL watched status. Authorized checkpoint committed as `226cc19`; staged whitespace and credential candidate checks passed. | 2026-11-20 |
| P0-14 | Review Phase 0 acceptance and carry-over | Completed | — | User explicitly approved G4 with the carry-over listed above. Updated phase records and checked the documentation diff. | 2026-11-20 |

## Phase 1A — Foundation

Closed for desktop use on 2026-11-20 (Asia/Jerusalem) with the user's G4
approval. The scaffold, health endpoint, JAR and PostgreSQL V1 were verified.
The user will run Docker manually on the laptop. Persistent packaging and
deployment validation carry forward. See [Docker instructions](docker-instructions.md).

| ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed |
| --- | --- | --- | --- | --- | --- |
| P1A-01 | Verify application scaffold and private configuration | Completed | — | Approved foundation manifest. Boot, HTTP health, profile isolation and packaging verified. The latest recorded offline run passed 28 tests and built the jar at the `226cc19` checkpoint. | 2026-11-20 |
| P1A-02 | Review persistence design and V1 migration | Completed | — | User approved the exact JDBC and Flyway dependencies and V1 schema. See `persistence-manifest.md`. | 2026-11-20 |
| P1A-03 | Implement approved PostgreSQL persistence | Completed | — | `992e7f2` contains V1 and repository code. V1 applied to the approved laptop database. Eight isolated PostgreSQL tests passed before the importer and 14 passed with it. | 2026-11-20 |
| P1A-04 | Package for Docker and document operation | Deferred | — | Persistent packaging and deployment acceptance carry forward. The user will run Docker manually on the laptop only. | 2026-11-20 |
| P1A-05 | Review foundation acceptance for desktop POC | Completed | — | User approved G4 and carried Docker into deployment. Ranking, feedback, notifications and the app AI adapter follow their feature phases. | 2026-11-20 |
| P1A-06 | Document manual Docker operation on the laptop | Completed | — | User authorized the runbook. Added `docker-instructions.md`, indexed it, and reviewed commands against project settings and Docker image documentation. The user reported completing the laptop runbook and receiving `UP` on 2026-11-20. This was the default-profile smoke check; persistent deployment remains deferred. | 2026-11-20 |
