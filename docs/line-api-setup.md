# LINE API setup for PMTD-Agent

Last reviewed: 2026-11-23 (Asia/Jerusalem)
Status: The user confirmed Messaging API activation for PMTD-Agent and supplied
the channel settings. They are stored in the ignored `application-local.yml` for
future use. The webhook handler and application outbound command are pending.

On 2026-11-23 the user supplied an access token and recipient ID in ignored
local configuration and authorized a sending test. Read-only LINE API checks
matched the expected bot and recipient display name and reported a monthly
limit of 500 with approximate usage of zero. A single direct API push containing
three shortlisted recommendations returned HTTP 200. The user confirmed receipt
on 2026-11-23. The direct test did not exercise an application gateway.

Account links supplied by the user:
[Official Account Manager](https://manager.line.biz/account/@966sowkr) and
[PMTD-Agent public page](https://page.line.biz/account/@966sowkr).
These links could not be fetched by the web tool; no authenticated account
inspection or setting change was performed. The public account handle is not a
Messaging API channel ID, access token or recipient user ID.

## How the pieces connect

The Official Account is the visible bot identity. Enabling Messaging API in
Official Account Manager creates its API channel. The Developers Console then
holds channel settings. An access token authorizes outbound API requests; the
channel secret verifies inbound signatures. A recipient's provider-specific user
ID identifies where to send; it differs from the public `@966sowkr` account handle.
See [LINE getting started](https://developers.line.biz/en/docs/messaging-api/getting-started/).

## User setup when ready

1. The user enabled Messaging API in Official Account Manager. LINE's
   provider assignment is permanent for this channel.
2. Open [LINE Developers Console](https://developers.line.biz/console/) using the
   same account. Select that provider and confirm a Messaging API channel exists.
3. Note the account's billing region, plan, allowance and current usage. No quota
   is inferred from Israel timezone. Proactive push messages count toward the
   allowance; replies are excluded from that monthly count. See
   [LINE pricing](https://developers.line.biz/en/docs/messaging-api/pricing/).
4. When implementation is ready, prepare the channel secret and an access token
   using LINE's supported token flow. Keep both in local ignored configuration or
   process environment. Token lifetime/renewal must be chosen before operation.
5. Add PMTD-Agent as a friend using its channel QR code. For the first push,
   use **Your user ID** in the channel's **Basic settings** with your LINE
   account linked to your Business ID. Store it locally as the sole recipient.
   [LINE user IDs](https://developers.line.biz/en/docs/messaging-api/getting-user-ids/)
6. After receipt of the first personalized push is confirmed, configure and
   verify the bot webhook, then enable it. Review automatic
   greetings/replies to avoid duplicate bot responses. Do not change these now
   merely to run the foundation tests.

LINE's [bot guide](https://developers.line.biz/en/docs/messaging-api/building-bot/)
documents tokens, friend setup and webhook configuration. Inbound events require
a public HTTPS URL with a browser-trusted certificate. The desktop's loopback
address and a LAN-only Kind cluster are not reachable by LINE. The user chose
[ngrok](https://ngrok.com/docs/start) to test the webhook from the desktop and
will enter the resulting URL in LINE. After the signed webhook handler is ready,
start the tunnel on the app's HTTP port, append the handler's path to the HTTPS
URL, and use **Verify** in LINE Developers Console before enabling the webhook.
Tunnel setup is pending the handler. Regenerate the channel secret before the
first public webhook test because the current value was shared in chat, then
update the local YAML.

## Planned application responsibilities

Use Spring HTTP client facilities behind `NotificationGateway`, without a bot
SDK initially. Validate signatures against the exact raw request bytes before
JSON parsing; check destination/account and the allowed user; deduplicate event
IDs; bind postbacks to stored recommendations. Never log secrets or raw private
webhook payloads. Use fake gateways/signed fixtures first. Exact action design
waits for the user's deferred feedback decision.

Implement monthly-cap checks, bounded sends and provider-supported retry handling
after schedule/resend decisions. Retain delivery uncertainty across crashes; a
locally unique delivery key alone cannot prove a remote send happened once.
The first direct personalized push was received. The remaining messaging work
includes an application gateway, signed webhook and feedback, subject to the
project's phase and design approvals.
