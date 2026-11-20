# IMDb import proposal

Last reviewed: 2026-11-20 (Asia/Jerusalem)

Status: G2 approved by the user. The user supplied an export and requested
continuation. Inspection found 1,085 data rows. The CSV is ignored by Git.
No personal rating values are recorded in this document.

Implemented and validated on 2026-11-20. The tester ran 23 offline and 14
PostgreSQL integration tests with no failures or skips. The packaged command
successfully imported and reimported a fabricated export against the authorized
test database. Reimport reported one skipped record and zero invalid records.
The user subsequently approved the real import. The packaged command inserted
1,085 ratings into `pmtd_agent.pmtd` with zero invalid, updated, unchanged or
skipped. A read-only PostgreSQL query confirmed the batch totals and 1,085 stored
ratings. Mockito/JVM warnings in the earlier test run were non-blocking.

## Observed columns

`Const`, `Your Rating`, `Date Rated`, `Title`, `Original Title`, `URL`,
`Title Type`, `IMDb Rating`, `Runtime (mins)`, `Year`, `Genres`,
`Num Votes`, `Release Date` and `Directors`.

## Proposed implementation

- Add a streaming UTF-8 CSV reader and import service under
  `src/main/kotlin/dev/pmtd/agent/imdb/`, plus a local command-line entry point.
  The command takes an explicit file path and requires the postgres profile.
  It returns aggregate inserted, updated, unchanged, invalid and skipped counts,
  and row numbers with non-sensitive error reasons. It never prints CSV rows,
  titles or ratings and never logs the file content.
- Map `Const` to IMDb title ID, `Your Rating` to the personal 1–10 rating,
  `Date Rated` to the optional calendar date, `Title` to the displayed title,
  `Title Type` to movie, series, other or unknown, and `Year` to an optional
  release year. `IMDb Rating` is a public aggregate and must never be used as
  the user's rating. Other fields are ignored for this first slice.
- Parse RFC-style quoted commas, escaped quotes and embedded newlines. Validate
  required headers, IDs, rating bounds, dates and row length. Reject malformed
  CSV before writing. Report invalid rows without inventing missing values.
- Identical IDs repeated within one export are marked invalid instead of choosing
  a winner. For an existing rating, update only when both dates are present and
  the incoming date is newer. Equal or absent dates with conflicting values are
  reported as conflicts and left unchanged. Equal values are counted as unchanged;
  a newer known rating date is retained to protect against later stale exports. This
  conservative rule avoids overwriting more recent ratings without a reliable
  ordering. Trusted-ID conflicts abort and roll back the transaction.
- Use the existing digest-unique import batch and JDBC repositories. A single
  transaction covers a batch and its accepted title, ID and rating writes.
  Reimporting an identical digest is a no-op. Store provenance and aggregate
  counts, never the raw export. Serialize concurrent imports for this single-user
  MVP and verify the lock in integration tests.
- Add fabricated fixtures and tests under `src/test/kotlin/dev/pmtd/agent/imdb/`
  covering CSV quoting, invalid and duplicate rows, ordering policy, rollback,
  reimport and concurrent imports. Extend the existing opt-in PostgreSQL suite
  using an authorized isolated test target. Run `.\gradlew.bat --no-daemon test`
  and `.\gradlew.bat --no-daemon postgresTest` after target authorization.

## Dependencies, migration and alternatives

No new production library or schema migration is proposed. The CSV reader must
be tested against quoted and multiline data; alternatively, an established CSV
library could be approved separately to reduce parser maintenance. Reuse the
existing PostgreSQL schema and transaction manager.

Rollback before a real import removes the added code. After a real import, the
batch and its ratings are persistent user data. Undo must be planned from a
verified backup or a separately reviewed correction; do not delete rows blindly.
Implementation and fabricated tests do not authorize importing the real export
into the laptop database. That write should be explicitly identified and
authorized after the user reviews the importer and its test results.
