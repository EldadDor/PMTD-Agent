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
was not rerun after this output-only change.

On 2026-11-22, the user approved G3 commit and push. Implementation `f76ae3c`
contains the CLI, recommendation repository and scorer, their tests, and the
README usage instructions. `git diff --cached --check` passed for the delivery
changes. After the user confirmed the destination, `f76ae3c` and `bb93b80` were
pushed to `origin/master`. `git ls-remote` verified `bb93b80` as the branch tip.

The user separately approved one read-only run on `pmtd_agent.pmtd` on
2026-11-22. The packaged JAR exited successfully with five recommendations
using `--recommend-from=2026-11-01 --recommend-to=2026-11-21 --recommend-limit=5`.
The CLI used a read-only transaction with Flyway disabled. The titles are
stored in the ignored `private/` directory for the user's review. The existing
tester results were used for delivery and the suites were not rerun for this
preview.

### User review of the first preview

On 2026-11-22 the user found all five results unsuitable. They emphasized a
limited viewing queue, normally wanting ratings of at least 6.5 and considering
vote counts, with more than 1,000 votes as a useful signal. Low ratings with few
votes are a negative signal. Subject matter and the kind of reality or documentary
content also matter. These observations do not establish a blanket rejection of
any genre, provider, or title with fewer than 1,000 votes. The current candidate
schema and ranking do not store or use candidate audience ratings or vote counts.
The source, availability, handling of missing ratings and exact ranking rules need
design before implementation.

### User review of the comparison

On 2026-11-22 the user said the improved preview was better and found the
high-scoring movie results appealing. Movies are generally something they
want to hear about as a one-time occasion, without assuming a cinema visit
or a commitment to watch. Shows compete for limited viewing time and need
more attention to fit. Distinguish documentaries, reality shows, and scripted
series, including action, drama and comedy. Judge subject and format within
those categories using the user's title-level feedback and rating history.
An individual rejection does not establish a category-wide dislike.

The current scorer builds separate movie and series genre profiles and sends
documentaries and reality TV to manual review. It does not yet model movie
awareness separately from a watch recommendation or assess subject and format
at the level described above. Keep these distinctions in the next ranking and
feedback design and in future message presentation.

### Proposed audience rating and review lookup

Use IMDb's daily `title.ratings.tsv.gz` snapshot for individual personal use.
Match the title's verified IMDb ID exactly and keep the weighted rating, vote
count, and snapshot date together. A manual import from a local copy can
validate the approach before choosing a refresh schedule. Keep the data local
and include IMDb's required attribution when displaying it. The licensed API
is a separate offering. IMDb's free file contains aggregate ratings and votes,
and its non-commercial terms prohibit scraping the website for reviews.
[Dataset](https://data.imdb.com/non-commercial-datasets/) ·
[IMDb terms](https://help.imdb.com/article/imdb/general-information/can-i-use-imdb-data-in-my-software/G5JTRESSHJBBHTGX).

Use IMDb whenever a current snapshot contains a rating. Treat 6.5 as the
proposed default minimum for the shortlist. A score below 6.5 with few votes
stays off the shortlist and can be reconsidered as votes accrue. A score at or
above 6.5 with sparse votes is provisional. Around 1,000 votes strengthens
confidence without acting as a hard gate. Require a strong subject and format
match, and allow an empty shortlist. If IMDb has no rating, a labeled TMDB
score with its own vote count can inform manual review. A TVmaze score without
a visible count can provide context. Keep scores from different audiences
distinct. Use the series score for series-level discovery.

For a promising title with sparse or missing votes, check its synopsis and
format and surface links to available reviews for manual consideration. TMDB
has a documented series reviews endpoint. Published critic reviews can supply
context when available. Keep that evidence separate from audience ratings and
leave the title pending if the evidence is too thin. Use review contents only
where permitted. A future local LLM can explain subject fit from supplied
evidence with source links. The rating and vote rules remain deterministic.
[TMDB series reviews](https://developer.themoviedb.org/reference/tv-series-reviews).

OMDb is an alternative to evaluate if a per-title lookup is needed. It offers a
free keyed tier with 1,000 requests per day. Its freshness and coverage for new
releases have not been verified here. [OMDb lookup](https://www.omdbapi.com/) ·
[OMDb keys](https://www.omdbapi.com/apikey.aspx).

Validate with fixtures for exact identity, low scores with sparse votes, strong
scores with sparse votes, credible scores with substantial votes, stale or
missing evidence, and genuinely empty shortlists. A later approved sample of
recent titles can measure coverage and delays. This review checked published
documentation. It did not download a snapshot or call a ratings API.

### P1D-02 implementation

The user approved the plan on 2026-11-22. The preview reads a dated local TSV
or gzip file and streams past rows unrelated to the eligible IMDb IDs. It
checks audience evidence before applying the output limit. The initial
defaults treat a snapshot older than two UTC days as stale and fewer than 100
votes as provisional. A fresh score below 6.5 excludes a title even when its
vote count is low. At or above 6.5, the shortlist prioritizes candidates with
at least 1,000 votes, then uses the existing deterministic rank within each
vote band. Reality TV and documentaries go to manual review.

The preview links to the synopsis for title-level screening and links manual
review candidates to their TMDB review page. An optional TMDB details request
adds a synopsis and labeled audience evidence when available. It uses the
existing bounded TMDB client and does not promote a candidate into the
shortlist. The preview requires no migration or extra library. Automated
subject assessment and review interpretation remain part of later AI work.

Tester verification on 2026-11-22 ran `gradlew.bat --offline --no-daemon
test bootJar` and passed 57 tests with no failures or skips, including the
IMDb snapshot, audience rules, CLI arguments and fake TMDB details checks.
The first attempt hit a sandbox restriction on Gradle's loopback daemon.
The elevated offline retry passed. The packaged CLI rejected an incomplete
snapshot configuration before startup. `git diff --check` passed.

Downloaded the public IMDb ratings snapshot into the Git-ignored
`private/title.ratings.tsv.gz` and verified the gzip stream and TSV header.
IMDb reported November 21, 2026 as its UTC last-modified date. No live TMDB
or PostgreSQL test ran during offline verification.

On 2026-11-22 the user approved G3 commit and push to
`EldadDor/PMTD-Agent` on `master` and a read-only comparison preview.
One packaged run on `pmtd_agent.pmtd` for November 1–21 used the November 21
IMDb snapshot and a limit of five per list. It returned three shortlisted
titles and five for manual review. A comparison by displayed title found
zero of the original five on the shortlist and one in manual review. The
report is Git-ignored. The preview used no live TMDB context request.
