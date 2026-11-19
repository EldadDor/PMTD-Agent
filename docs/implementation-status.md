# Implementation status

Last reviewed: 2026-11-19 (Asia/Jerusalem)

Current phase: Phase 0 — technical validation and decisions.
Scope, the P1A-01 foundation manifest and Spring AI 2.0.2 are approved; the
foundation and disabled AI dependency are implemented and verified. JDBC/Flyway,
V1 and persistence primitives are verified against laptop PostgreSQL. Phase 0
and Phase 1A remain open pending full phase acceptance.

## Initial state

Only `AGENTS.md` and `.codex/` existed, copied from Password Manager Central.
No Git repository, application, build, wrapper, tests, handoff or phase records
existed. Python/Express/React/Android, Firestore, vault and Kubernetes instructions
were unrelated to this project and are replaced by the supplied specification.

## Running task list

| ID | Task | Status | Evidence / next step |
| --- | --- | --- | --- |
| P0-01 | Save supplied plan; adapt instructions/configuration | Completed | `1818702`; explicitly requested project setup; configuration checks below |
| P0-02 | Inspect workspace/tools | Completed | Read-only probes below |
| P0-03 | Architecture, decisions, backlog, verification checklist | Completed proposal | See `phase-0-proposal.md`; recommendations require approval |
| P0-04 | Resolve region, hardware, versions, database/provider | Partially resolved | Spring AI 2.0.2, laptop PostgreSQL and desktop Windows operation now approved; LINE intended/account reported; quota/channel/hardware details remain open; models/schedule/feedback deferred |
| P0-05 | Prove approved foundation toolchain/dependencies | Completed for foundation | `1818702`; checksum-pinned wrapper; recorded compilation, test and bootJar successful; AI library validation now recorded separately in P0-06 |
| P1A-01 | Minimal health/config/logging scaffold | Completed and verified | `1818702`; recorded 2 HTTP tests passed; executable jar built; user milestone acceptance not yet recorded |
| P1A-02 | Review schema, keys and transaction/resend semantics | Approved on 2026-11-18 | User approved exact dependencies and V1 creation from `persistence-manifest.md`; future delivery/feedback policies remain deferred |
| P1A-03 | Add JDBC/PostgreSQL and reviewed migration | Implemented and verified | 8 PostgreSQL integration tests passed on `pmtd_agent_test`. V1 applied to `pmtd_agent.pmtd`. The implementation remains uncommitted. |
| P0-06 | Validate approved Spring AI 2.0.2 foundation | Completed locally for disabled AI foundation | Uncommitted. Previously verified: 3 tests passed, 15 Spring AI artifacts resolved at 2.0.2, and bootJar succeeded. Inference is deferred. |
| P0-07 | Explain LINE API setup for reported account | Documentation completed | `line-api-setup.md`; official requirements reviewed, account channel/quota not inspected |
| P1D-02 | Model selection, inference and bake-off | Deferred by user | Do not download models; user will select later |
| P1C-02 / P1E-01 | Scheduling and feedback | Deferred by user | Keep manual/fixture path; revisit decisions later |
| P1A-04 / P1F-02 | Container/deployment work | Deferred for current POC | Desktop IntelliJ operation first; no Kind/ArgoCD/VPS setup |

## Laptop PostgreSQL and LINE setup — 2026-11-19

The user authorized laptop PostgreSQL provisioning and supplied an existing login.
`psql` verified PostgreSQL 16.14, the supplied role's database-creation access,
and that the server has TLS disabled. Created `pmtd_app` with a generated password
and no superuser, database-creation or role-creation privilege. The login owns
`pmtd_agent` and `pmtd_agent_test`. Created `pmtd` for application data and
`pmtd_test_initial` for synthetic integration data. The existing database was used
as the maintenance connection for cluster-level provisioning. Its schema and data
were left intact.

`bootJar` succeeded. A non-web packaged-app startup with the `postgres,local`
profiles applied V1 to `pmtd_agent.pmtd` and exited normally. `psql` confirmed
Flyway history version 1, all four domain tables and zero imported ratings.
A second startup with the documented profiles and default config resolution
loaded the root local YAML and found the schema already at V1.
Gradle `postgresTest` passed all eight tests with zero failures, errors or skips.
It verified migration repeatability, schema routing, trusted IDs, rating constraints
and provenance, duplicate batch concurrency, rollback, and restart persistence.
Synthetic rows remain in the disposable database for inspection.
The regular Gradle `test` task was up to date and retained the previous five
passing offline results.

