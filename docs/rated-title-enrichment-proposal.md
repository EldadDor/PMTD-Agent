# Rated-title metadata for personalized ranking

Last reviewed: 2026-11-21 (Asia/Jerusalem)

**P1C-05 — G2 approved on 2026-11-21.** The user reported completing the laptop preview and
import steps. To relate discovery candidates to personal history, the app needs
genre metadata for rated titles. IMDb ratings are local; V1 does not store genres.

## Proposed bounded enrichment

A manual command `--tmdb-enrich-limit=N` reads at most N distinct movie or series
IMDb IDs from local ratings, in IMDb ID order. The limit is required and must be
1–20. It excludes `other` and `unknown` types and reports totals only. For each
eligible ID without a recorded successful, unmatched or conflict attempt, use TMDB `/3/find/{imdb_id}` with
`external_source=imdb_id`. Accept exactly one movie or TV result matching the
existing catalog type. Fetch the corresponding TMDB details for genre IDs,
original language and origin countries. Missing, multiple, type-conflicting,
or previously assigned identities are reported and remain unlinked. The app
never matches by title or sends personal rating values to TMDB.

**Privacy:** Each lookup sends an IMDb title ID to TMDB. Those requests can
reveal titles from the user's rating history to TMDB even though rating values
are not sent. G2 covers design and implementation only. A real-history lookup
and its exact target require separate explicit user authorization after review.
Tests use synthetic IDs and responses. Do not print IDs, titles, ratings, tokens,
URLs containing IDs or raw provider errors. Respect existing TMDB timeouts,
request cap, retry and attribution requirements.

## Proposed V4 schema

```sql
CREATE TABLE rated_title_tmdb_metadata (
    catalog_title_id uuid PRIMARY KEY REFERENCES catalog_title(id),
    media_type text NOT NULL CHECK (media_type IN ('movie', 'tv')),
    tmdb_id bigint NOT NULL CHECK (tmdb_id > 0),
    genre_ids bigint[] NOT NULL DEFAULT '{}',
    original_language text CHECK (original_language ~ '^[a-z]{2}$'),
    origin_countries text[] NOT NULL DEFAULT '{}',
    observed_at timestamptz NOT NULL,
    UNIQUE (media_type, tmdb_id)
);
CREATE TABLE rated_title_enrichment_attempt (
    catalog_title_id uuid PRIMARY KEY REFERENCES catalog_title(id),
    outcome text NOT NULL CHECK (outcome IN ('matched', 'unmatched', 'conflict')),
    attempted_at timestamptz NOT NULL
);
```

Store only validated source metadata for unambiguous matches. Keep the existing
catalog and ratings unchanged. A transaction locks each catalog title, rechecks
its IMDb external ID and type and checks that the TMDB ID is not attached to a
different title or namespace mapping. Store the observation only when both
identity checks and V4 uniqueness agree. A conflict or API failure does not
advance a checkpoint or imply dislike. Store the attempt outcome with any metadata in the same transaction. Each
run starts from the first eligible title without a recorded attempt, so
unmatched titles do not prevent progress. A separate `--tmdb-enrich-retry`
flag rechecks unmatched and conflict attempts in the same deterministic order;
matched entries remain skipped. A failed HTTP request creates no attempt row,
so a subsequent run can retry it. Every invocation retains the explicit cap. Record timestamps
and source attribution, but do not store raw TMDB responses or personal ratings.

## Scope and validation

- Add `V4__rated_title_tmdb_metadata.sql` containing the schema above, an
  enrichment client/service under `discovery/`, and CLI routing in
  `MovieTvAgentApplication.kt`. Reuse the existing TMDB bearer authentication,
  response bounds, timeout/backoff and catalog lookup. Expose a total request
  budget of at most 40 API calls per invocation. Disable automatic Flyway for
  this command. No new dependency or version change.
- Offline tests cover exact movie/TV matching, zero/multiple results, episode
  results, type/ID conflicts, missing metadata, 429/failure, limits, and no
  sensitive output. Disposable PostgreSQL tests cover repeatability, rollback,
  concurrent imports, conflicts and uniqueness. Compile and run `test bootJar`
  offline. Execution of V4 on a fresh disposable schema needs separate approval.
- After migration or stored user metadata, rollback requires a reviewed forward
  migration or verified backup/restore. Never edit an applied migration. Applying
  V4 to `pmtd_agent.pmtd` and running with real history require separate approval.

An alternative is to rank using only genres for discovered candidates. It would
not establish genre preferences from rated history because the current IMDb import
does not retain genres. This enrichment supplies that missing evidence. Ranking,
threshold selection, LINE sends, scheduled runs and feedback remain later work.

## Verification

The user approved G3 commit and push on 2026-11-21. Application-database
migration execution and real-history requests require separate approval.

On 2026-11-21, the approved implementation passed
`gradlew.bat --offline --no-daemon test bootJar`: 45 tests passed. The changes
cover the TMDB client, enrichment service, CLI, V4 migration, offline and
PostgreSQL tests, application README, laptop handoff and phase records. No
tester subagent was available for the initial offline checks; the main agent
ran them. After the user requested tester-led integration checks, the tester
created the fresh schema `pmtd_test_enrich_5d01d3c78728` in `pmtd_agent_test`.
`gradlew.bat --offline --no-daemon postgresTest` applied V1–V4 and passed
23 tests with synthetic data; the schema remains for inspection. An initial
sandboxed connection was denied, and the authorized escalated run succeeded.
The V4 migration matches the SQL in this proposal and `git diff --check`
passed. V4 has not been applied to the application database, and no real
rated-title history was sent to TMDB as part of this implementation.

Sources: [TMDB find by external ID](https://developer.themoviedb.org/reference/find-by-id),
[TMDB movie details](https://developer.themoviedb.org/reference/movie-details),
[TMDB TV details](https://developer.themoviedb.org/reference/tv-series-details),
[TMDB rate limits](https://developer.themoviedb.org/docs/rate-limiting),
[TMDB attribution](https://developer.themoviedb.org/docs/faq).
