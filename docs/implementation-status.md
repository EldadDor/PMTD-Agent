# Implementation status

Last reviewed: 2026-11-16 (Asia/Jerusalem)

Current phase: Phase 0 — technical validation and decisions.
Scope and the P1A-01 foundation manifest are approved; the foundation scaffold is
implemented and verified. Phase 0 and Phase 1A remain open; no phase closed.

## Initial state

Only `AGENTS.md` and `.codex/` existed, copied from Password Manager Central.
No Git repository, application, build, wrapper, tests, handoff or phase records
existed. Python/Express/React/Android, Firestore, vault and Kubernetes instructions
were unrelated to this project and are replaced by the supplied specification.

## Running task list

| ID | Task | Status | Evidence / next step |
| --- | --- | --- | --- |
| P0-01 | Save supplied plan; adapt instructions/configuration | Completed | Explicitly requested project setup; configuration checks below |
| P0-02 | Inspect workspace/tools | Completed | Read-only probes below |
| P0-03 | Architecture, decisions, backlog, verification checklist | Completed proposal | See `phase-0-proposal.md`; recommendations require approval |
| P0-04 | Resolve region, hardware, versions, database/provider | Partially resolved | Exact foundation versions approved; other choices remain open |
| P0-05 | Prove approved foundation toolchain/dependencies | Completed for foundation | Checksum-pinned wrapper; compilation, test and bootJar successful; AI compatibility not yet exercised |
| P1A-01 | Minimal health/config/logging scaffold | Completed and verified | 2 HTTP tests passed; executable jar built; user milestone acceptance not yet recorded |
| P1A-02 | Review schema, keys and transaction/resend semantics | Next; not implemented | Database selection and migration manifest require approval |

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
changes for continuation in a new session. Consult Git history for its ID.
