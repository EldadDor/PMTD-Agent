# Foundation execution manifest

Last reviewed: 2026-11-16 (Asia/Jerusalem)

The user approved the reviewed plan and requested project start, then explicitly
approved this exact manifest, downloads and isolated loopback tests on 2026-11-16.
P1A-01 is implemented: compilation and both HTTP tests passed, and bootJar built.
See `implementation-status.md` for commands, limitations and remaining phase work.

## Proposed exact toolchain

| Component | Version / scope |
| --- | --- |
| Java | Existing Java 25.0.4.1 LTS; JVM target 25 |
| Spring Boot plugin and dependency BOM | 4.1.2 |
| Kotlin JVM and Spring plugins | 2.3.21; matches Boot-managed Kotlin |
| Gradle wrapper | 9.2.1, binary distribution with official SHA-256 |
| Production dependencies | `spring-boot-starter-webmvc`, `spring-boot-starter-actuator` at 4.1.2; `kotlin-reflect` at 2.3.21 |
| Test dependencies | `spring-boot-starter-webmvc-test` at 4.1.2; JUnit platform launcher version managed by Boot BOM |

Use native Gradle BOM support; no additional dependency-management plugin.
No custom JSON payload is needed for the health endpoint. Add Jackson Kotlin
support when a feature needs Kotlin request/response types, with a separate
manifest update. Spring AI, database drivers and messaging are later additions.

Official documentation checked on the review date:

- [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html):
  Java 25 and Gradle 9.x supported.
- [Boot managed dependencies](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html):
  Kotlin 2.3.21.
- [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html):
  Kotlin 2.3.20–2.3.21 fully supports Gradle through 9.3.0.
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html):
  running on Java 25 requires Gradle 9.1.0 or newer.
- [Gradle 9.2.1](https://docs.gradle.org/9.2.1/release-notes.html).

These sources support the proposed combination; artifact resolution, compilation
and HTTP tests must still prove it. Alternative: Kotlin 2.4.21 with a compatible
Gradle version, overriding Boot's managed Kotlin. Prefer fewer overrides initially.

## Intended files and behavior

- `settings.gradle.kts`, `build.gradle.kts`, wrapper scripts and
  `gradle/wrapper/*`.
- `src/main/kotlin/dev/pmtd/agent/MovieTvAgentApplication.kt`.
- `src/main/resources/application.yml`: application name, loopback binding,
  configurable port, only health exposed, no health details.
- `src/test/kotlin/dev/pmtd/agent/ApplicationHealthTest.kt`: random-port embedded
  server; HTTP health status/body and non-exposure of sensitive actuator endpoints.
- `README.md`, `.env.example`, existing `.gitignore`, status/decision updates.

Use existing Java; no automatic JDK provisioning. Download only the approved
Gradle distribution and artifacts from official Gradle/plugin/Maven repositories.
Use ignored workspace `.gradle-user-home` for build caches to avoid writing to
the protected global Gradle installation. No migrations or deployment files.

Commands after approval (PowerShell):

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon bootJar
```

The tester runs the focused tests; random-port loopback listeners exist only for
isolated tests. Manual `bootRun`, Ollama and other live services remain separate.
Rollback removes only the newly added scaffold and ignored generated caches;
retain planning records and unrelated user files. No commit/push/merge authorized.

## Preflight findings

- Git repository initialized on unborn `master`, with `origin` configured.
- Git reads work; the default sandbox denies `.git` writes despite permissive
  Windows ACLs. Escalated write-probe result is recorded in implementation status.
- Global Gradle is 8.10. Native startup fails opening its DLL lock under
  `C:/config/gradle/native` (access denied); it also predates Java 25 runtime
  support. Do not repair the global installation or assume changing caches alone
  makes it compatible. Bootstrap the approved wrapper independently.
- Privacy ignores checked using `git check-ignore --no-index`: `.env`, private
  samples, ratings CSV, SQLite and build output ignored; `.env.example` allowed.