The user confirmed LINE Messaging API activation and selected ngrok for desktop
webhook testing. Channel settings and the generated PMTD database password are in
Git-ignored `application-local.yml`. The webhook handler and tunnel are pending.
The user will register the final HTTPS URL in LINE. See `line-api-setup.md`.

## Phase sync — 2026-11-19

Reviewed Git history from the start of 2026-11-18 (Asia/Jerusalem) through HEAD.
The only commit remains `1818702`, which contains the initial foundation.
The Spring AI and PostgreSQL changes are present in the working tree. The
validation below comes from the previous implementation runs. Phase 0 and
Phase 1A are open, with PostgreSQL integration and phase acceptance pending.

## Approved persistence implementation — 2026-11-18

The user replied **Approved** to adding the exact JDBC/Flyway dependencies and
creating V1 from `persistence-manifest.md`. Preserved all pre-existing changes;
no staging/commit/push/merge. Implemented:

- `build.gradle.kts`: JDBC/Flyway starters, PostgreSQL Flyway module and driver;
  regular tests exclude tag `postgres` and force the test profile; `postgresTest`
  requires separate target settings and disables up-to-date/build-cache reuse.
- `application.yml`: datasource/Flyway exclusions when `postgres` is inactive.
  `application-postgres.yml`: required external connection settings, dedicated
  existing schema, finite timeouts, no schema creation/clean/automatic baseline.
- `db/migration/V1__catalog_and_imdb_history.sql`: exactly matches the approved
  manifest SQL (verified by normalized full-text comparison). **Not applied.**
- `catalog/CatalogTitle.kt`, `CatalogRepository.kt`, `imdb/ImdbHistory.kt`,
  `ImdbHistoryRepository.kt`: explicit title upsert/lookup, conflict-safe trusted
  IDs, digest-unique batch insertion/lookup, rating storage preserving first
  import provenance. Storage primitives join caller transactions; no CSV importer,
  parsing/update policy, API endpoint or manual import command is implemented.
- `ApplicationHealthTest.kt`, `persistence/PostgresProfileTest.kt` and
  `PostgresPersistenceTest.kt`: default/no-model/no-DB health, offline JDBC wiring,
  and separately tagged migration/constraint/concurrency/rollback/restart checks.
- README, `.env.example`, decision/manifest/proposal records: setup, profile
  behavior, test-target separation and approval boundaries.

Assigned tester ran with ignored workspace `GRADLE_USER_HOME` and escalation for
the known Gradle loopback restriction:

- `.\gradlew.bat --no-daemon test`: final run **5 passed, 0 failures, 0 errors,
  0 skipped** (health class 4, offline profile class 1). All main/test Kotlin code
  compiled, including 8 tagged PostgreSQL tests that were **excluded, not run**.
- Dependency inspection confirmed JDBC/Flyway starters **4.1.2**, Flyway core and
  PostgreSQL module **12.4.0**, and pgJDBC **42.7.14**.
- `.\gradlew.bat --no-daemon postgresTest`, in a child process with every
  `PMTD_TEST_DB_*` variable removed: expected exit 1 before test execution with
  missing-settings guard. This validates refusal only, not database behavior.
- Read-only review identified possible ambient Spring-property redirection of
  integration writes. Main corrected the suite to use a fresh Spring environment
  without ambient system/environment property sources; tester reviewed and reran
  offline tests successfully. No tester edits. Existing non-blocking Mockito/JVM
  dynamic-agent/class-sharing warnings remain.

Main ran `.\gradlew.bat --no-daemon bootJar`: **BUILD SUCCESSFUL**. Inspected the
jar and confirmed V1 SQL, postgres profile and pinned JDBC/Flyway libraries are
packaged. Jar was not separately launched. Reviewed JUnit XML (4 + 1 passing),
verified privacy ignores and `rtk git diff --check` passed. Reports:
`build/reports/tests/test/index.html`, `build/test-results/test/*.xml`,
`build/postgres-missing-settings-guard.txt`, and
`build/persistence-{flyway,postgres,jdbc}-dependencies.txt` (all ignored).

