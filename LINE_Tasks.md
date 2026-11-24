# LINE webhook checklist

## Connect for a session

1. In the ignored `application-local.yml`, keep `PMTD_LINE_CHANNEL_SECRET`, `PMTD_LINE_BOT_ID` (the bot's LINE user ID, not its @ handle), and `PMTD_LINE_USER_ID` (your user ID). Ask Codex to help identify the bot ID if needed. Never paste secrets into chat.
2. Start the application from the repository in PowerShell and leave it running:
   ```powershell
   $env:PMTD_PORT = '8080'
   java -jar build/libs/pmtd-agent-0.0.1-SNAPSHOT.jar '--spring.profiles.active=postgres,local,line-webhook' '--spring.flyway.enabled=false'
   ```
3. Start ngrok on the same computer: `ngrok http 8080`. Leave it running. If ngrok is not installed or connected, use [ngrok setup](https://dashboard.ngrok.com/get-started/share-localhost) and its [authtoken page](https://dashboard.ngrok.com/get-started/your-authtoken).
4. In the [LINE Developers Console](https://developers.line.biz/console/channel/2012688480), open **Messaging API**. Set the webhook URL to the ngrok HTTPS forwarding URL plus `/webhook/line`; click **Verify**, then enable **Use webhook**. Update the URL if it changes. The [LINE Official Account settings](https://manager.line.biz/account/@966sowkr/setting/messaging-api) are also available.

Start the app and tunnel again when they are stopped. An ngrok dev domain can stay the same across restarts; check the displayed URL. A running tunnel alone does not mean the app is reachable.

## Rotate only when needed

- **LINE channel secret:** If exposed or compromised, open the channel's **Basic settings** and click **Issue** beside **Channel secret**. Replace `PMTD_LINE_CHANNEL_SECRET` locally and restart the app. The old secret stops working immediately.
- **ngrok authtoken:** No rotation is needed for each connection. Rotate it if exposed or compromised, then update the agent's stored token using ngrok's dashboard instructions.

[LINE signature and rotation guidance](https://developers.line.biz/en/docs/messaging-api/verify-webhook-signature/) · [ngrok dev domains](https://ngrok.com/docs/gateway/domains)
