# Floating chat verification

JDK 8 / Tomcat 9. Production source is not modified by the test runners.

## Focused Java tests

Compile production sources to a dedicated output directory with Tomcat's servlet API, then compile:

- `tests/controller/ManagementControllerTest.java` (shared fixture only)
- `tests/controller/ChatTestController.java` (test-only bridge to `AIChat.ChatController`)
- `tests/controller/ChatControllerTest.java`
- `tests/controller/ChatSubmissionTest.java` (overlapping identical submissions, native pending state and failed-result recovery)
- `tests/controller/ChatDatabaseControllerTest.java` (scope changes, revoked history and database failures)
- `tests/AIChat/ChatDatabaseTest.java` (compile with `tests/admin-audit/adminaudit/AdminFixture.java`; uses only its in-memory JDBC fixture)
- `tests/controller/LoginControllerTest.java`
- `tests/AIChat/ChatClientTest.java`
- `tests/AIChat/ChatControlAccessTest.java` (compile with `tests/admin-audit/adminaudit/AdminFixture.java`; verifies current personal control grants for every role)

Run `controller.ChatControllerTest`, `controller.ChatSubmissionTest`, `controller.ChatDatabaseControllerTest`, `AIChat.ChatDatabaseTest`, `AIChat.ChatControlAccessTest`, `AIChat.ChatClientTest` and `controller.LoginControllerTest`. They use fake identity data / a loopback HTTP provider and do not call a live provider or SQL Server.

## Real Servlet/JSP integration

From the project root:

```powershell
& ([scriptblock]::Create([IO.File]::ReadAllText((Join-Path (Get-Location).Path 'tests/chat-audit/run.ps1'))))
```

The runner uses the existing test-only `tests/admin-audit` identity/JDBC fixture, plus this folder's local provider. A separate Tomcat binds to loopback port 18091 by default and contains no SQL Server driver. The fixture overrides chat configuration only inside that QA JVM, using a dummy key and localhost URL. Never copy fixture endpoints/classes into the production deployment.

The default runner stops its own server. For browser QA, add `-KeepServer`, then run:

```powershell
& ([scriptblock]::Create([IO.File]::ReadAllText((Join-Path (Get-Location).Path 'tests/chat-audit/browser.ps1'))))
```

The browser script launches its own hidden Chrome profile, verifies native open/close/send behavior, unchanged parent URL, desktop/tablet/mobile geometry and missing configuration, and saves screenshots. It closes its own Chrome process. Stop the retained QA server afterwards using its recorded PID and start time; do not stop another Tomcat.

Evidence: `build/chat-ui-qa/audit.log`, `evidence/*.html`, `evidence/*.png`, `browser-validation.log`. Java checks: `build/chat-qa/controller-tests.log`, `client-tests.log`, `login-tests.log`.

These probes validate the integration contract against controlled fixtures, not real provider billing/model availability, real SQL writes, embeddings, live device queries or hardware control.
