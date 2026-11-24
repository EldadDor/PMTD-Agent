# Phase 1E — LINE delivery and feedback proposal

G2 and V5 migration creation approved on 2026-11-23.
The user has confirmed receipt of the direct personalized LINE test.

## First implementation slice

Use the existing Spring WebMVC, JDBC, Flyway and PostgreSQL dependencies.
Add a `notification` package with a small LINE gateway, a manual CLI command
and a signed webhook controller. No new library or deployment change is
needed. The CLI will prepare a specific recommendation set, show the exact
message and a delivery ID, and send only that prepared delivery when explicitly
requested. It will address the locally configured recipient and verify LINE
quota before a real send. An empty set produces no push.

For each recommendation include a link and feedback actions for interested,
not interested and watched. Feedback is specific to the title. Movie
awareness and TV format preferences remain distinct; one response does not
create a blanket preference. Future ranking excludes titles explicitly
marked watched and may use individual feedback when the ranking design is
updated. Free text is deferred pending its interpretation design.

## Proposed PostgreSQL migration V5

Add a migration under `src/main/resources/db/migration/V5__line_feedback.sql`
with these tables. All IDs are UUIDs except LINE event IDs and provider IDs.
Use timestamptz for timestamps and checks for the enumerated statuses.

- `notification_delivery` stores the ID, target provider, status, retry
  UUID, immutable message payload, request and acceptance times, provider
  request ID, and sanitized error code. Status tracks prepared, sending,
  accepted, rejected and uncertain. The retry UUID is unique. Credentials
  and raw webhook bodies are excluded.
- `notification_item` binds a delivery and an opaque UUID action target to
  `tmdb_candidate(media_type, tmdb_id)`, retaining the sent title and
  ordering. Its primary key is the opaque UUID, with a unique delivery and
  candidate pair. Postback data contains that opaque ID and an action only.
- `line_webhook_event` stores a unique LINE webhook event ID, received time
  and processing outcome. A transaction records the event and applies its
  feedback together so a rollback allows redelivery.
- `feedback_event` records a unique webhook event ID, target item, action and
  event timestamp. `user_title_state` holds the current explicit state per
  media type and TMDB ID, with event ID and event time. For out-of-order
  events the latest event timestamp wins, with a stable event-ID tie-break.
  Unique and foreign-key constraints protect identity and deduplication.

Use a manually supplied approval of the specific prepared delivery rather than
an automatic scheduler. Create the retry UUID before the first LINE push and
reuse it only for identical retry requests within LINE's documented retry
window. Never automatically resend an uncertain delivery after that window.
An accepted response does not establish that a user received the message.
The earlier direct test has no durable retry key and is recorded separately.

## Inbound and verification

The opt-in webhook endpoint receives a bounded raw body, verifies the LINE
HMAC-SHA256 signature before parsing, checks the destination bot and the
allowlisted user, and accepts only postback actions referring to stored
items. The signed empty event array used for LINE verification receives 200.
Invalid signatures and unauthorized or unknown actions do not alter state.
After the local implementation passes fake tests, the user can rotate the
previously exposed channel secret and configure ngrok and LINE verification.

Add synthetic offline tests for gateway request formatting and retry keys,
invalid and valid signatures, empty verification events, unauthorized
senders and destinations, repeated and concurrent events, malformed payloads,
unknown actions, transaction rollback and feedback ordering. Run offline
`gradlew.bat --offline --no-daemon test bootJar`. Use an explicitly approved
disposable PostgreSQL schema for migration, deduplication and transaction
integration tests. Live webhook configuration, migration execution on the
application database and actual sends each require their own authorization.

## Local commands

After V5 has been separately applied, prepare a delivery with the
`postgres,local` profiles and `--line-prepare-from=YYYY-MM-DD`,
`--line-prepare-to=YYYY-MM-DD`, `--line-imdb-ratings=<local snapshot>`
and `--line-imdb-date=YYYY-MM-DD`. Review the returned delivery ID and
payload. Send that specific ID with `--line-send=<delivery UUID>` after
separate approval. The send command checks LINE quota and stores the
outcome. A retry of an uncertain delivery uses the same retry key and payload
within 23 hours. A delivery left in `sending` by a crash requires manual
investigation before further action.

