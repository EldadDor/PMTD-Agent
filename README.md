# Personal Movie & TV Discovery Agent

A local-first movie and TV discovery project. The initial foundation contains a
Kotlin/Spring Boot application and a loopback health endpoint. IMDb import,
discovery, ranking, persistence, AI and messaging are not implemented yet.

## Build and test

Requires Java 25. The checked-in Gradle wrapper selects Gradle 9.2.1; no global
Gradle installation is needed. The first build downloads the selected Gradle
distribution and Maven/plugin dependencies.

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon bootJar
```

Test results: `build/reports/tests/test/index.html`.
Packaged application: `build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar`.

## Local operation

When ready to start the app locally:

```powershell
$env:PMTD_PORT = '8080'
java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar
```

`GET http://127.0.0.1:8080/actuator/health` reports `status: UP` when healthy
and may include liveness/readiness group names. The server binds to loopback and exposes only the health actuator
endpoint, with component details disabled. Stop with Ctrl+C. `.env.example`
documents configuration; `.env` is not automatically loaded. Boot's standard
console logging is used; avoid credentials or personal history in log messages.

## Project records and privacy

See [implementation status](docs/implementation-status.md),
[decisions](docs/decisions.md), and the
[foundation manifest](docs/foundation-manifest.md).

Keep real IMDb exports, ratings, feedback, tokens and databases in ignored
`private/` or `data/` directories. Use fabricated test data. `.gitignore` also
excludes environment files, CSV exports, databases and build caches; review
staged files before any separately authorized commit. No database, messaging
provider, model download or deployment is configured by this scaffold.
