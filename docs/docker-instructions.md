# Docker on the laptop

Run these commands manually in PowerShell on the laptop with Docker Desktop.
Select Linux containers in Docker Desktop and use a Java 25 JDK for the Gradle
build. The first build and image pull need internet access. One container is
enough for this smoke check, so Docker Compose is optional.

Clone the repository using its remote URL. If you already have a laptop clone,
open that checkout instead. The clone only contains commits available on the
remote.

```powershell
git clone '<repository-url>' PMTD-Agent
Set-Location PMTD-Agent
docker version
docker info --format '{{.OSType}}'  # Expected: linux
java -version                     # Expected: Java 25
```

Build the executable JAR using the checked-in Gradle wrapper:

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat --no-daemon bootJar
if ($LASTEXITCODE -ne 0) { throw 'bootJar failed' }
$jar = (Resolve-Path 'build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar').Path
```

Run the JAR in the [Java 25 Temurin runtime image](https://hub.docker.com/_/eclipse-temurin)
with the file mounted read-only. The app listens on the container interface,
and Docker publishes the port only on laptop loopback. This smoke check uses
the default app configuration, which runs without PostgreSQL or Ollama.

```powershell
docker pull eclipse-temurin:25-jre
docker run --detach --rm --name pmtd-agent-smoke `
  --publish 127.0.0.1:18080:8080 `
  --mount "type=bind,source=$jar,target=/app/pmtd-agent.jar,readonly" `
  eclipse-temurin:25-jre java -jar /app/pmtd-agent.jar `
  --server.address=0.0.0.0 --server.port=8080
```

After startup, check for `UP`. If the request fails, check the container logs.
If host port 18080 is already in use, pick another host port and change the
health URL to match. Stop the container when finished. `--rm` removes it on exit.

```powershell
docker logs pmtd-agent-smoke
(Invoke-RestMethod 'http://127.0.0.1:18080/actuator/health').status
docker stop pmtd-agent-smoke
```

This run mounts only the built JAR. Keep local configuration, private IMDb
exports and credentials outside the container. A persistent Docker setup and
container access to the laptop PostgreSQL or Ollama services belong to the
later deployment task.
