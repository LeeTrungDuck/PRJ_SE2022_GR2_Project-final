# Chatbot setup

The dashboard now has a bottom-right AI assistant. It uses native HTML details/forms and a same-origin iframe. Sending a question refreshes only the conversation. No JavaScript is required for this feature.

Type a message in the single-line question field and press Enter, or click the Send button. The field uses native form submission; the mobile keyboard is marked with the Send action. While a reply is pending, the question is read-only and Send is disabled.

## Code locations

The chatbot's Java implementation is grouped in the `AIChat` package at `src/java/AIChat/`:

- `ChatController.java`: HTTP requests, session state and authorization checks.
- `ChatAssistant.java`: combines guides, approved database context and the model response.
- `ChatClient.java`: provider configuration and HTTP requests.
- `ChatKnowledgeBase.java`: role-filtered guide retrieval.
- `ChatDatabaseContext.java`: topic selection and safe, bounded database context.
- `ChatJson.java`: the package-private JSON codec.
- `ChatDataKind.java`: the fixed operational dataset enum.
- `ChatException.java`: safe user-facing chat failures.

`ChatDatabaseDAO` stays in `dao`; `ChatDatabaseSnapshotDTO`, `ChatMessageDTO` and `ChatSourceDTO` stay in `dto`. The servlet class in `web.xml` is `AIChat.ChatController`; the public `/ChatController` URL and `MainController?action=CHAT` route are unchanged.

Web resources retain their web-app locations: `web/web/Chat.jsp`, `web/web/chatWidget.jspf`, `web/css/chat.css` and the protected guides under `web/WEB-INF/chat/`. Provider setup scripts remain under `tools/`. JSON/provider/database unit tests are in `tests/AIChat/`; servlet tests reuse the management fixtures under `tests/controller/`.

## Configure the provider

### Gemini on this Windows development machine

In VS Code, choose **Terminal > Run Task > Configure Gemini chatbot**. Enter the key in the masked local window, choose a model available to your Google AI Studio project, and press **Save settings**. The default model is `gemini-3.5-flash-lite`; availability and quota still depend on your project. The checked connection test sends one short greeting before saving and reports errors without showing the key or raw provider response.

The setup saves `CHAT_API_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai`, `CHAT_TOKEN_FIELD=max_tokens`, the chosen `CHAT_MODEL` and `CHAT_API_KEY` in your **Windows user environment**, outside Git. Keys are not encrypted by this environment-variable mechanism; they remain server-side and are not written into project files. A non-secret result is stored in the ignored `nbproject/private/chat-setup-status.json`.

Stop the current Tomcat, then run **Run SmartSwitchSystem (Tomcat)** in VS Code. Its launcher reads the saved user environment even if VS Code was already open. When launching through NetBeans or IntelliJ instead, close/reopen that IDE after setup so its Tomcat process inherits the new settings. This machine's existing Tomcat context points to this project's `build/web` directory. Other machines must register their own context or deploy the built WAR as usual.

To reopen setup from PowerShell in the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File .\tools\configure-gemini.ps1
```

The execution-policy setting applies only to that helper process. No machine-wide execution policy is changed. To remove the saved key, use Windows **Edit environment variables for your account**, delete `CHAT_API_KEY`, and restart Tomcat. The launcher contains no key. Do not add keys to `launch.json`, `tasks.json`, JVM command-line arguments or Git.

References: [Gemini's OpenAI-compatible endpoint](https://ai.google.dev/gemini-api/docs/openai), [Gemini 3.5 Flash-Lite model ID](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite), [Google AI Studio API keys](https://aistudio.google.com/apikey).

### Other compatible providers

Set these environment variables in the **Tomcat run configuration** (Smart Tomcat/NetBeans/service), then rebuild/restart the application:

| Variable | Value |
|---|---|
| `CHAT_API_BASE_URL` | Provider base URL including its API prefix, for example `https://api.openai.com/v1`. Do not append `/chat/completions`. Optional for OpenAI because this is the default. |
| `CHAT_API_KEY` | Your provider's API key. Set it locally in the server environment. Do not put it in a JSP, source file, Git commit or conversation. |
| `CHAT_MODEL` | Exact model ID available to your account/provider. Required; the application does not guess a model. |
| `CHAT_TOKEN_FIELD` | Optional. Defaults to `max_completion_tokens`. Use `max_tokens` only if your compatible provider requires that field. |

