# Scoped database context for the assistant

Status: implemented and verified, 2026-10-16.

## Agreed outcome

The user explicitly approved sending Gemini these role-filtered operational fields: device/switch IDs and names, GPIO and saved states; schedule time/action/enabled; history time/action/result without the actor. Exclude all account/profile/contact/credential fields, IP/hostname/MAC, inactive rows and records outside the current user's permissions. Admin/Operator retain their existing all-active operational view; Viewer is restricted by active permissions and active switches/devices.

The existing Java 8 / javax.servlet / SQL Server MVC2 application, MainController CHAT route, no-JavaScript iframe, CSRF, duplicate-submit recovery and rate limits remain the integration points. This feature reads saved database records; it does not contact ESP32, execute generated SQL, change database rows or grant the model database credentials.

## Implementation tasks and checks

1. Add a DAO containing only fixed parameterized SELECT statements and explicit safe projections. Bound lists, statement duration and context size; provide totals within the authorized scope. Verify SQL bindings/projections, active filters and both Viewer and administrative scopes with JDBC contract tests and a narrow read-only SQL Server probe when available.
2. Add a small snapshot DTO and context formatter. Select only relevant operational groups from the question, explicitly serialize approved columns, mask obvious contact/network/secret patterns in labels and mark partial lists. Verify the outbound prompt contains approved facts, excludes seeded private fields and never includes owner IDs/network/profile data or a query generated from a user question.
3. Integrate through ChatAssistant and ChatController. Revalidate an operational-scope fingerprint before generation and before storing/rendering database-derived history. Reset chat context when scope/account/role changes; fail closed on database errors. Verify revoked permissions cannot reappear through prior session messages, supplied user IDs cannot choose another user's scope, and no provider call occurs on failed authorization/database reads.
4. Update role-filtered guides, setup documentation and the existing chat notice to describe database snapshots accurately. Preserve the existing layout and English UI. Verify real Servlet/JSP rendering and all prior chat/auth/replay tests against isolated JDBC/provider fixtures.
5. Build the application, verify the actual local deployment and stop owned QA processes. Do not commit/push, mutate real SQL data or send private database records during verification. Record precise results and remaining limits here.

## Acceptance

- A status/schedule/history question can receive fresh, relevant, permitted database facts.
- Private columns and history actor identities are excluded before any model request; permission checks are enforced in server code/SQL.
- Viewer rows and totals exclude ungranted/inactive switches and devices. Scope changes remove stale chat context.
- There is no arbitrary SQL or write-capable chatbot action, and empty/partial/failed reads are represented honestly.
- Tests inspect the actual provider payload, not just the model's refusal text.

Security reference: [OWASP RAG Security](https://cheatsheetseries.owasp.org/cheatsheets/RAG_Security_Cheat_Sheet.html); server-side authorization and data minimization remain independent of model instructions.

## Results

- 52 JDBC projection/privacy/context checks pass, including the captured generation input, Viewer filtering, forged roles, revoked grants, row caps/totals, label masking, valid timestamps and exclusion of extra private fields.
- 9 controller scope/history/error checks pass, including ignored form identity, mid-flight revocation/demotion and fail-closed SQL errors.
- Existing regressions pass: chat controller 38, duplicate-submit recovery 13, login 33, HTTP/JSON/RAG client 38 and HardwareClient 8.
- 36 real Servlet/JSP integration checks pass using isolated JDBC and a local provider. They inspect Viewer/Admin payload scope, privacy flags, revoked context and original no-JavaScript/CSRF/replay flows.
- The deployed classes' fixed SELECT queries ran successfully against the real SQL Server for active Admin, Operator and Viewer accounts, covering all four groups. The probe printed only role/check status, never data rows, credentials or identity values. No real data was modified.
- Ant `dist` and `git diff --check` pass. The local development Tomcat automatically reloads this application; owned QA Tomcat was stopped. No live Gemini calls were added during verification.
- A guide edit initially made schedule questions retrieve an unrelated history excerpt; the RAG regression caught it. The schedule explanation was moved to its own section, and the client/RAG checks passed again.

Limits: lists are capped at 12 rows per selected group and may be further shortened to fit the context budget. Totals are scoped to current active operational visibility. Names are explicitly approved free text; common sensitive patterns are masked, but this is not a universal personal-data classifier. Provider answers remain generated text. Browser visual QA was not repeated for the footer text change; rendered HTML and full controller/HTTP flows were checked.
