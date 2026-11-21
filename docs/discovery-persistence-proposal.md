# Phase 1C discovery persistence proposal

Last reviewed: 2026-11-21 (Asia/Jerusalem)

**P1C-01 — G2 approved by the user.** This proposes the first manual TVmaze persistence
slice. G0 approved the Phase 1C sequence and G1 authorized design. G2 authorizes this schema, migration creation, and command interface. Production
database migration execution was separately approved and performed on `pmtd_agent.pmtd` on 2026-11-21.

## Scope and identity

A manual command accepts an explicit ISO country and date. It requests the
complete TVmaze country schedule, validates the entire response and stores
source observations. The country is a TVmaze query parameter. The user now
prioritizes original language and show origin over a fixed release region. This endpoint includes country-tied airings and omits global web
channels. An episode airing is not proof of streaming availability.

Store source episode and show IDs as supplied. For this slice, source records
stay separate from `catalog_title` and imported ratings. A show ID alone does
not prove which imported IMDb title it belongs to. Trusted cross-source mapping,
including conflicts and provenance, needs a later design. No title-name match.

## Proposed V2 schema

Create `src/main/resources/db/migration/V2__tvmaze_schedule.sql` containing:

```sql
CREATE TABLE tvmaze_schedule_episode (
    country char(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    episode_id bigint NOT NULL CHECK (episode_id > 0),
    show_id bigint NOT NULL CHECK (show_id > 0),
    show_name text,
    episode_name text,
    air_date date,
    source_url text,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (country, episode_id)
);
CREATE TABLE tvmaze_schedule_checkpoint (
    country char(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    schedule_date date NOT NULL,
    last_success_at timestamptz NOT NULL,
    PRIMARY KEY (country, schedule_date)
);
```

A source episode may occur in multiple countries. Repeated observations upsert
on country and episode ID, retain first-seen time and refresh last-seen and
source fields. A changed air date updates that observation. The checkpoint is
keyed by the requested country and date, records the last complete successful
observation, and is not a claim that the source will never change. Polling a
previous date again remains possible. This first slice does not delete records
missing from a later response. Preserve original data on parse errors.

Fetch and validate outside the transaction. Serialize writes for the same
country, then upsert the full response and its checkpoint in one
transaction. On failure, roll back both. Never use the existing display limit
as a persistence limit: if the entire response cannot be safely processed,
fail without advancing the checkpoint. Empty successful schedules may advance
it. Implement finite timeouts and bounded 429 backoff. Explicitly fail on
invalid required IDs or malformed nonempty dates. Null dates stay unknown.
No scheduler or TMDB API is introduced in this slice.

## Intended implementation and verification

- Extend `src/main/kotlin/dev/pmtd/agent/discovery/TvmazeDiscovery.kt` with a
  full-response path while preserving the current limited dry run. Add a
  PostgreSQL repository/service in that package and update
  `MovieTvAgentApplication.kt` with a mutually exclusive manual command
  `--tvmaze-import-country=XX --tvmaze-import-date=YYYY-MM-DD`. Require the
  postgres profile. Print aggregate counts and TVmaze attribution.
- Add the V2 migration above, update README and laptop handoff, and add focused
  synthetic tests under the discovery and PostgreSQL test packages. No new
  dependency is proposed; existing Boot 4.1.2, Flyway 12.4.0 and pgJDBC 42.7.14
  remain in use.
- Offline fixtures cover full response beyond the display cap, empty response,
  malformed dates/IDs, retry and 429 behavior. Disposable PostgreSQL checks
  cover initial and repeated imports, multiple countries, changed dates,
  rollback/checkpoint atomicity, and concurrency. Check packaged command and
  existing dry run. A fresh disposable schema and a production migration target
  require separate, explicit execution authorization. V2 execution on the
  production `pmtd_agent.pmtd` target was subsequently approved and verified
  on 2026-11-21.

Alternative: write directly to `catalog_title` and merge by a shared identifier.
That would require reliable external identity evidence and conflict handling not
provided by the current schedule result. Separate source observations allow a
useful first slice without guessing. No inference that a title is new, a new
season, or streamable should be made from an airing record alone.

Before migration, rollback removes the new code and V2. After migration or
real writes, use a reviewed forward correction or verified backup/restore;
never edit an applied migration or drop user data. Subsequent TMDB, canonical
matching, scheduling, release/season facts and notification behavior require
separate designs or approvals as applicable.

## Provider review

- [TVmaze API](https://www.tvmaze.com/api): country schedule coverage and global
  web schedule differ. API responses are cached; 429 calls for backoff. Data
  is CC BY-SA and requires credit and compliance with share-alike terms.
- [TMDB FAQ](https://developer.themoviedb.org/docs/faq): noncommercial access
  requires attribution. [Authentication](https://developer.themoviedb.org/docs/authentication-application)
  uses an API key or bearer token. [Rate guidance](https://developer.themoviedb.org/docs/rate-limiting)
  asks clients to respect 429 responses. The user registered for TMDB. The
  [preview design](tmdb-discovery-proposal.md) tracks integration and verification.