The configured endpoint is `POST <base-url>/chat/completions`, with Bearer authentication, JSON `model`, `messages`, `stream:false`, and a 1024-token output limit. Only HTTPS is accepted except loopback HTTP used for local testing. The provider must support system/user/assistant messages and return `choices[0].message.content` as text.

Equivalent JVM properties are `smartSwitch.chat.baseUrl`, `smartSwitch.chat.apiKey`, `smartSwitch.chat.model` and `smartSwitch.chat.tokenField`. Properties override environment variables when nonblank. Prefer environment settings for the key. They must be present in the process that starts Tomcat, not just in an unrelated terminal.

When configuration is missing/invalid, the widget says the assistant is not configured and disables generation. It never invents a local AI reply. Invalid authorization, timeouts, malformed replies and provider rate limits produce safe visible errors.

API contract reference checked during implementation: [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create). Provider/model availability and actual credentials must be checked with your chosen provider.

## Use

1. Sign in as an active Admin, Operator or Viewer.
2. Click the blue robot/chat icon in the lower-right corner.
3. Select a suggested question or type a question and press Send.
4. Use New chat to clear the current session's conversation. Click the same launcher to close the panel.

The interface is English, consistent with the dashboard. The model is instructed to answer in the question's language, including Vietnamese.

## What the assistant can access

It uses **basic keyword RAG** over these server-owned guides:

- `web/WEB-INF/chat/common.md`: common workflows.
- `viewer.md`: Viewer access.
- `operator.md`: device/permission management for Operator and Admin.
- `admin.md`: User Management for Admin only.

Guide sections begin with `## Title | ACTION_KEY`. Their text and bilingual keywords drive retrieval. Keep action keys mapped in URLMap and keep guide content aligned with real application behavior. Related-page links are generated from retrieved guide metadata, not arbitrary URLs returned by the model.

The servlet refreshes account identity/role through UserDAO. The model receives the role, bounded conversation, permitted guide excerpts and relevant approved operational database projections. It never receives the authentication DTO or database credentials. This is not a vector/embedding index or a document-upload system.

### Approved database access (2026-10-16)

The user approved these fields for Gemini, after server authorization:

| Dataset | Model-visible fields |
|---|---|
| Active ESP32 | Device ID/name and saved ONLINE/OFFLINE/ERROR state |
| Active switches on active ESP32 | Switch/device IDs and names, GPIO, saved ON/OFF state |
| Active schedules for those switches | Switch/device reference, command, daily time, enabled |
| History for those switches | Switch/device reference, command, saved result, timestamp; no actor |

Admin/Operator can read all active operational records, matching their existing read scope. Viewer rows and totals require a current active permission on an active switch and active parent ESP32. Every chat SELECT also checks that the account is still active and its database role matches the server identity. Account/profile/contact/credential columns, grantor/actor IDs and IP/hostname/MAC columns are never selected into a chat snapshot, including for Admin.

`ChatDatabaseDAO` contains only fixed prepared SELECT statements. Neither a question nor model output can supply SQL, identifiers or a different user's identity. Lists have at most 12 rows per dataset; full authorized totals and a partial marker remain available. History is ordered newest first. Relevant groups are selected server-side; an omitted group is unqueried, not empty. Context is bounded and timestamped in Vietnam time. The database path uses the application's existing connection; no SQL account, grants or database rows are changed by this feature.

Saved database state is not a fresh ESP32 measurement. This assistant does not contact hardware, execute schedules, change records or grant permissions. Query failures stop generation with a safe error; they do not produce a fake empty database answer.

Operational visibility is fingerprinted on every chat request and checked again after generation. A changed permission/account/role clears earlier chat context and invalidates earlier forms so revoked facts do not travel through the next prompt. A role/account check is also repeated before storing a generated reply.

