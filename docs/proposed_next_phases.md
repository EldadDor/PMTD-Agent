# Later phases

G0 approved the revised sequence. The user closed 1E at G4 on 2026-11-25
and 1F is active. The product plan remains the source for full scope.

- **1F — Deferred discovery, AI and MVP acceptance:** Complete 1C scheduled
  polling, incremental completeness, event semantics and trusted TVmaze identity;
  complete grounded AI explanations and the model comparison carried from 1D;
  then address deployment, reliability and unattended operation. Choose schedule
  times after the manual full run.
- **Phase 2 — Semantic retrieval:** Deferred until the MVP works and the user
  separately approves expansion.

## First 1F task

The user requested starting 1F once the remaining 1E work was settled.
P1F-01 on the active board starts with manual discovery coverage:
compare the TMDB import's capped pages and IMDb lookups with TVmaze's
country/date checkpoints. Specify how a run records what it covered and when
it can safely resume or advance. Keep movie releases, TV premieres, and
episode airings distinct. Defer the choice of schedule until the manual run.

Use local source review and synthetic fixtures for empty, complete, capped,
and failed runs. Preserve source IDs and verify that retries do not create
duplicates or report incomplete work as complete. This review inspected
the existing importers and roadmap. No application tests were needed for it.

Sources: [product plan](../movie_tv_agent_codex_plan.md), [decisions](decisions.md),
[completed phases](archive/complete_phases.md), [discovery design](discovery-persistence-proposal.md).
