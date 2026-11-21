# TMDB candidate persistence proposal

Last reviewed: 2026-11-21 (Asia/Jerusalem)

**P1C-03 — G2 requested.** Build on the verified TMDB preview to provide stored
candidate evidence for deterministic ranking. The user requested continuation.
This document proposes V3 and a bounded manual command for review.

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
