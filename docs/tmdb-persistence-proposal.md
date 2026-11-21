# TMDB candidate persistence proposal

Last reviewed: 2026-11-21 (Asia/Jerusalem)

**P1C-03 — G2 approved on 2026-11-21.** Build on the verified TMDB preview to provide stored
candidate evidence for deterministic ranking. The user requested continuation.
V3 and the bounded manual command are implemented. See [Verification](#verification)
for disposable testing and the application database migration.

## Data and identity

Retain source-specific candidates, metadata and their observation provenance.
Movie primary release and TV series first-air date are distinct event meanings.
The dates are not evidence of streaming availability or a new season. An
unknown date or origin stays unknown. Keep local IMDb ratings in their existing
catalog. No titles or ratings are sent to TMDB.

```sql
CREATE TABLE tmdb_candidate (
    media_type text NOT NULL CHECK (media_type IN ('movie', 'tv')),
    tmdb_id bigint NOT NULL CHECK (tmdb_id > 0),
    title text NOT NULL CHECK (length(trim(title)) > 0),
    primary_date date,
    original_language text CHECK (original_language ~ '^[a-z]{2}$'),
    genre_ids bigint[] NOT NULL DEFAULT '{}',
    origin_countries text[] NOT NULL DEFAULT '{}',
    imdb_id text CHECK (imdb_id ~ '^tt[0-9]+$'),
    catalog_title_id uuid REFERENCES catalog_title(id),
    link_status text NOT NULL CHECK (link_status IN ('linked', 'unmatched', 'conflict', 'not_checked')),
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (media_type, tmdb_id),
    CHECK ((link_status = 'linked') = (catalog_title_id IS NOT NULL)),
    CHECK (link_status <> 'linked' OR imdb_id IS NOT NULL)
);
CREATE INDEX tmdb_candidate_catalog_idx ON tmdb_candidate(catalog_title_id);

CREATE TABLE tmdb_discovery_run (
    id uuid PRIMARY KEY,
    requested_from date NOT NULL,
    requested_to date NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz NOT NULL,
    movie_pages integer NOT NULL CHECK (movie_pages BETWEEN 1 AND 2),
    tv_pages integer NOT NULL CHECK (tv_pages BETWEEN 1 AND 2),
    movie_truncated boolean NOT NULL,
    tv_truncated boolean NOT NULL,
    CHECK (requested_from <= requested_to AND requested_to - requested_from <= 30)
);
```

Upsert on media type and TMDB ID. Provider-supplied exact IMDb IDs are checked
against `title_external_id` in the same transaction. Link only if the IMDb title
and type match and any existing same-namespace IDs agree. The database uniqueness
constraints remain authoritative. No title-name matching or automatic merge.
Store a conflict status without changing existing catalog identities. A missing
provider IMDb ID is unmatched. Candidates beyond the external-ID lookup cap are
not_checked. Previously verified links are not silently downgraded or reassigned
when later metadata is missing or conflicting. Revalidate each run and preserve
previous evidence on conflict. An existing linked row keeps its IMDb ID, catalog
ID and linked status when a later run supplies a different or absent ID, or a
match becomes conflicting. Report that candidate as a conflict for the run and
refresh only non-identity metadata. An existing unmatched, conflict or
not_checked row can become linked only after a fresh exact, type-consistent
lookup. A candidate not checked in this run retains any prior identity state.
Record the candidate and status only; do not attach TMDB IDs to catalog titles
in this slice. Test these transitions before migration execution.

## Bounded execution and consistency

A new explicit `--tmdb-import-from=YYYY-MM-DD --tmdb-import-to=YYYY-MM-DD`
command uses the postgres profile and the existing local TMDB read-token
setting. Fetch and validate both media types and their capped external IDs before
the database transaction. Serialize imports using a PostgreSQL transaction lock.
Upsert observations and insert one run record together. Failures roll back the
whole write. Empty successful results are recorded. Set each truncated flag
when more than two source pages exist, and report counts, limits and conflicts.
Never interpret that flag as a completed scan. An invocation always starts at
page one, rather than resuming unstable TMDB page numbers. Repeated runs upsert
candidates and append run evidence. Limit discovery to two pages and identity
lookups to ten per media type, with the existing timeouts and retry budget.

Source limits mean this initial slice is not exhaustive discovery or an
incremental polling checkpoint. The later scheduled polling design must address
changing source order, overlap, completeness and safe checkpoint advancement.
Avoid claiming that unobserved entries do not exist.

## Files, validation and rollout

- Proposed V3: `src/main/resources/db/migration/V3__tmdb_candidates.sql`
  containing the SQL above. Existing migrations remain unchanged.
- Extend `TmdbDiscovery.kt` to expose bounded page metadata and reuse the
  existing matching rules from `TmdbPreview.kt`. Add a transactional repository
  and import service under `discovery/`, and wire the command in
  `MovieTvAgentApplication.kt`. Disable Flyway for the command. Update README,
  laptop handoff and fixture tests. No new library or version change.
- Run offline fixture tests and `bootJar`, including truncated pages, missing
  and conflicting IDs, repeated runs, failed requests and rollback. Use a fresh
  explicitly authorized disposable PostgreSQL schema for V1–V3 and test
  concurrency and integrity. A production V3 migration and any live write to the
  application database require separate authorization.
- Before V3 is applied, rollback removes new code and the migration. Once applied,
  use a reviewed forward migration or verified backup/restore. Do not alter an
  applied migration or remove the user's data.

Alternative: persist directly into the existing catalog and attach every TMDB
ID. A separate source table preserves observations when an identity is missing
or disputed and avoids changing IMDb ownership. Ranking can read verified links
and source candidates independently.

## Verification

- Offline `test bootJar` passed 40 tests. The user authorized V1–V3 execution
  on the disposable PostgreSQL database. `postgresTest` passed 19 tests in
  `pmtd_agent_test.pmtd_test_tmdb_6efa88b3b1d4` and 20 with the concurrency
  check in `pmtd_agent_test.pmtd_test_tmdb_50b50416418e`. Both schemas remain
  for inspection.
- The packaged live import ran twice in the second disposable schema for
  2026-11-14 through 2026-11-20. Each run observed 77 candidates, and the
  second run added no candidate rows. Movie results hit the two-page cap.
  An initial attempt used an incorrectly entered token and returned HTTP 401.
- On 2026-11-21, the user separately authorized V3 on `pmtd_agent.pmtd`.
  The JAR's migration files matched the workspace SQL, and V3 matched the
  approved design. Read-only queries confirmed the configured endpoint,
  database, role and existing V1–V2 history. The first preflight stopped
  because PostgreSQL reported an internal address distinct from the configured
  endpoint. Rechecking the connection through the configured endpoint and
  confirming the existing migrations resolved the mismatch.
- Ran the packaged app with the `postgres` profile, web startup disabled
  and `--spring.flyway.target=3`. Read-only queries confirmed V3 succeeded,
  earlier history entries were unchanged, and both empty TMDB tables had the
  expected keys and catalog index. No application catalog import was run.
  The user authorized the P1C-03 commit and push on 2026-11-21.
