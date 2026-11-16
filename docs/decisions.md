# Decisions

Last reviewed: 2026-11-16 (Asia/Jerusalem)

## Approved scope

Kotlin; Spring Boot 4; Spring AI; self-hosting; local inference; IMDb import;
TMDB + TVmaze; deterministic polling/ranking; interactive feedback in V1;
3B/~5B/8B bake-off. Phase 2 semantic retrieval and other expansion are deferred.
Project-specific instruction/configuration adaptation is explicitly requested now.

On 2026-11-16 the user additionally explicitly approved replacing copied approval
rules with the plan's requirements, correcting PMTD-Agent paths and adapting the
tester while preserving the read-only hook. Those changes are implemented; the
application architecture and dependency choices below were proposals at that time.

On 2026-11-16 the user approved the reviewed plan and requested project start.
They then explicitly approved `foundation-manifest.md`: Boot 4.1.2, Kotlin 2.3.21,
Gradle 9.2.1, existing Java 25, web/Actuator/test support, dependency downloads and
isolated loopback health tests. This authorizes P1A-01. No commit, push, migration,
deployment, database service, model download or real send is authorized.
Use package `dev.pmtd.agent`, native Gradle BOM support and an ignored workspace
Gradle cache. Global Gradle 8.10 is incompatible with running on Java 25 and cannot
write its native DLL lock in this sandbox; preserve the global installation.

## Decision register

| ID | Choice | Recommendation / alternative | Still needed |
| --- | --- | --- | --- |
| D01 | Versions | Foundation approved and build/tests verified: Boot 4.1.2, Kotlin 2.3.21, Gradle 9.2.1, Java 25; see exact manifest | Spring AI 2.0.2 remains a later dependency proposal; AI compatibility not exercised |
| D02 | Database | Prefer PostgreSQL + JDBC + versioned SQL migrations for transactional workers. SQLite is a reasonable single-process alternative if avoiding a server matters most. No vector extension. | Select database, then approve driver/migration dependencies and schema |
| D03 | Messaging | LINE evaluated first; recommend Telegram long polling for simpler local operation. LINE remains viable if preferred and quota/webhook work is acceptable. | LINE billing region/plan and preferred app; provider approval |
| D04 | Ollama | Host app and separately managed loopback Ollama initially; model optional for ranking | CPU, RAM, GPU/VRAM, disk, OS; approve downloads and model after bake-off |
| D05 | Schedule | Start manual only; later suggest 3 polls/day, one digest/day of up to 3 items, quiet hours 22:00–08:00 Asia/Jerusalem | Approve times, timezone, caps and resend policy |
| D06 | Feedback | Four buttons first, rating next, optional free text after button persistence is stable (within V1) | Confirm sequence |
| D07 | Hosting | Local development first; Linux/container target later | Approve deployment target/configuration separately |

These are engineering recommendations, not locked choices. Database selection does
not authorize migrations; provider selection does not authorize external messages.

## Reservations and operating-rule changes

- Version strings are ambiguous. Current official docs show Boot 4.1.2 and Spring
  AI 2.0.2. A real resolved build is still required before claiming compatibility.
- LINE quota cannot be inferred from timezone; actual account billing region matters.
- RAM requirements depend on quantization, context, concurrency and offloading;
  parameter count alone cannot establish a safe minimum.
- A unique local delivery key cannot guarantee exactly-once remote delivery if a
  process crashes after sending but before persisting the receipt. Track uncertain
  sends separately; agree resend policy before automatic delivery.
- Replace inherited G0–G4 and three-file roadmap mechanics with the specification's
  explicit approval triggers and a single running task list. Approved scope is not
  execution approval. Remove the inherited ban on tests and Firestore-only rule.
  Relevant tests are authorized by the supplied specification; database work stays gated.
