# Current phase

Last reviewed: 2026-11-23 (Asia/Jerusalem)

**Phase 1E — Notifications and feedback**

The user approved the manual-first sequence at G0 and closed 1C at G4.
AI explanation and model evaluation carry into 1F.

P1D-01 was delivered and the approved one-time preview completed. The user
reviewed the five recommendations and found them unsuitable. The user wants
quality and vote counts considered alongside individual relevance and limited
watching capacity. Reality and documentary preferences depend on the title.
P1D-02 added IMDb audience evidence and manual review. The approved
comparison returned three shortlisted titles and five for manual review.
The user found the movie results appealing and clarified different goals for
movie awareness and show selection. Future ranking and feedback should account
for show format, subject and genres.
[Verification](deterministic-ranking-proposal.md#p1d-02-implementation).

On 2026-11-23 the user requested a LINE sending test and reported an ngrok
tunnel forwarding to localhost:8383. The app has no outbound LINE command yet.
Local configuration inspection found no identified outbound access token or
recipient ID. The user's own recipient ID, channel token and quota status are
needed for a direct one-message test. No message was sent. The previously
approved signed webhook and feedback sequence remains after first receipt.
The user added both settings locally. On 2026-11-23 the token setting was
present, but the recipient value failed LINE's user-ID format check. The
recipient value was subsequently corrected. On 2026-11-23, read-only LINE
API checks confirmed the expected bot and recipient display name, with
approximate quota usage of zero out of 500. A single user-authorized push
containing the three comparison shortlist items was sent directly through
the LINE API and returned HTTP 200. The user confirmed receipt on 2026-11-23.
This was a direct API test; the application has no outbound LINE command yet.

See the [index](README.md#completed) for completed work.

| ID | Task / scope | Status | Gate | Request / decision / evidence | Last reviewed |
| --- | --- | --- | --- | --- | --- |
| P1E-01 | LINE gateway, signed webhook and title feedback | In progress | — | All 27 PostgreSQL tests passed. User approved V5 on `pmtd_agent.pmtd` on 2026-11-23. Flyway applied it and read-only verification confirmed V1–V5 and the five new tables. User authorized G3; implementation and supporting records pushed as `e22ff87` to `origin/master` on 2026-11-23. Live webhook testing remains a separate decision. [Evidence](line-feedback-proposal.md#verification-status). | 2026-11-23 |