The webhook is opt-in using `postgres,local,line-webhook` and listens at
`/webhook/line`. Set `PMTD_LINE_CHANNEL_SECRET`, `PMTD_LINE_BOT_ID` and
`PMTD_LINE_USER_ID` locally. Rotate the previously exposed channel secret
before the first public webhook test. The application still requires separate
authorization for live migration, tunnel configuration and sends.
Ordinary startup with the `postgres` profile enables Flyway and will apply
pending V5. Use `--spring.flyway.enabled=false` until migration execution
is explicitly approved. The manual LINE commands disable Flyway themselves.

## Alternatives and rollback

In-memory deduplication would lose state on restart. A file-only message
history would not make webhook event handling and title updates atomic.
V5 preserves durable decisions and retry keys in PostgreSQL. If code is
rolled back, leave the added tables in place. Removing stored feedback or
undoing an applied migration requires a separately reviewed data operation.

## Later iteration — higher-volume digests

After several rounds of messages and replies, review twice-daily delivery of
potentially dozens of suggestions. The user wants an easy way to respond to
individual titles while keeping message volume practical. Consider Flex
carousels with per-title postback buttons, grouped movie and TV sections, or
a compact list with a dedicated review interface. Compare usability and quota
with real usage before deciding cadence or format. LINE allows up to five
message objects in one request and counts that request once per deliverable
recipient. A Flex carousel supports up to 12 bubbles. These limits make it
possible in principle to send multiple individually actionable titles in one
push. Payload size and client usability still need validation.
[LINE counting](https://developers.line.biz/en/tips/2026/05/28/how-to-count-messages/) ·
[Flex carousel limit](https://developers.line.biz/en/news/2020/?article=login-with-2fa&day=28&month=10).

## Verification status

On 2026-11-23 the tester ran `gradlew.bat --offline --no-daemon test bootJar`.
The run passed 67 tests with no failures or skips, including fake LINE gateway
and signed webhook tests. The user subsequently approved V1–V5 and the suite in
`pmtd_agent_test.pmtd_test_line_20261123_a1`. The tester verified the target,
created the initially empty schema, and ran the suite. V1–V5 applied and
validated. All three new LINE feedback tests passed, including concurrent
deduplication, ordering and rollback. The suite reported 26 of 27 passing.
The single failure was an existing restart test that still expected four
migrations; the tester corrected it to five and compiled it. The original run
had already verified the persisted fixture after the connection pool restart,
and logs showed Flyway validated five migrations and applied none on restart.
Read-only checks confirmed V1–V5 and persisted synthetic feedback. The full
rerun needs a fresh disposable schema because setup requires an empty schema;
the approved schema remains for inspection. No application database migration,
live webhook test or real application send has occurred. Changes are uncommitted.

The user approved a clean rerun in `pmtd_agent_test.pmtd_test_line_20261123_a2`.
The tester verified the target and empty schema, then ran
`gradlew.bat --offline --no-daemon postgresTest`. All 27 tests passed with
zero failures or skips. Flyway applied V1–V5, and validated them after restart
without further migrations. Read-only checks confirmed all five successful
versions. Both disposable schemas remain for inspection.

On 2026-11-23 the user approved V5 on `pmtd_agent.pmtd`. A read-only
preflight confirmed database `pmtd_agent`, user `pmtd_app`, successful
V1–V4 history, and absence of the five V5 tables. The packaged V5 hash matched
the tested source. Flyway targeting version 5 reported one migration applied.
Read-only verification confirmed successful V1–V5 history, the five new tables
with constraints, and zero rows in each. No live webhook or application send
was performed.

## Live feedback check

LINE verified the signed webhook with HTTP 200 on 2026-11-23 after the
bot ID was corrected. This verified a request with no feedback events.

On 2026-11-24, local preflight confirmed the packaged JAR and IMDb snapshot
are present. No new live calls or application tests were run. The next check
uses the existing November 1–21 comparison window and November 21 snapshot.
After authorization, prepare one delivery locally using `--line-prepare-*`
with `postgres,local`. Preparation reads recommendations and stores the
delivery and items in `pmtd_agent.pmtd` with Flyway disabled. Review the
message and delivery ID before requesting approval for the specific push.

On 2026-11-24, the user approved preparation. The packaged JAR ran with
`postgres,local`, `--line-prepare-from=2026-11-01`,
`--line-prepare-to=2026-11-21`, the local IMDb ratings file and
`--line-imdb-date=2026-11-21`. It exited successfully and prepared one
carousel with three title cards. No additional test suite was run for this
live preparation.

For the approved push, confirm the app and tunnel are running and LINE webhook
delivery is enabled. Send the prepared ID once to the configured recipient.
After the user taps one intended feedback button, verify that the event and
current state match the selected title and action. Report the result without
logging private payloads or feedback details.

On 2026-11-24, the user approved sending the prepared delivery once. Local and
tunneled health checks returned HTTP 200 and the LINE endpoint was enabled.
The packaged `--line-send` command exited 0 with `LINE delivery result: accepted`.
The user reported tapping all three cards. A read-only PostgreSQL query scoped
to the delivery joined `notification_item`, `feedback_event` and
`user_title_state`. It found three rows, one response per card, with the
stored action matching current state on each. No application suite was rerun.

The user asked about visible selection, repeated taps and changed responses.
Flex buttons support colors defined at send time. The current handler ignores
redelivery of the same webhook event ID. Each distinct valid tap is recorded,
and the latest event by timestamp and ID determines current state. For follow-up
design, consider treating repeat selections as one preference signal while
preserving event ordering, and showing confirmation after a change is saved.
Decide separately whether watched status should persist alongside interest.

## Flex button styling

On 2026-11-24, the user approved static button colors and asked to avoid
per-tap acknowledgments. `LineDelivery.kt` uses green for Interested, red for
Not interested and blue for Watched on newly prepared cards. The labels and
postback actions retain their existing behavior. LINE excludes replies sent
through its reply API from the monthly quota and counts deliverable pushes.
[Message counting](https://developers.line.biz/en/docs/messaging-api/pricing/).

`gradlew.bat --offline --no-daemon bootJar` passed using the workspace Gradle
cache. No live send or test suite was run for this styling change.

## Independent interest and watched status

On 2026-11-24 the user approved the behavior: repeat taps count as one
preference, a changed choice replaces the current preference, and Watched
is independent. The user then approved V6 migration creation and implementation.
Execution against a database requires separate authorization for the target.

`V6__independent_title_feedback.sql` adds a read-only
`user_title_feedback` view over `feedback_event` and `notification_item`.
For each title, select the latest Interested or Not interested event and
the latest Watched event independently, ordered by event timestamp and ID.
Existing history supplies both values as soon as the view is created,
including interest choices previously superseded by Watched. A distinct
tap remains a receipt for ordering and audit. Retries with the same event
ID remain deduplicated. No separate row needs rewriting for repeated taps,
and ranking continues to exclude watched titles even after an interest change.

`LineFeedbackRepository.kt` records webhook receipts and feedback events
transactionally and stops writing the legacy `user_title_state` once V6 is
present. The V5 write path remains available during rollout.
`RecommendationRepository.kt` uses the view on V6 with the existing
fallback on older schemas. V6 preserves the legacy table and history. If rolling
back to V5-only code after accepting new taps, rebuild the legacy projection
from the latest event per title before resuming the old webhook. Review that
database operation separately if needed. No new dependency or LINE reply is
needed.

An alternative is to update a materialized state row for each dimension. It
would require backfill and concurrency logic to keep late events and repeats
ordered. Start with the view for the current volume and measure before adding
a cache. Verify migration from populated V5 and test repeated, changed,
duplicate and out-of-order taps, plus ranking exclusion after Watched. Use
an approved disposable PostgreSQL schema for those checks.

On 2026-11-24, the user authorized `gradlew.bat --offline --no-daemon postgresTest`
in the disposable schema `pmtd_agent_test.pmtd_test_feedback_v6_20261124_a1`.
The suite stages V5, writes synthetic feedback, and then applies V6.

The tester ran `gradlew.bat --offline --no-daemon test bootJar` using the
workspace Gradle cache. The build passed with 67 tests and zero failures or
skips. The PostgreSQL regression tests compiled. The application database is
outside the scope of the approved PostgreSQL test run.

The tester confirmed the approved schema was absent, created it, and ran
`gradlew.bat --offline --no-daemon postgresTest` with the workspace Gradle
cache. All 28 tests passed with zero failures, errors or skips. The suite
verified upgrade from populated V5, migration confinement and restart,
duplicate events, repeated and out-of-order taps, rollback and redelivery,
and watched exclusion after a later interest change. The disposable schema
was retained. The application database was not accessed during the test run.

Requested application rollout: verify that `pmtd_agent.pmtd` is on V5,
apply only V6, then verify the Flyway version and that the view derives
state from existing feedback. Restart the desktop webhook with the packaged
V6 code so it uses the new behavior. Confirm webhook health and re-run LINE's
empty verification request. No new notification is required for rollout.

On 2026-11-24 the user approved applying V6 and chose to restart the webhook
personally. A read-only preflight confirmed V1–V5 and three feedback events
in `pmtd_agent.pmtd`. The packaged V6 SQL matched the source hash. Running the
packaged JAR with `postgres,local`, web startup disabled and
`--spring.flyway.target=6` validated six migrations and applied only V6.
A read-only postflight confirmed successful V1–V6, all three feedback events,
and three matching view rows with zero discrepancies. The webhook connection
check awaits the user's restart. No application tests were rerun for rollout.

After the user reported the webhook running, local and tunneled health checks
returned HTTP 200, the expected LINE endpoint was enabled, and LINE's empty
webhook verification returned HTTP 200. The port 8080 listener was the process
started on 2026-11-23, before the V6 build, so the running process must be
restarted to load the new code. V6 feedback behavior is not yet live-verified.

The user stopped the old Java process and requested startup. On 2026-11-24,
the V6 JAR started on port 8080 with Flyway disabled. Local and tunnel health
returned HTTP 200, LINE reported the expected endpoint enabled, and its empty
webhook verification succeeded with HTTP 200. No new feedback event was sent.

On 2026-11-24, the user requested a message to test V6 and colored buttons.
Downloaded the public IMDb ratings snapshot dated 2026-11-24 UTC, verified
its gzip stream and header, and prepared a delivery for the November 1–21 window. The
four cards were reviewed and the user approved that specific delivery. The app
and tunnel returned HTTP 200 and LINE reported the webhook enabled. The
`--line-send` command exited 0 with result `accepted`. Feedback taps and their
database verification remain pending. No test suite was rerun for this send.

On 2026-11-25, the user confirmed receipt and reported button taps. A read-only
query scoped to the colored delivery found two new Interested events, each on
a different card, and their V6 interest states matched. The other two cards had
no events from this delivery; their view states came from earlier history.
The live sequence involving changed interest and Watched has not been observed.

The user later reported a Watched tap and a Not interested tap. A read-only
query scoped to the same delivery confirmed both. For the watched title, the
view retained its earlier Interested choice and showed `watched=true`. The
other title showed Not interested and `watched=false`. A later interest change
after Watched was covered by disposable tests, not by this live check.

On 2026-11-25, the user approved G3 delivery of the V6 follow-up and separately
approved G4 closure of Phase 1E.

Sources: [LINE signature verification](https://developers.line.biz/en/docs/messaging-api/verify-webhook-signature/),
[LINE retry requests](https://developers.line.biz/en/docs/messaging-api/retrying-api-request/),
[LINE messaging reference](https://developers.line.biz/en/reference/messaging-api/).
