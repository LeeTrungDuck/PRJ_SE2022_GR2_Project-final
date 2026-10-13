# English UI

Scope: translate application-owned text in JSP pages, shared navigation,
accessible labels, form hints, dialogs and server-generated UI messages.

- Use consistent English across Login, User Management, Permissions, Account,
  Device Control, Device/Switch Management, Schedules and Control History.
- Set document language to English. Keep user/device names from the database.
- Preserve actions, URLs, request parameters, enum/SQL values, authorization,
  validation rules, password handling and current styles/interactions.
- Update existing tests that assert translated messages; retain behavioral
  assertions and Vietnamese user-input fixtures.
- Verify Java compilation, focused controller tests, JSP rendering and a scan
  for remaining Vietnamese application text. Do not change database contents.

Completed verification (2026-10-13):

- Compiled all 37 production Java sources with Java 8.
- ManagementControllerTest: 95 checks passed.
- ManagementErrorTest: 32 checks passed.
- LoginControllerTest: 33 checks passed.
- 15 HTTP checks passed on an isolated Tomcat 9 instance: all eight application
  pages plus the error page, empty states, Add User/Revoke Permissions dialogs,
  and a Vietnamese personal name displayed without translation.
- Inspected desktop Chrome screenshots of Login, Account and Permissions.
  Shared English navigation, labels and buttons fit the existing layout.
- Verified Java changes are limited to string literals. Form/route attributes
  and existing inline scripts are identical to their pre-translation versions.
- No Vietnamese application text remains in JSP markup or Java message
  literals; developer comments and user-input fixtures keep their original text.

Test maintenance: the old management test incorrectly required Viewer-only
permission targets, although the pre-existing controller/DAO already accept
active Admin, Operator and Viewer accounts. Its negative case now uses an
inactive account and still asserts an error with no writes. Production role
rules were not changed.

Tests and JSP rendering use fake data and an isolated local servlet outside
the project. No live SQL Server access or real database writes were performed.
