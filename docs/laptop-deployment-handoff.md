# Laptop deployment handoff

This file is the handoff for manual laptop deployments. For each deployment,
update **Current rollout** with the changes, prerequisites, database or
configuration actions, verification and any rollback details. Keep the reusable
commands below current. The operator performs the steps on the laptop.

## Current rollout

- **2026-11-20 — IMDb rating distribution:** Adds `--imdb-stats` for a local
  PostgreSQL profile. The command displays aggregate counts for ratings 1–10.
- **Database and configuration:** No migration or dependency was added. The
  statistics command disables Flyway. It needs PostgreSQL connection settings
  and the `postgres` profile; the container below uses the default profile and
  does not connect to PostgreSQL. Configuring container database access is a
  separate deployment task.
- **Verification:** After the rollout, the container health check should show
  `UP`. This confirms startup only. The statistics command has not yet been
  verified against a disposable PostgreSQL target; the offline suite passed 30
  tests. To run the statistics command on a host with the local PostgreSQL
  configuration, use the command in the [README](../README.md#postgresql-setup).
  Do not paste private rating counts into public logs or Git.
- **Rollback:** If startup fails, inspect `docker logs pmtd-agent-smoke`. To
  return to a known earlier revision, check out that revision in the laptop
  clone, then run the container steps starting at removal (skip `git pull`).
  Preserve local changes
  before changing revisions. No database rollback is needed for this rollout.

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
health URL. See [initial Docker instructions](docker-instructions.md) for setup.
