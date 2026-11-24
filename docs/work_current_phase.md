# Current phase

Last reviewed: 2026-11-25 (Asia/Jerusalem)

**Phase 1F — Deferred discovery, AI and MVP acceptance**

Phase 1E closed with G4 approval after delivery of the V6 follow-up.
[Closure evidence](archive/complete_phases.md#phase-1e--notifications-and-feedback).

| ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed |
| --- | --- | --- | --- | --- | --- |
| P1F-01 | Review manual discovery coverage and safe resume rules | In progress | — | User requested starting 1F. G1 intake covers the existing TMDB and TVmaze clients and importers, the manual commands, and coverage evidence. Source review started. Validate the eventual implementation with synthetic fixtures for empty, complete, capped and failed runs and replay without duplicates. [Scope](proposed_next_phases.md#first-1f-task). | 2026-11-25 |

TMDB fetches at most two pages and checks IMDb identity on at most ten
candidates per media type. It records page counts and truncation per run.
A capped run is partial. A completed page scan can still leave unchecked
identities. TVmaze checkpoints a successful country/date response in the same
transaction as its episode upserts. That checkpoint describes the country
schedule response. Global web coverage and removal of previously observed
episodes require separate rules.

The next design decision is the unit of coverage and replay. For TMDB,
define how to retry a capped date window without relying on page offsets
that may shift between requests. For TVmaze, distinguish successful empty
dates from failed attempts, and keep episode airings distinct from new
seasons and series premieres. Choose schedule times after the manual full run.

The initial review used local source and phase records. No live discovery
calls or new tests ran for this review.
