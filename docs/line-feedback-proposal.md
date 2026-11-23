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

Sources: [LINE signature verification](https://developers.line.biz/en/docs/messaging-api/verify-webhook-signature/),
[LINE retry requests](https://developers.line.biz/en/docs/messaging-api/retrying-api-request/),
[LINE messaging reference](https://developers.line.biz/en/reference/messaging-api/).
