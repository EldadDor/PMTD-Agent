# Laptop deployment handoff

This file is the handoff for manual laptop deployments. For each deployment,
update **Current rollout** with the changes, prerequisites, database or
configuration actions, verification and any rollback details. Keep the reusable
commands below current. The operator performs the steps on the laptop.

## Pending rollout — personalized preview

The G2-approved read-only ranking preview uses the existing PostgreSQL schema;
no migration is added. It reads already imported TMDB candidates and local
ratings, and requires explicit authorization before a real-history preview.
After delivery, use the reusable deployment commands below and the
[preview instructions](../README.md#personalized-recommendation-preview).
The default-profile Docker smoke check does not exercise PostgreSQL or ranking.

## Pending rollout — rated-title metadata

V4 and the enrichment command passed disposable PostgreSQL validation. The user
approved V4, and it was applied and verified on `pmtd_agent.pmtd` on 2026-11-21.
A normal application startup with the `postgres` profile applies pending Flyway migrations. The enrichment command
disables Flyway. The user approved processing the then-eligible history in
bounded batches, which completed on 2026-11-21. Requests send IMDb title IDs
to TMDB. See the
[enrichment design](rated-title-enrichment-proposal.md).

## Next rollout — TMDB persistence

Flyway applied the approved V3 migration to `pmtd_agent.pmtd` on 2026-11-21.
Both TMDB tables and the catalog index are verified. The new TMDB tables are
were initially empty. The user reported completing the laptop preview and import test steps; counts were not supplied. The import command
disables Flyway. The laptop rollout remains manual. The user authorized publishing the implementation on 2026-11-21. See the [TMDB import instructions](../README.md#tmdb-candidate-import)
and [verification](tmdb-persistence-proposal.md#verification).

## Current rollout

- **Phase 1C — TVmaze manual schedule import:** V2 adds observation and
  checkpoint tables. The user approved V2 on `pmtd_agent.pmtd` and Flyway
  applied it on 2026-11-21. Both tables and the migration history were verified.
  V1 and V2 also passed 17 synthetic PostgreSQL tests in a disposable test schema.
  The packaged US sample import ran twice against that schema, observing 74
  episodes each time without increasing the total on the second run.
- The default container health check uses no database. The new import command
  disables automatic migration. Other application starts with the `postgres`
  profile normally enable Flyway and would apply V2. See the [README](../README.md#tvmaze-schedule-import)
  for its command and [design](discovery-persistence-proposal.md) for scope.
- The laptop rollout remains manual. The initial release and verification
  commits are `29367dc` and `b61ec1f`; check that they are available in the
  remote before pulling. A production schedule import has not been run, and
  the user prioritizes language and show origin. Supply a country to query a
  TVmaze schedule when running an import.

## First-time laptop setup

Clone the repository if needed. Open the checkout in PowerShell and confirm
Docker Desktop is using Linux containers and Java 25 is on the path. The first
Gradle build and image pull need network access.

```powershell
git clone '<repository-url>' PMTD-Agent
Set-Location PMTD-Agent
docker version
docker info --format '{{.OSType}}'  # Expected: linux
java -version                     # Expected: Java 25
```

## Continuous Laptop deployment

Run in PowerShell on the laptop from the existing repository checkout. Docker
Desktop must be running with Linux containers selected and Java 25 available.
This repeats the existing default-profile smoke deployment: it mounts the built
JAR into the Temurin runtime image; it does not build a custom Docker image or
configure PostgreSQL. It replaces only the container named `pmtd-agent-smoke`.

```powershell
# From the laptop's PMTD-Agent checkout, on the intended branch:
git status --short --branch
git pull --ff-only
if ($LASTEXITCODE -ne 0) { throw 'Pull failed; resolve the checkout state first.' }

# Remove the previous named container, if present, before replacing the mounted JAR.
$existing = docker ps -aq --filter 'name=^/pmtd-agent-smoke$'
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable.' }
if ($existing) {
    docker rm -f pmtd-agent-smoke
    if ($LASTEXITCODE -ne 0) { throw 'Could not remove the previous container.' }
}

$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat --no-daemon bootJar
if ($LASTEXITCODE -ne 0) { throw 'JAR build failed; the container is stopped.' }
$jar = (Resolve-Path 'build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar').Path

docker pull eclipse-temurin:25-jre
if ($LASTEXITCODE -ne 0) { throw 'Runtime image pull failed.' }
docker run --detach --rm --name pmtd-agent-smoke `
  --publish 127.0.0.1:18080:8080 `
  --mount "type=bind,source=$jar,target=/app/pmtd-agent.jar,readonly" `
  eclipse-temurin:25-jre java -jar /app/pmtd-agent.jar `
  --server.address=0.0.0.0 --server.port=8080
if ($LASTEXITCODE -ne 0) { throw 'Container start failed.' }

# Allow startup to finish; retry this command if the app is still starting.
(Invoke-RestMethod 'http://127.0.0.1:18080/actuator/health').status
# Expected: UP
docker logs pmtd-agent-smoke
```

Leave the container running until you wish to stop it. To stop and remove it,
run `docker stop pmtd-agent-smoke`; `--rm` removes it automatically. If port
18080 is occupied, choose a different host port in both the publish option and
health URL. Keep local configuration, private IMDb exports and credentials outside the smoke container.
