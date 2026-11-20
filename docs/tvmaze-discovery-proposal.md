# TVmaze manual discovery proposal

Last reviewed: 2026-11-20 (Asia/Jerusalem)
Status: G2 approved on 2026-11-20. Phase 1C depends on Phase 0 closure and
foundation acceptance.

Validation: five offline discovery fixtures passed. The packaged CLI returned
three attributed episodes from the public US sample schedule on 2014-12-01.
The schedule endpoint requires no account credentials.

Use TVmaze as the first source for a bounded manual dry run. Fetch a specified
country and date with `/schedule` and normalize episode and show IDs, names,
air dates and the source URL. Require explicit country and date and cap results.
Display results locally with TVmaze attribution and source links. Treat missing
fields as unknown. Do not infer streaming availability, merge by title, persist
records or advance a checkpoint in this first slice. A schedule entry is an
episode airing, not a new show or season announcement.

Implement a small feature package under `src/main/kotlin/dev/pmtd/agent/discovery`,
an explicit CLI entry in `MovieTvAgentApplication.kt`, fixture tests under the
corresponding test package, and operator instructions in README. Use the existing
Spring HTTP client and current dependencies. Add no migration. A later persistence
design must handle trusted IDs and checkpoint transactions before writes.

The client should impose finite timeouts, bound response size and result count,
handle malformed payloads and report 429 and service errors without partial
success. Fixture tests should cover valid and missing fields, limits, 429 and
outage behavior. Run the offline Gradle test suite and a packaged CLI fixture
check. Live requests require a separately specified target and authorization.

The country schedule excludes global web channels. TVmaze describes separate web
and full schedules, one-hour general caching, 24-hour caching for full schedule
and show index, and 429 backoff. Its data requires attribution and compliance
with CC BY-SA. The show index is paginated by ID and terminates with 404. IMDb
lookup redirects for a match and returns 404 for no match. Season and episode
endpoints are separate. See the [TVmaze API documentation](https://www.tvmaze.com/api).

Alternative: TMDB first supports movies as well, but needs account access and
attribution decisions. TVmaze allows a fixture-first TV slice without credentials.
Rollback removes the new feature package, CLI wiring, tests and documentation.
No data migration or rollback is needed for this dry run. Actual release region,
license presentation and subsequent persistence remain decisions for later work.