No PostgreSQL connection, migration execution, server provisioning or cleanup
occurred. Actual SQL constraints, schema routing, transactions, concurrent writes
and persistence across restart remain **unverified**. P1A-03 acceptance stays open
pending an explicitly authorized disposable database/schema and actual test run.
Laptop version/port/database/schema/TLS/credentials and execution authorization
are still pending. No model download, real send, scheduler/feedback implementation
or deployment change. No phase closed.

## AI foundation continuation — 2026-11-18

Preserved the pre-existing uncommitted 2026-11-17 phase-sync edits. User decisions
are recorded in `decisions.md`. Spring AI 2.0.2 is explicitly approved; introduced
its BOM and Ollama starter under `ai-foundation-manifest.md`, without selecting a
model. Chat/embedding autoconfiguration is disabled and model pulling is `never`.
The startup regression test uses an isolated fake loopback Ollama endpoint, checks
absence of model beans and zero requests, alongside the existing HTTP checks.

Assigned tester ran, with `$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'`:

- `.\gradlew.bat --no-daemon test`: **3 tests passed, 0 failures, 0 errors,
  0 skipped**. Main/test Kotlin compilation and random-port Boot 4.1.2 startup on
  Java 25 succeeded. Health UP with no details, sensitive endpoints 404, no model
  beans, pull strategy `never`, and zero requests to fake Ollama verified.
- `.\gradlew.bat --no-daemon dependencyInsight --dependency spring-ai --configuration runtimeClasspath`:
  **BUILD SUCCESSFUL**; 15 unique selected Spring AI coordinates are all 2.0.2.

Main agent ran `.\gradlew.bat --no-daemon bootJar`: **BUILD SUCCESSFUL**,
producing `build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar`. It was packaged, not separately
launched. Default sandbox testing could not connect to the loopback Gradle daemon;
escalated approved tests/dependency resolution/build succeeded. Non-blocking
Mockito dynamic-agent/JVM class-sharing warnings remain. Tester made no edits.
Reports: `build/reports/tests/test/index.html`,
`build/test-results/test/TEST-dev.pmtd.agent.ApplicationHealthTest.xml`, and
`build/ai-dependency-insight.txt` (all ignored). Main reviewed the changed
configuration/test and results; `rtk git diff --check` passed. No inference,
database, discovery or messaging integration test ran; those remain unverified.

Prepared `persistence-manifest.md`: JDBC/Flyway exact-version proposal and a V1
schema for catalog titles, namespaced trusted IDs, import batches and ratings.
This is a reviewed design artifact for user review, not applied DDL. Database
integration tests require an authorized disposable PostgreSQL resource; no H2 or
unapproved container dependency is substituted. PostgreSQL version, connection
details and target schema will be confirmed later. No laptop connection attempted.

Prepared `line-api-setup.md` using official LINE documentation: account/channel
distinctions, activation, token/signature responsibilities, friend/recipient setup,
region-specific quota and public HTTPS ingress for future incoming events. The
supplied manager/public links were inaccessible to the web tool; no account state
was inspected or changed. No messaging implementation or real message was sent.

Desktop Windows/IntelliJ operation is the current target. No model download,
schedule, feedback implementation, migrations or deployment configuration added.
No commit, push or merge performed. Phase 0 and Phase 1A remain open; no phase
closure or full POC/MVP completion is claimed.

## Project start — 2026-11-16

- User approved the reviewed plan, then the exact `foundation-manifest.md`.
- Added privacy/build ignores, `.env.example`, README, Gradle Kotlin build,
  application entry point, loopback-only health configuration and HTTP tests.
- At project start, `git rev-parse --show-toplevel` and status worked; the
  repository was on unborn `master`, with origin and author identity configured.
- Temporary `.git` write probe failed in the default sandbox and passed with
  escalation; probe removed. Git writes need escalation in this session. Remote
  authentication/push permission was untested. No staging/commit/push had occurred.
- `git check-ignore --no-index` verified private samples, ratings CSV, environment,
  SQLite and build output are ignored; `.env.example` is allowed.
- Global Gradle 8.10 fails on a protected native DLL lock and predates Java 25
  runtime support. Downloaded approved Gradle 9.2.1 into ignored workspace cache
  and verified against the official SHA-256. Default sandbox DNS failed;
  escalated download succeeded. Global tool installation is unchanged.
