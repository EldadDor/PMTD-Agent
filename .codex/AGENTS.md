# Personal Movie & TV Discovery Agent

## Scope and records

Read root `AGENTS.md`, `movie_tv_agent_codex_plan.md`,
`docs/implementation-status.md`, `docs/decisions.md` and the relevant proposal.
Follow `C:/Users/eadno1/.codex/RTK.md` for PowerShell conventions.
The plan is approved product scope, not blanket implementation permission.
Source code establishes implemented behavior. Report conflicts.
The copied password-manager stack, vault design, roadmap and deployment rules do
not apply. Keep a running task list and short decisions log, clearly separating
approved choices, proposals, open decisions and completed work.

## Incremental work and approval requirements

Inspect before implementation. Propose a small milestone before scaffolding an
empty workspace. Show intended files, exact dependencies/versions, migrations and
commands before major architectural changes; explain alternatives and rollback.

Obtain explicit approval before:
- Adding a major dependency, adopting an external service or changing architecture.
- Database migrations or deployment configuration, including Docker/Compose files.
- Irreversible operations, commits, pushes or merges (each separately).
- Optional Phase 2 expansion, after the MVP is working.

Mark dependent tasks Awaiting approval and wait; continue independent authorized
inspection/documentation. Preserve existing authorization without asking again.
Approved scope does not grant execution approval. Close phases only with verified
acceptance evidence and approval. Record actual files/checks/results/limitations
and authorized commit IDs when applicable; update Last reviewed.

The user explicitly approved replacing inherited G0-G4/three-file phase mechanics
with these plan-specific approval requirements on 2026-11-16. Use
`docs/implementation-status.md` and `docs/decisions.md`; PPP is not required here.

## Development

Use idiomatic Kotlin, straightforward Spring Boot services and explicit interfaces
where useful. Verify Boot/Spring AI/Kotlin/Java/Gradle versions before adding them.
Prefer feature cohesion; avoid unnecessary frameworks, services, bot SDKs and UI.
No multi-agent product framework, vectors or Phase 2 implementation in MVP.

Design the relational schema before migrations. SQLite/PostgreSQL remains open
until approved; Firestore is not required. Preserve source IDs, provenance and
unique constraints. Keep import, polling, ranking, buttons and persistence
deterministic. Ranking must work without AI. Use Spring AI for grounded language
interpretation/explanation with validated output; treat metadata as untrusted data.
Avoid unsafe fuzzy merges; distinguish discovered/released/season/episode events
and do not claim streaming availability without a verified region-specific source.
Record checkpoints, caps, quiet hours and delivery uncertainty. Local keys alone
do not guarantee exactly-once remote sending. Do not generalize one rejection
into a blanket genre preference.

## Privacy and operations

Never commit or print credentials, bot tokens, private IMDb exports, personal
ratings, private feedback, local databases or sensitive logs. Add appropriate
ignores before application work; use `.env.example` placeholders. Default to local
inference; hosted processing of personal ratings requires explicit approval.
Validate inbound provider authenticity, callback targets and authorized user/chat.

Verify official API terms, attribution, regional quota and rate guidance before
implementation. Evaluate LINE first; provider/database/model selection remains
open. Do not create accounts, download models, send real messages, start or
configure live services, deploy, access live databases or change secrets without
explicit authorization. Do not install unapproved dependencies for validation.

## Tests and delegation

The supplied specification authorizes relevant automated tests. Run focused tests
and report actual commands, pass/fail counts, skipped checks and limitations.
Use fixtures/fakes or approved isolated resources; live credentialed integrations
and real sends need separate authorization. Hooks/documentation are not test proof.

Delegate substantial authorized testing to the tester described by
`.codex/tester.toml`. Main agent owns architecture/implementation, reviews results
and corrects production code. Tester runs/investigates/reports without changing
production code. Wait for results before declaring that testing complete. Do not
launch agents for unrelated work without explicit authorization.

## Read-only preflight

`.codex/hooks.json` invokes a read-only Git diagnostic. It must not initialize Git,
change trust settings, grant approval or claim tests ran. `.codex/config.toml`
scopes safe.directory to this workspace only; never use `*`. No Git repository
initially exists; preflight may exit quietly until initialization is authorized.
Preserve unrelated work. Do not commit/push/merge without explicit permission.
