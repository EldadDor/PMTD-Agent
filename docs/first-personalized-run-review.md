# First personalized run — planning review

Reviewed: 2026-11-21 (Asia/Jerusalem). **G0 and 1C G4 approved by the user.**

## Evidence and gaps

- [Rated-title enrichment](rated-title-enrichment-proposal.md#verification)
  processed the eligible history: 882 matched, one unmatched and two conflicts.
  Unmatched and conflicted titles are not negative preferences.
- [TMDB discovery](tmdb-persistence-proposal.md#bounded-execution-and-consistency)
  is bounded to two pages and ten IMDb identity checks per media type. Some
  candidates are `not_checked`; a missing verified link is not proof that a title
  is unwatched. The user's laptop import was reported successful, but counts and
  discovery completeness were not supplied. The 77-candidate evidence used a
  disposable catalog, not the application catalog.
- [TVmaze observations](discovery-persistence-proposal.md#scope-and-identity)
  are separate from the catalog and do not establish a new season or streaming
  availability. Trusted cross-source identity and complete polling are pending.
- Source inspection shows no recommendation engine, notification gateway,
  scheduler or feedback handler. IMDb ratings are available as watched evidence;
  general watched/feedback state has not been implemented.
- LINE Messaging API is activated, but outbound access token, recipient and
  quota verification are pending. The webhook remains deferred until receipt of
  the first personalized push, as the user requested.

## Sequence approved at G0

1. **Essential, P1:** With separate G4 approval, close 1C with explicit carry-over:
   scheduled polling, incremental completeness, release/season/episode event
   semantics and trusted TVmaze identity. Decide scheduling only after the manual
   full run. Carry the unfinished discovery work into 1F.
2. **Essential, P1:** Make 1D's first deliverable a manual, deterministic
   recommendation preview using existing ratings and verified metadata. Prefer
   original English and US/UK/Australian origin; use rating evidence, avoid known
   watched items and apply a relevance threshold. Return no recommendation when
   evidence is insufficient. Resolve potentially watched candidates only with
   trusted identifiers; do not assume `not_checked` means unwatched. Describe
   discovery date/source accurately and do not assert availability. Test with
   synthetic cases, then review a separately authorized personal preview.
3. **Essential, P1:** After the preview is satisfactory and 1D is closed with
   carry-over, implement one manually initiated personalized LINE push in 1E.
   Confirm recipient, credentials and quota; use fake tests first and obtain
   explicit approval before a real send. Empty recommendations produce no push.
4. **Essential for MVP, later:** Carry local-AI explanation/model evaluation and
   deferred 1C work into 1F. Preserve the approved model comparison and grounded
   explanation requirements. After the first push is received, implement signed
   webhook/feedback in 1E; then finish 1F reliability and MVP acceptance. The
   manual first run uses a deterministic, evidence-based reason until AI is ready.

**Alternative:** Finish scheduled polling and the full model bake-off before a
first personalized push. That preserves the original phase order but delays the
requested manual run and requires deciding operating details before firsthand
results. Another option is a manual preview without LINE; it is useful for
testing relevance but does not complete the requested message-sending milestone.

**Optional:** Review the two identity conflicts after the first run if they are
material; do not infer a match or dislike. Genre statistics can be added later.
**Speculative:** Semantic retrieval remains Phase 2 and requires separate approval.

## Gates and unresolved decisions

- G0 approval covers the sequence and carry-over above. It does not close 1C
  or authorize implementation, migrations, deployments or sends.
- G4 for 1C is approved. G1 task intake and applicable G2 design, G3 delivery
  and later G4 decisions remain separate.
- The ranking design must decide candidate identity safety, relevance threshold,
  missing/limited metadata and explainability before implementation. Limited
  discovery may produce an empty result or omit relevant titles.
- Actual LINE access token, recipient, allowance and any explicit live-send
  authorization remain to be confirmed. User feedback and schedule times follow
  the first successfully received personalized message and manual full run.

Sources: [product plan](../movie_tv_agent_codex_plan.md),
[decisions](decisions.md), [current phase](work_current_phase.md),
[next phase](next_phase.md), [roadmap](proposed_next_phases.md),
[completed phases](archive/complete_phases.md), [LINE setup](line-api-setup.md),
[laptop handoff](laptop-deployment-handoff.md), and the V1–V4 migrations and
discovery sources under `src/main/`.

## Phase 1C closure review

**G4 approved on 2026-11-21.** The user separately approved closure after
approving the sequence and carry-over at G0.

- P1C-01: TVmaze manual persistence and V2 verified; disposable test import was
  repeatable. No application TVmaze import was reported.
- P1C-02/03: TMDB preview and persistence verified; V3 applied. Live repeat
  import evidence used a disposable database; the user reported completing laptop
  test steps without supplying counts.
- P1C-04: PostgreSQL settings prepared in ignored local `.env`.
- P1C-05: V4 applied; 45 offline and 23 disposable PostgreSQL tests passed.
  The approved application-history enrichment processed 885 eligible titles:
  882 matches, one unmatched and two conflicts, with none remaining unattempted.
- Carry into 1F: scheduled polling and schedule decisions, incremental discovery
  completeness, release/season/episode event semantics and trusted TVmaze
  cross-source identity. The manual importer is bounded and is not complete
  scheduled coverage. Select schedule times after the manual full run.
- 1D becomes active only after G4; its first task is deterministic personalized
  preview design. AI explanation and model evaluation are retained in 1F.
