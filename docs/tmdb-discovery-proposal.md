# TMDB manual discovery preview

Last reviewed: 2026-11-21 (Asia/Jerusalem)

**P1C-02 · G2 approved on 2026-11-21.** The user registered for TMDB and set the discovery
and rollout priorities. This read-only preview adds movie and TV candidates
and checks which ones can be linked safely to the IMDb catalog. It supplies
inputs for the later personalized run.

## Discovery and identity

- Use TMDB `/3/discover/movie` and `/3/discover/tv`. Require an explicit date
  window of at most 31 calendar days. Request at most two pages per media type
  in the preview. Report when the cap leaves results unread. Use primary release
  dates for movies and first-air dates for TV. An episode airing, a series
  premiere, and a movie release are separate facts. This TV query does not detect
  new seasons of existing series. Keep TVmaze as the episode source.
- Query for original English (`with_original_language=en`) in this first
  preview. Keep its scope visible in the output. Retain original language,
  genre IDs, and origin country when TMDB supplies them. US, GB, and AU origins
  are a preference for shows and should not exclude other English-language
  shows. Use origin metadata for that preference. The first run needs no fixed
  release region. A TVmaze schedule still needs a country for the query.
- For up to ten distinct candidates per media type, get `/3/movie/{id}/external_ids`
  or `/3/tv/{id}/external_ids`. Match only an exact provider-supplied IMDb title ID
  against the local catalog. Keep movie and TV ID namespaces separate. A type
  mismatch or conflicting known mapping yields a conflict. Missing IDs remain
  unmatched. Existing IMDb titles and TVmaze records stay intact. This preview
  does not attach IDs, create catalog rows, or store polling checkpoints.

## Command and validation

- Proposed command: `java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=postgres,local --tmdb-preview-from=YYYY-MM-DD --tmdb-preview-to=YYYY-MM-DD`.
  Require a configured PostgreSQL profile for catalog reads and disable Flyway
  for this command. Print source candidates, date meaning, and match status.
  Omit personal ratings and credentials from the output. The preview is not a
  recommendation list.
- Add the client and preview in `src/main/kotlin/dev/pmtd/agent/discovery/`, wire
  the command in `MovieTvAgentApplication.kt`, reuse `CatalogRepository` for
  exact reads, and document the command and `PMTD_TMDB_READ_TOKEN` placeholder.
  Use the existing Spring Boot 4.1.2 HTTP client and JSON support. No dependency
  or migration. Rollback removes the preview and its config placeholder.
- The bearer token is supplied locally through an environment variable or
  ignored local config. Send it only in the authorization header to the fixed
  TMDB HTTPS host. Bound connect/read time and response size, fetch sequentially,
  and bound retries. Respect 429 and `Retry-After` within the run budget. Stop
  cleanly on auth failures or malformed required fields. Include the attribution
  notice and a credits entry with the approved TMDB logo.
- Verify request parameters, page caps, release and first-air dates, retries,
  missing and conflicting IDs, and movie/TV namespace separation using mocked
  HTTP and synthetic catalog data. Run offline `test bootJar`. Agree on the
  credentialed live verification target separately. No service or database
  writes are needed for the preview.

## Verification

Implemented the read-only preview, exact catalog lookups and setup docs. The
initial offline build exposed two problems in the mock setup. After fixing them,
`gradlew.bat --offline --no-daemon test bootJar` passed 39 tests, including six
TMDB checks. The packaged JAR rejected mixed commands, a reversed date window
and a missing PostgreSQL profile.

On 2026-11-21 the user authorized the live TMDB preview and delegated the catalog
choice. Ran the packaged command with `--tmdb-preview-from=2026-11-14` and
`--tmdb-preview-to=2026-11-20` using `pmtd_agent_test.pmtd_test_tvmaze_090ab3d61812`.
The preview returned 40 movies and 37 TV shows, with one media type hitting the
page cap. It checked 20 candidate identities, all unmatched in the synthetic
catalog. Mock tests separately cover positive matches and conflicts. Read-only
queries before and after confirmed identical catalog rows, external IDs and
Flyway history. The token was supplied to the process for this run only.
The implementation and documentation updates are uncommitted.

Alternative considered: persist candidates and links on the first TMDB run.
Reviewing the match evidence in the preview first reduces the risk of attaching
the wrong identity to imported ratings. Persisted discovery and further event
types still need their own design.

## Sources

- [TMDB movie discovery](https://developer.themoviedb.org/reference/discover-movie)
  and [TV discovery](https://developer.themoviedb.org/reference/discover-tv)
- [Movie external IDs](https://developer.themoviedb.org/reference/movie-external-ids)
  and [TV series external IDs](https://developer.themoviedb.org/reference/tv-series-external-ids)
- [TMDB authentication](https://developer.themoviedb.org/docs/authentication-application),
  [rate limits](https://developer.themoviedb.org/docs/rate-limiting), and
  [attribution](https://developer.themoviedb.org/docs/faq)
