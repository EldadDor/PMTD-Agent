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

P1C-03 is complete. The user approved applying V3 to the application database
on 2026-11-21. [Flyway and schema verification](tmdb-persistence-proposal.md#verification)
passed. The user reported completing the laptop preview and import steps successfully; output counts were not supplied.

| ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed |
| --- | --- | --- | --- | --- | --- |
| P1C-05 | Prepare metadata for personalized ranking | In progress | Real-history authorization | G2 and G3 approved; V4 applied to `pmtd_agent.pmtd` after user approval. Implementation passed 45 offline and 23 tester-led disposable PostgreSQL tests. [Verification](rated-title-enrichment-proposal.md#verification). | 2026-11-21 |

See the [index](README.md#completed) for completed work.