- Wrapper generation succeeded using the verified bootstrap distribution; wrapper
  distribution SHA-256 is pinned in `gradle/wrapper/gradle-wrapper.properties`.

Initial validation: configured tester ran
`$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'; .\gradlew.bat --no-daemon test`
with escalation for artifact downloads and isolated loopback connections. Main
and test compilation passed. First run: 2 tests, 1 passed, 1 failed, 0 skipped.
Health returned UP plus liveness/readiness groups; the exact-body assertion was
too strict. Corrected it to parse JSON and check UP with no components/details;
the sensitive-endpoint test already passed.

Final tester rerun, same command: **2 tests, 0 failures, 0 errors, 0 skipped**.
Random-port Spring Boot startup and HTTP health UP/no internal details verified;
`env`, `configprops`, `beans`, `loggers`, `heapdump` return 404. A non-blocking
Mockito dynamic-agent warning appeared on Java 25; no test was skipped.

Main agent ran `.\gradlew.bat --no-daemon bootJar` with workspace Gradle cache
and escalation: **BUILD SUCCESSFUL**, producing
`build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar`. `rtk git diff --check` passed;
10 privacy/ignore assertions plus the untracked private/generated-file candidate
scan passed. The executable jar was packaged; it was not separately launched.
Only test-scoped application startup occurred. No manual/live service started.

Foundation files: `settings.gradle.kts`, `build.gradle.kts`, `gradlew`,
`gradlew.bat`, `gradle/wrapper/*`, application entry point, `application.yml`,
`ApplicationHealthTest.kt`, `.gitignore`, `.env.example`, `README.md` and records.
Database, imports, discovery, ranking, AI, messaging, Docker and deployment remain
unimplemented. No real messages, model downloads or migrations have occurred.
Next: select the database, review schema and obtain explicit approval for the
driver/migration manifest before persistence work. Provider and hardware choices
remain open. These checks do not complete Phase 0 or all Phase 1A acceptance.

## Initial setup checks (before project-start approval)

- Directory enumeration and `rg --files` confirmed no application files.
- `rtk git status --short`: not a Git repository. No init, commit or push.
- `rtk ls`: MSYS access failure; used native .NET directory enumeration instead.
- `java -version`: Java 25.0.4.1 LTS available.
- `kotlinc -version`: not on PATH. A global compiler is not required for a Gradle build.
- `gradle --version`: failed loading `native-platform.dll`; build capability unproven.
- `ollama --version`: client 0.40.0, no running server reachable. No model loaded/downloaded.
- `docker --version`: not on PATH; container capability unproven.
- Official documentation inspected; this does not verify integrations or compilation.
- No application tests: no application exists. No services started or configured.
- Plan copy verified byte-identical to the attachment.
- Hook JSON parsed; referenced PMTD-Agent script exists. Both TOML files parsed;
  session safe.directory is scoped to this checkout. No old absolute project path
  remains in active instructions/configuration.
- Hook PowerShell syntax parsed without errors; no-repository smoke check exited 0.
  Existing read-only hook script was preserved. Actual SessionStart execution
  by the desktop client was not verified; configuration reload may be needed.
- Automatic approval review initially blocked `.codex` replacement. The user then
  explicitly approved the rules/path/tester changes; the reviewed retry succeeded.
  No configuration work remains blocked. No application scaffold was created.

Phase-record status: Phase 0 and Phase 1A open; P1A-01 verified, awaiting user
acceptance. On 2026-11-16 the user authorized an initial commit of all current
changes for continuation in a new session. Commit `1818702` (2026-11-16),
`Initialize movie and TV agent foundation`, contains that setup and scaffold.

## Phase sync — 2026-11-17

Reviewed history from the start of the previous review date (2026-11-16,
Asia/Jerusalem) through HEAD `1818702`, including same-day commits. The initial
commit is the only commit; no later implementation or phase-closure evidence
exists in Git history. The working tree was clean before this documentation sync.
Compared the committed build, wrapper, health configuration and HTTP tests with
the task records. Validation results above are retained from the previous session;
no application tests or builds were run during this sync. Phase 0 and Phase 1A
remain open. No new implementation, dependency, migration, deployment, commit,
push or merge approval is recorded by this sync.
