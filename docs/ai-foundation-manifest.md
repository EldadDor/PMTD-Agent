# Spring AI foundation execution manifest

Last reviewed: 2026-11-18 (Asia/Jerusalem)
Status: Spring AI 2.0.2 explicitly approved by the user on this date.

This bounded step verifies the approved AI library alongside the existing Boot
4.1.2/Kotlin 2.3.21/Java 25/Gradle 9.2.1 foundation. It does not select a model.

## Files and dependencies

- `build.gradle.kts`: native Gradle platform
  `org.springframework.ai:spring-ai-bom:2.0.2` and
  `org.springframework.ai:spring-ai-starter-model-ollama:2.0.2`.
- `src/main/resources/application.yml`: disable chat and embedding models;
  explicitly set Ollama model-pull strategy to `never`.
- `src/test/kotlin/dev/pmtd/agent/ApplicationHealthTest.kt`: preserve HTTP checks;
  verify startup without model beans or calls to a fake loopback Ollama endpoint.
- README and decision/status records: document deferred model integration and
  Windows/IntelliJ development.

Commands use the existing ignored workspace Gradle cache:

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat --no-daemon dependencyInsight --dependency spring-ai --configuration runtimeClasspath
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon bootJar
```

Only approved library artifacts are downloaded. Tests use isolated loopback
listeners. No Ollama service, model download, model selection, live PostgreSQL,
LINE call, scheduler or migration is part of this step. Actual inference and
grounded explanations belong to P1D-02 after the user's model decision.

Alternative: defer all AI artifacts until P1D-02. The disabled starter permits
compatibility/startup validation now. Rollback removes these dependencies,
disabled settings and additional test assertions; no persistent data changes.

Official references checked on this date:

- [Spring AI getting started](https://docs.spring.io/spring-ai/reference/getting-started.html):
  2.0.x supports Boot 4.0.x/4.1.x, Maven Central and native Gradle BOM usage.
- [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html):
  starter coordinates, `spring.ai.model.chat=none`, and
  `spring.ai.ollama.init.pull-model-strategy=never`.
- [Ollama embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html):
  `spring.ai.model.embedding=none`.

Execution evidence on 2026-11-18: dependencyInsight succeeded (15 unique Spring
AI coordinates at 2.0.2); main/test compilation and all 3 HTTP/startup tests passed
(0 failures/errors/skips), including zero fake Ollama requests/no model beans.
`bootJar` succeeded. See `implementation-status.md` for exact commands, reports,
sandbox escalation and non-blocking warnings. This proves the disabled library
foundation builds/starts; it does not verify actual inference.
