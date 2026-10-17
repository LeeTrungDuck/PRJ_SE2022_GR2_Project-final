# Chat submission recovery — 2026-10-15

The user reported an expired/already-submitted form warning on their first chat attempt. Live access logs showed HTTP 409 completing before a successful POST shortly afterwards. A controlled reproduction confirmed that a second identical iframe submission can replace the browser's first navigation while the first generation is still running. The old servlet checked the consumed token first and rendered 409, hiding the pending reply.

The latest accepted send ID is now retained per authenticated session. An identical send with valid CSRF redirects to the existing conversation, both during generation and after completion. It never calls the provider again. Unknown tokens still return 409, and invalid CSRF still returns 403. Both checks and reservation are repeated under the session lock to handle parallel requests.

A pending GET returns 202 and renders a two-second native HTML refresh confined to the chat iframe. Sending, suggestions and clearing are disabled while waiting, and the composer is read-only. Pending reads do not allocate new form tokens. Original provider errors and bounded drafts are retained for one subsequent GET so a replaced navigation does not erase the real error. Account/role changes and new logins clear replay/feedback state. Clear rechecks the active reservation under the same lock.

Validation: the new overlapping-submit regression failed against the old servlet, then passed after the fix. Final checks pass: 13 submission/recovery, 38 existing chat-controller, 33 login, 32 real Servlet/JSP and 8 existing HardwareClient checks. The HTTP tests include closing the original client connection mid-generation, then recovering the same reply without a second provider call. All probes use fixtures/localhost; no live SQL writes, hardware commands or extra Gemini calls were made for this fix. Ant `dist` passes. The local development Tomcat automatically reloaded SmartSwitchSystem after the updated classes were built; other applications were not restarted.

Browser observation was unavailable in this session (no connected browsers; IAB unavailable). Pending markup, refresh directives, disabled controls and final conversation were verified through real Tomcat/JSP HTTP responses. A live browser visual/interaction check is not claimed for this follow-up.

The live application also returns the expected protected 401 sign-in view with the updated stylesheet version (`20261015-chat-2`). The isolated QA Tomcat has stopped. The user's Gemini setup status reports a successful greeting after the key was entered locally; no key value was read into the conversation.

## Approved database context — 2026-10-16

The user explicitly approved device/switch IDs, names, GPIO and saved status; schedule times/actions/enabled; and anonymized history times/actions/results. Fixed SELECT projections and an independent output-field allowlist enforce that boundary before generation. SQL also verifies the active authenticated account and exact role in EXISTS predicates; account columns are used only for authorization, never returned as chat data. Viewer rows and totals require current active grants and active switches/devices.

Operational scope fingerprints now invalidate old conversation/replay state on changes. Snapshot scope is checked before generation, and account/role/scope are rechecked before a reply is stored. SQL failures suppress history/model calls. The previous guides-only constructor remains available for controlled tests; the production controller explicitly wires database context.

Queries/response context have bounded lists/time/payload, with full authorized totals and partial indicators. No generated SQL, chat write action, schema migration, new DB account/grants or hardware access was added. Field exclusions are strict; label heuristics are an additional safeguard for obvious sensitive strings, not a promise to classify all free text. See `../plans/chat-database.md` for final evidence and limits.
