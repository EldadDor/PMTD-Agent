# Personal Movie & TV Discovery Agent

A local-first movie and TV discovery project. The initial foundation contains a
Kotlin/Spring Boot application and a loopback health endpoint. IMDb import,
discovery, ranking and messaging are not implemented yet. PostgreSQL JDBC
repositories and a V1 migration are available through the opt-in `postgres` profile.
The Spring AI 2.0.2 Ollama starter is present. The default application disables
chat, embeddings and model pulling. Spark-X2.6-4B is the first laptop Ollama trial.

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

The current POC target is this Windows desktop. In IntelliJ, open the project as
a Gradle project, choose JDK 25 for the project and Gradle JVM, and use the Gradle
wrapper. Set Gradle user home to this checkout's `.gradle-user-home` if needed.
Run `dev.pmtd.agent.MovieTvAgentApplicationKt` to start the app; set `PMTD_PORT`
in the run configuration's environment to override port 8080. The default app
needs no database, model, LINE credentials or schedule. `.env` is not auto-loaded.

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

The desktop app uses a dedicated database on the user's laptop PostgreSQL. The
default profile excludes datasource/Flyway setup even
if database environment variables are present. The `postgres` profile wires JDBC
and runs the approved V1 migration on startup. See the
[persistence manifest](docs/persistence-manifest.md). The laptop already has
`SparkLLM/Spark-X2.6-4B:latest` installed for the inference trial. See the
[current phase board](docs/work_current_phase.md) for validation and the
[decision register](docs/decisions.md) for model selection status. Scheduling and
feedback are deferred.

The LINE account is PMTD-Agent (`@966sowkr`); see the
[API setup guide](docs/line-api-setup.md). Messaging API is enabled. The user will
enter the ngrok HTTPS webhook URL in LINE when the handler is ready.
Kind + ArgoCD and Hostinger VPS are future context; desktop POC work adds no
Docker, cluster or remote deployment configuration.

## TVmaze manual discovery

The public TVmaze schedule requires no API key. Provide an uppercase country
code, date and result limit from 1 to 100. The country schedule lists episode airings and does
not include global web channels. Results are displayed locally with TVmaze credit.

```powershell
java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar --tvmaze-country=US --tvmaze-date=2026-11-20 --tvmaze-limit=20
```

## TVmaze schedule import

After V2 has been applied to an explicitly authorized PostgreSQL target, the
manual import stores a complete country schedule and a checkpoint. Run from the
repository root with your local database configuration and replace the example
country and date with your chosen values:

```powershell
java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar '--spring.profiles.active=postgres,local' --tvmaze-import-country=US --tvmaze-import-date=2026-11-20
```

The command disables automatic Flyway migration. It stores TVmaze observations
separately from IMDb titles and does not infer streaming availability. TV data
is supplied by TVmaze under CC BY-SA.

## PostgreSQL setup

The laptop hosts `pmtd_agent` with schema `pmtd` and a dedicated `pmtd_app` login.
Flyway applied V1 there. The ignored root `application-local.yml` holds the
desktop's connection settings and LINE channel settings. The PostgreSQL server
currently lacks TLS, so this local LAN connection uses `sslmode=disable`.

Run from the repository root with the `postgres,local` profiles. In IntelliJ,
set the working directory to the repository root and add
`--spring.profiles.active=postgres,local` to the program arguments.

```powershell
java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar '--spring.profiles.active=postgres,local'
```

JDBC operations preserve trusted source IDs, retain the first import provenance on
rating updates and allow composing writes in a Spring transaction. These are
storage primitives. The IMDb import command has been tested with fabricated data.
Repositories are active only under
the `postgres` profile. Never log private rating records or driver error details.

Once validated and authorized for your export, run it from the repository root
using the local PostgreSQL settings. Replace the sample path with your export.
The command uses the current directory for the ignored local configuration.

```powershell
java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar '--spring.profiles.active=postgres,local' '--import-imdb=docs/resources/your-export.csv'
```

The command reports counts and record numbers for invalid or conflicting rows.
It does not print titles or ratings. Equal ratings are counted as unchanged, and
newer known rating dates are retained. Identical exports are skipped by digest.

To show the local rating distribution, run:

```powershell
java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar '--spring.profiles.active=postgres,local' --imdb-stats
```

The command prints counts for each rating from 1 to 10, including zeros. It
uses the existing database and disables Flyway for this invocation. The output
is personal aggregate information; keep it private. Genre statistics are not
available because the importer does not store genres.

Regular `test` excludes PostgreSQL integration tests and forces the test profile;
it needs no database credentials. It compiles the integration suite and checks
offline profile wiring with Flyway disabled, without opening a DB connection.

For an integration run, use `pmtd_agent_test` and provide all `PMTD_TEST_DB_*`
variables in the invoking process. Use an existing,
empty schema named `pmtd_test_<unique-suffix>` (lowercase letters, digits and
underscores, at most 63 characters total). The suite checks emptiness before
applying V1, uses fabricated data, and never falls back to `PMTD_DB_*` credentials.

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat --no-daemon postgresTest
```

The task fails before tests if settings are missing. The suite checks migration
repeatability, schema routing, trusted-ID conflicts, rating constraints/provenance,
duplicate batches under concurrency, rollback and restart persistence. It retains
the synthetic schema/data for inspection; no automatic drop/cleanup occurs. Each
suite run needs a fresh empty schema. Reports are in `build/reports/tests/postgresTest/`.

## Project records and privacy

See the [project index](docs/README.md) for phase status, decisions, deployment
instructions and archived implementation evidence.

Keep real IMDb exports, ratings, feedback, tokens and databases in ignored
`private/` or `data/` directories. Use fabricated test data. `.gitignore` also
excludes environment files, CSV exports, databases and build caches; review
staged files before any separately authorized commit. Keep `application-local.yml`
out of source control and the packaged app.
