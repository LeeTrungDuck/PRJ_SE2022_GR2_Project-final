# Floating assistant implementation

Status: implementation and local verification complete

Follow-up: identical accepted sends now recover the existing result through a 303 redirect. Pending GETs use 202 and native HTML refresh, with no JavaScript. The regression reproduction and final verification are recorded in `implementation-notes.md`.

2026-10-16: the user approved scoped operational database access (devices/switches, schedules and anonymized history). The assistant now reads these through explicit safe SELECT projections; personal/credential/network fields remain unavailable. Implementation and verification: `../plans/chat-database.md`.

## Accepted direction

Add ChatController and an English chatbot launcher fixed at the bottom right, consistent with the dashboard. User selected an OpenAI-compatible provider. Preserve the project's JDK 8, javax.servlet, Tomcat 9, MainController/URLMap, MVC2 and native HTML forms.

## Implementation

1. Add an authenticated read-only chat route with POST + CSRF, bounded session history, account isolation, duplicate-submit protection and friendly errors.
2. Retrieve relevant bundled guide sections by keywords, restricted to the authenticated role, then send only the conversation and guide excerpts to the configured Chat Completions endpoint.
3. Add a shared native details/iframe widget through the sidebar include, plus a protected JSP and scoped CSS. No new JavaScript.
4. Read API base URL, model and key from server environment/properties. No hardcoded keys or fake fallback AI replies. Missing configuration and provider failures remain visible.
5. Verify Java compilation, HTTP/JSP flows, provider protocol against localhost fixtures, negative security/session cases, and desktop/tablet/mobile rendering.

## Acceptance and boundaries

- All authenticated dashboard pages using the sidebar have the bottom-right launcher.
- Open/close/send/clear work without JavaScript, leaving the parent dashboard untouched.
- Anonymous/inactive accounts cannot use chat; role/account changes reset prior context.
- GET never sends a message or clears history. Invalid CSRF, duplicate submits and concurrent sends do not cause additional provider requests.
- Provider keys, raw exceptions and database credentials never reach the browser or logs.
- The assistant does not call device hardware, execute model-generated SQL/actions or ingest private user records. Server-owned DAO queries provide only the subsequently approved operational projections.
- Keyword document retrieval is basic RAG, not an embeddings/vector-search system.
- Actual provider connectivity cannot be claimed without configured credentials; automated tests use a local HTTP provider.

## Verification results

- 45 production Java sources compile with JDK 8.
- 38 controller/auth/session/input checks and 38 HTTP/JSON/RAG checks pass.
- 25 real Servlet/JSP integration checks pass, including all seven shared dashboard launchers, role-filtered retrieval, protected guide resources, provider errors, escaped content and parallel submissions.
- Existing regression suites: Login 33, ManagementController 91, ManagementError 32, HardwareClient 8 pass.
- Chrome verifies open/close/send with the parent dashboard preserved at 1440x1000, 768x1024 and an actual 390x844 mobile viewport. Ready, conversation and missing-configuration states were visually inspected.
- The QA Tomcat and browser were stopped. No live SQL writes, live provider calls or physical device commands were used.
- API key/model setup and actual provider connectivity remain environment-dependent and unverified; see `chatbot-setup.md`.
- Windows Gemini setup now includes a masked local key dialog with an optional greeting probe, user-environment storage and a VS Code Tomcat launcher that refreshes that environment. The configured default endpoint/model are Gemini / `gemini-3.5-flash-lite`. The key was subsequently entered locally and the setup's non-secret status records a successful greeting. Ant `dist`, script/JSON checks and the 38 local client checks pass for that setup change.
