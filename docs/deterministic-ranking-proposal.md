# Manual personalized recommendation preview

P1D-01 · 2026-11-21 · **G2 approved by the user**

## Purpose and boundaries

Produce a repeatable local preview from already stored IMDb ratings and TMDB
candidates, with a concrete preference threshold. This is a first ranking
baseline, not a claim that its scores predict the user's taste. It must not
call an LLM or external provider, send messages, modify database records, or
claim availability, new seasons or unseen coverage. The user's approval of a
specific real-history preview remains separate.

## Candidate and identity rules

- Read TMDB movie and TV candidates within explicit inclusive release-date
  arguments; reject a missing or invalid window, or a window longer than 31 days.
  Exclude missing dates. Movie dates are primary release dates; TV dates are
  series premieres, not season or episode events.
- Use only candidates with `link_status = 'unmatched'` and a valid stored IMDb
  title ID. Exclude `not_checked`, `conflict`, `linked`, or missing IMDb ID;
  `unmatched` without an IMDb ID does not mean unwatched.
- Recheck current imported ratings by exact IMDb ID, TMDB `(media_type, id)`
  against rated-title metadata, and catalog external IDs by correct namespace.
  If any identifies an imported rated title, exclude the candidate. Exclude any
  identity inconsistency. Do not match by name. This avoids *known* rated titles;
  it cannot prove that an unrated title has not been watched.
- Current discovery checks at most ten IMDb identities per media type per run,
  and returns at most two pages per media type. These conservative checks can
  yield few or zero candidates; missing candidates do not imply dislike.

## Rating evidence and provisional scoring

- Build separate movie and series genre profiles from distinct imported titles
  with verified V4 TMDB metadata. For each distinct genre, ratings 8–10 add
  `rating - 7` positive points; ratings 1–4 add `5 - rating` negative points;
  5–7 are neutral. Each title contributes at most once per genre. Missing genres
  contribute nothing; do not treat an unmatched or conflicted title as negative.
- A genre contributes only with at least three distinct positively rated titles.
  For that genre, affinity is `max(0, (positive - negative) / (positive + negative))`.
  Empty denominators and insufficient support produce zero. This is an aggregate
  signal, not a blanket claim about the user's tastes.
- Candidate relevance is the mean affinity over its distinct TMDB genre IDs;
  unsupported genres contribute zero and a missing genre list is ineligible.
  Require at least one supported positive genre and relevance of at least 0.35.
  Use fixed-point integer arithmetic (scale 1,000,000, floor after each division)
  and stable ordering, so results do not depend on locale or floating-point
  rounding. These weights and threshold are initial, reviewable defaults.
- Order by relevance descending, then original language `en` first, then origin
  containing `US`, `GB` or `AU` first, then known release date descending,
  media type and TMDB ID ascending. Language and country are preferences, not
  evidence of streaming region. Missing language/origin remains unknown.
- Limit output to at most 10 candidates. Return an explicit no-recommendations
  result if none qualify. Explanations must state only that the candidate shares
  supported genres with positively rated history, without inventing precise
  title-specific reasons or using raw rating values. Candidate titles are
  sensitive output: print only for an explicitly requested local preview and
  avoid application logs, test artifacts and Git.

## Intended implementation and verification

- Add `src/main/kotlin/dev/pmtd/agent/recommendation/` for a read-only JDBC
  snapshot repository and a pure deterministic scoring service; add CLI routing
  in `MovieTvAgentApplication.kt` and focused tests in matching test packages.
  Command shape: `--recommend-from=YYYY-MM-DD --recommend-to=YYYY-MM-DD
  [--recommend-limit=N]`, default 5 and range 1–10, requiring `postgres` profile.
  Ensure exclusive CLI routing, disable Flyway and SQL initialization, and use a
  read-only repeatable-read transaction for all source data. Only the intended
  candidate output is displayed; provider responses and private history are not.
- Update the README and laptop handoff. No database migration, external calls,
  scheduler, feedback or configuration/deployment change. Existing Kotlin 2.3.21,
  Spring Boot 4.1.2, Spring AI 2.0.2, Flyway and JDBC dependencies remain unchanged.
- Tester-led synthetic unit tests cover negative/neutral/positive signals,
  distinct support, missing genres, threshold edges, language/origin ordering,
  fixed-point ties, zero candidates and window/limit validation. Disposable
  PostgreSQL tests cover exact identity exclusions through all three paths,
  uncertain or conflicting IDs, repeatability, read-only behavior, movie/series
  separation, output privacy and no migrations. Run offline `test bootJar` and
  the authorized disposable PostgreSQL suite. Fresh test-schema provisioning and
  any real-data preview require separate authorization for the specified target.
- Rolling back the code removes the preview; no schema or user data is changed.
  A simpler popularity-only list is rejected because it is not personalized.
  Relaxing uncertain identity exclusion would increase coverage but can suggest
  already-rated titles; defer until identity can be established safely.

## Decisions for G2

Approve this initial scoring formula and 0.35 threshold, conservative candidate
identity checks, command and test scope as a starting point. The actual preview
may be empty due to the discovery bounds; inspect a separately authorized local
result before tuning the algorithm or proceeding to a LINE send.

## Verification

On 2026-11-21, the tester added synthetic scoring, CLI argument and PostgreSQL
identity tests. `gradlew.bat --offline --no-daemon test bootJar` passed 52
tests with no failures or skips. The first sandboxed Gradle daemon connection
failed; an authorized escalated retry passed. The user separately approved
`pmtd_agent_test.pmtd_test_rank_20261121_a1`, and the tester created the fresh
schema and ran `gradlew.bat --offline --no-daemon postgresTest`: 24 tests passed,
including the synthetic recommendation identity and repeatability check.
The suite applied V1–V4 and left its synthetic data for inspection. A packaged
preview against the synthetic fixture returned the expected recommendation with
unchanged Flyway history. An ASCII separator corrected a captured PowerShell
display issue; the tester reran all 52 offline tests and the packaged synthetic
preview successfully, with Flyway history still unchanged. The PostgreSQL suite
was not rerun after this output-only change. No real-history recommendation
preview has been run.

On 2026-11-22, the user approved G3 commit and push. Implementation `f76ae3c`
contains the CLI, recommendation repository and scorer, their tests, and the
README usage instructions. `git diff --cached --check` passed for the delivery
changes. The delivery used the recorded tester results. The proposed one-time
real-history preview on `pmtd_agent.pmtd` for 2026-11-01 through 2026-11-21
still awaits separate authorization.
