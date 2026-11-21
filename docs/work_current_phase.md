# Current phase

Last reviewed: 2026-11-21 (Asia/Jerusalem)

**Phase 1C — Discovery and scheduled polling**

G0 approved by the user on 2026-11-20. The sequence is discovery design,
repeatable manual persistence, then scheduled polling. TVmaze is the first
proposed implementation slice, followed by TMDB within Phase 1C.

P1C-01 is complete. The TVmaze importer still takes a country per run. The user
prioritizes original language and show origin over release region. Choose
schedule times after a manual full run.

P1C-02 is complete. The [TMDB preview](tmdb-discovery-proposal.md#verification)
passed its approved live check against a disposable catalog.

| ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed |
| --- | --- | --- | --- | --- | --- |
| P1C-03 | Persist bounded TMDB candidate observations and verified links | Awaiting G2 | G2 | User requested continuation (G1). Review [V3 design](tmdb-persistence-proposal.md), transactional identity handling and bounded runs. Validate with offline fixtures and an authorized disposable PostgreSQL target. G2 requested for migration creation and command. | 2026-11-21 |

See the [index](README.md#completed) for completed work.
