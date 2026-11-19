# Decisions

Last reviewed: 2026-11-19 (Asia/Jerusalem)

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

Later on 2026-11-16 the user separately authorized the initial commit of the
current changes. Git records it as `1818702`, `Initialize movie and TV agent
foundation`. This one-time authorization does not authorize further commits,
pushes or merges, or constitute milestone acceptance or phase closure. The
2026-11-17 phase sync found no subsequent commits or additional decision evidence.

## Decision register

| ID | Choice | Recommendation / alternative | Still needed |
| --- | --- | --- | --- |
| D01 | Versions | Approved: Boot 4.1.2, Kotlin 2.3.21, Gradle 9.2.1, Java 25, and now Spring AI 2.0.2 | AI foundation validation recorded in implementation status; actual inference deferred |
| D02 | Database | Laptop PostgreSQL 16.14 at `10.100.102.12:5432`. User approved provisioning and use on 2026-11-19. `pmtd_agent` holds app data, `pmtd_agent_test` holds integration fixtures, and both use the dedicated `pmtd_app` login. | The server lacks TLS, so the desktop connection uses `sslmode=disable`. V1 applied in `pmtd_agent.pmtd`. See implementation status for test evidence. |
| D03 | Messaging | User confirmed PMTD-Agent Messaging API activation and chose ngrok for desktop webhook testing on 2026-11-19. Channel settings are in ignored local YAML. | Signed webhook handler and ngrok tunnel pending. The user will set the HTTPS URL in the LINE console. Outbound token and quota remain pending. |
| D04 | Ollama | Model selection deferred by user; **do not download any models**. AI chat/embeddings disabled and pull strategy `never` | Hardware and inference placement/evaluation later; no model tag selected |
| D05 | Schedule | Deferred by user; manual-only POC path | Frequency/timezone/caps/quiet hours/resend policy later; no scheduler added |
| D06 | Feedback | Deferred by user, including sequence of buttons/ratings/free text | Retained in MVP scope; implementation waits for later decision |
| D07 | Hosting | Approved current target: this Windows desktop, Kotlin app run from IntelliJ; Codex may run Gradle tests. Laptop supplies PostgreSQL | Other laptop services after POC. Existing Kind + ArgoCD and Hostinger VPS are context only; VPS deployment is not currently considered. No container/K8s/ArgoCD/VPS configuration authorized |

The approved selections above supersede earlier recommendations. The dedicated
PMTD role and databases were provisioned with the supplied PostgreSQL login.
Cleartext local settings are approved in Git-ignored `application-local.yml`.
Future schema changes and real LINE messages require their own approval.

## User direction — 2026-11-18

The user approved Spring AI **2.0.2**, chose PostgreSQL on their other computer
(`10.100.102.12`) with credentials to follow, reported a LINE developer/Official
Account named PMTD-Agent, and requested continuation of the plan. Account links:
[manager](https://manager.line.biz/account/@966sowkr),
[public page](https://page.line.biz/account/@966sowkr).
This is intent to use LINE; account/API readiness was not demonstrated by those
links. See `line-api-setup.md` for verified official setup requirements.

The user explicitly prohibited Ollama model downloads, deferred model selection,
scheduling and feedback, and chose desktop Windows/IntelliJ execution first with
Gradle testing by Codex. Laptop Kind + ArgoCD and Hostinger VPS exist, but no VPS
deployment is currently considered. Additional laptop services will be considered
after the POC is complete. Use fixture-based/disabled integrations meanwhile.

Proceed with the approved AI dependency at 2.0.2 and local validation, as described
in `ai-foundation-manifest.md`; no repeated approval is needed for that choice.
The user subsequently replied **Approved** to the explicit request to add the
JDBC/Flyway dependencies and create the proposed V1 migration in
`persistence-manifest.md`. P1A-02 schema/dependency review is approved; implement
the bounded P1A-03 files and tests without asking again. This includes Boot JDBC
and Flyway starters 4.1.2, Flyway PostgreSQL support 12.4.0, pgJDBC 42.7.14 and
their transitive libraries. Laptop migration execution still waits for connection
details and separate execution authorization, as stated in the approval request.
No disposable integration database has been authorized or supplied. No new commit,
push, merge, model download or deployment authorized.

## Reservations and operating-rule changes

- Exact version strings are now fixed. Resolved Spring AI 2.0.2 artifacts,
  compilation, disabled-model startup/tests and packaging passed with Boot 4.1.2
  on 2026-11-18. Actual model inference remains unverified/deferred.
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
