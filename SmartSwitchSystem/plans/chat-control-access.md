# Chat: current user's controllable switches

Status: implemented and verified, 2026-10-16.

## Accepted outcome

Answer “vậy tôi có thể điều khiển SW nào?” with current, authorized switch IDs/names, GPIO and parent device. Preserve AIChat packaging, separate DAO/DTO packages, native HTML submission and existing chat request protections.

## Findings

- Bare `SW` is not selected by the current operational-topic classifier.
- Device Control exposes all active switches to Admin/Operator, but SwitchStageController requires an active personal permission for every role. Viewing is not proof of control.
- Existing administrative conversation fingerprints reflect visibility, so they must also reflect personal control grants before permission answers can safely remain in history.

## Implementation and acceptance

1. Recognize Vietnamese/English questions asking for the current user's controllable switch list; retain status/history/schedule intent separation.
2. Add a fixed controllable-switch projection using active personal grants, active switches/devices and the existing active-account/role guard for all roles. Keep the 12-row bound, exact authorized totals, private-field exclusions and SELECT-only SQL.
3. Include current control grants in the chat-scope fingerprint. Grant/revocation changes must invalidate earlier conversation facts for all roles.
4. Return these exact permission facts directly from the privacy-filtered snapshot, with a Device Control link, localized text, honest empty/partial results and no generated hardware commands. General questions continue using the configured model.
5. Verify intent variants, role isolation, revoked/inactive rows, partial lists, label privacy, no provider call for permission facts and real Servlet/JSP rendering. Compile/build and verify the deployed servlet; keep NetBeans cached Java deployment disabled. No real SQL writes or hardware commands during testing.

## Verification

- 38 control-list intent/role/revocation/privacy/bounded-reply checks; 61 database/context checks; 9 scope/session checks; 38 controller checks; 13 submission/recovery checks; 38 HTTP/JSON/RAG checks.
- 41 real HTTP/JSP checks, including the original Vietnamese SW question, forged form identity, empty/revoked access and distinct Admin viewing/control scopes; 8 existing HardwareClient exception checks. All use isolated fixtures.
- A read-only SQL Server probe passed for ADMIN, OPERATOR and VIEWER using the deployed production classes. It compared authorized IDs and exact totals internally and verified the native reply; no account/record values were printed or sent to an AI provider.
- Ant dist passed. All 58 deployed classes match the WAR, test/probe classes are excluded, cached NetBeans Java deployment remains disabled, and the actual Tomcat context was redeployed. Its chat route returns the expected anonymous sign-in UI (HTTP 401).
- Evidence is copied to `outputs/chat-control-access-2026-10-16/`. No real SQL rows were changed and no hardware command or live AI-provider request was made for verification.
