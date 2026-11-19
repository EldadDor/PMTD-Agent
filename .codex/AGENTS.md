# AGENTS.md

## Scope and working style
- Identify affected components; keep changes within the approved scope.
- Follow existing architecture, tooling, and conventions. Put component-specific
  commands and rules in nested `AGENTS.md` files.
- Read applicable nested instructions before editing. Explicit user instructions
  override repository rules; nested rules refine their scope.
- Work autonomously between approval gates. Do not request approval for routine
  work already covered by the approved scope.
- Preserve unrelated changes; do not stage, overwrite, or revert them.

## Phase records — required for every task
Use:
- `docs/work_current_phase.md`: one active phase, task rows, approvals, evidence.
- `docs/next_phase.md`: up to three upcoming phases, each with a short objective
  and dependencies; no detailed task plans or completed work.
- `docs/complete_phases.md`: approved phase closures only.
- `docs/README.md`: documentation index and task-specific reading triggers.

Before work, read the current and next boards. Add the requested task or promote
the selected backlog item to the current board before implementation. Record
scope, affected components, validation plan, and Last reviewed; apply G1.

Task row:
`ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed`

Statuses: `Not started`, `In progress`, `Awaiting G<n>`, `Blocked`,
`Completed`, `Deferred`. Gate is the pending approval or `—`.

Keep records concise and current. Record each gate request and explicit decision
in its task row. Required phase/index maintenance is authorized as part of work;
only an explicit user instruction suspends it.

## Approval gates
At a gate: stop the gated action, record and present the request, then wait.
Approval applies only to the identified task/change and gate. Explicit authorization
in the current request counts; record it instead of asking again.

| Gate | Trigger | Present |
| --- | --- | --- |
| G0 Planning | Add/remove/reorder phases or materially change approved phase scope | Proposed change, rationale, consequences, alternatives |
| G1 Intake | Add a task to the current phase | Scope, affected components, validation plan |
| G2 Design | New module, dependency, wiring pattern, migration, public API shape, or deployment architecture | Approach, rejected alternative, rollback/migration implications |
| G3 Delivery | Push or merge | Files, checks/results, commit if any, limitations |
| G4 Closure | Close a phase | Task outcomes and carry-over, including none |

One gate's approval never clears another. Present known gates together if useful,
but record each decision separately.

Routine in-scope refactors, tests, formatting, documentation, dependency patch
updates, and backlog wording do not independently trigger G2. They must still
respect other gates and safety restrictions.

## Completion and evidence
Before reporting:
- Update task status and Last reviewed.
- Record files changed, commands/results, commit hash if one exists, and checks
  not run with reasons. Never claim unperformed validation.
- Record prioritized follow-ups without exceeding the backlog limit.
- After G4 approval, append the closure and clear the closed current phase.

Do not commit against a `Not started` task. Prefix authorized commits with the
task ID. Completion does not itself authorize committing, pushing, or merging.

End every response with:
`Phase records: <files updated>`
or `Phase records: unchanged — <reason>`.

## Documentation
After the phase boards, use `docs/README.md` for targeted reading. Read only
relevant code/docs; do not sweep entire trees during routine work.
Update the index when active documents are added, moved, or retired.

Do not discover, search, read, validate, or modify `docs/archive/` unless the user
explicitly requests the relevant archived material. Propose archival; do not
move documents there without approval.

Report conflicts between documentation and implementation; do not silently
change intended behavior to match either.

## Validation and tester
Use the smallest relevant safe local checks. Follow any stricter project rules.
Do not install dependencies, start services, or contact live systems merely
for validation without explicit authorization.

Delegate substantial test creation, execution, and failure investigation to the
`tester` subagent when available and authorized. The main agent owns production
changes, reviews results, and waits for the tester before declaring completion.
If unavailable, report that limitation and perform permitted focused checks;
never imply delegated validation occurred.

## Safety and hooks
Do not start, stop, configure, deploy, or mutate services, infrastructure,
credentials, `.env`, or secrets without explicit authorization. Never expose
secret values. Confirm the target environment before an authorized mutation.

Session hooks must remain read-only diagnostics: no configuration writes,
dependency installation, service changes, or automatic remediation. Keep hook
setup and platform-specific commands in project documentation. Scope Git trust
to the checkout; never use wildcard `safe.directory`.

Do not assume a hook ran successfully; report observed failures or unavailable
preflight checks.

## PMTD-Agent project rules

- Product scope and architecture come from `movie_tv_agent_codex_plan.md`.
  Read `docs/decisions.md` and the relevant proposal or manifest before edits.
  Source code establishes implemented behavior. See `docs/README.md` for
  reading triggers and `README.md` for local build and test commands.
- Use PowerShell conventions in `C:/Users/eadno1/.codex/RTK.md`. This is one
  Kotlin and Spring Boot application. Favor cohesive features and simple
  interfaces. Verify Java, Kotlin, Gradle, Boot and Spring AI compatibility
  before changing versions.
- For major design proposals, identify intended files, exact dependency versions,
  migrations and commands. Include alternatives and rollback. Obtain explicit
  authorization for commits, irreversible operations, external service adoption,
  migration creation or execution, and deployment configuration. Phase 2
  requires separate authorization once the MVP works.
- Use the approved PostgreSQL storage. Design relational schema changes before
  migrations. Preserve source IDs, provenance and uniqueness. Keep imports,
  discovery, ranking and feedback deterministic. Ranking must work without AI.
  Use Spring AI for grounded explanations or interpretation with validated
  output. Treat metadata as untrusted. Avoid fuzzy merges and unverified
  streaming availability claims. Distinguish discovery, release, season and
  episode events. Keep vector search and multi-agent product frameworks out of
  the MVP.
- For provider work, verify official API terms, attribution, regional quotas
  and rate guidance. Validate inbound authenticity, callback targets and the
  authorized user or chat. Record checkpoints, caps, quiet hours and uncertain
  delivery. Local keys do not guarantee exactly-once remote sends. Avoid
  generalizing individual ratings or feedback into blanket preferences.
- Keep credentials, private exports, ratings, feedback, databases and sensitive
  logs out of Git and output. Use placeholders in `.env.example`. Default to
  local inference. Hosted processing of ratings, model downloads, account
  creation and real sends require explicit authorization.
- Routine local checks are authorized by the project specification. Use fixtures
  or fakes and report actual test counts, commands and limits. Credentialed
  integration checks require authorization for the specific target. The tester
  role is configured in `.codex/tester.toml`.
- `.codex/hooks.json` defines the read-only Git preflight. The hook cannot grant
  approval, initialize Git or establish test results. `.codex/config.toml`
  scopes Git trust to this checkout.