Only the approved fields are shared. Obvious emails, phones, IP/domain/MAC addresses and key/password patterns embedded in labels are masked as an additional safeguard. This heuristic is not a general personal-data classifier: keep private information out of operational names, whose inclusion was explicitly approved. Typed chat messages still go to the configured provider; do not paste secrets into them.

History is kept only in the session (up to six message pairs). A successful new login, logout, changed account/role/operational scope or expired session removes/reset context. This does not configure the provider's own retention policy; consult that provider separately when choosing it.

### Which switches can I control?

Questions such as “vậy tôi có thể điều khiển SW nào?” and “Which switches can I control?” select a dedicated `CONTROLLABLE_SWITCHES` projection. Bare `SW` is also recognized for ordinary switch questions. A control list requires the signed-in account's active device-permission row for every role, including Admin and Operator, matching `SwitchStageController`; being able to view a switch does not prove control permission. Switches and parent ESP32 devices must be active too. This uses the existing `is_active` flag; it does not restore a deleted `canControl` database column.

These exact control-list replies are formatted directly from the privacy-filtered snapshot, without asking the AI provider to infer permissions. Replies contain switch IDs/names, GPIO and parent-device IDs/names, with a Device Control link. They clearly distinguish zero permissions from a partial list (at most 12 rows, with the exact permitted total). They do not operate hardware or promise that an ESP32 is reachable. Other questions continue through the configured AI provider.

The session-scope fingerprint includes current personal control grants for every role. Revoking an administrative user's grant therefore clears older permission answers even when that user can still view all active switches. Existing account rechecks, CSRF, replay protection, rate limits and escaping remain in force.

## Request protection

- `CHAT` routes through MainController to `/ChatController`; direct servlet requests are checked too.
- Only authenticated active accounts can send. GET cannot send/clear.
- POST requires CSRF and a bounded one-use request ID. Parallel sends cannot generate twice. A repeat of the latest accepted send redirects to its existing conversation/result; unknown tokens remain rejected.
- While a reply is pending, the iframe uses native HTML refresh every two seconds. Send, suggestions and New chat are disabled, and the draft field is read-only. Refreshing only checks state; it never calls the provider. A provider failure remains available to the refreshed view with its original message and draft.
- Questions are limited to 1,000 characters; provider responses/IO, recent context and session history are bounded.
- Limit: one in-flight reply per session, a 1.5-second gap and at most ten accepted send attempts per minute. Clear does not reset that quota.
- Text is escaped in JSP. No model HTML/JavaScript is rendered. Provider credentials and raw provider bodies do not appear in user-facing errors.

## NetBeans build and deployment

This project disables `j2ee.compile.on.save` and `j2ee.deploy.on.save`. Use **Build** or **Run** after changing Java code so Ant compiles the current sources. Static web resources still have copy-on-save enabled.

After the chatbot package move, the NetBeans class-signature cache did not contain `AIChat.ChatController`. A successful WAR build could therefore be followed by a cached deploy that omitted `build/web/WEB-INF/classes/AIChat/ChatController.class`, producing `ClassNotFoundException` when opening chat. Rebuilding alone repaired only the current deployment; disabling cached Java deployment prevents that path from replacing the compiled servlet on the next Run.

For an existing broken deployment, run **Clean and Build**, then **Run** the project. Reload the application after the complete build so Tomcat discards any cached servlet-loading failure. DAO and DTO packages remain unchanged.

## Verification

The feature is tested with an isolated JDBC fixture and localhost provider, including real Servlet/JSP flows. No real API credentials are shipped. Actual provider connectivity remains unverified until you configure the key/model.

Windows Gemini setup verification (2026-10-15): both PowerShell helpers parse, the masked dialog initializes, VS Code configuration JSON parses, the launcher resolves the installed JDK 8 / Tomcat 9 paths, Ant `dist` succeeds, and all 38 local HTTP/JSON/RAG checks pass. Non-secret Gemini defaults have been saved to the Windows user environment. The key must be entered in the local dialog before a live provider check or Tomcat restart can activate generation.

See `tests/chat-audit/README.md` for commands and scope. Test evidence is under `build/chat-qa` and `build/chat-ui-qa`.
