# User / Permission / Account MVC2 implementation

Scope: `huong_dan_de_tai.md` sections 4, 5, 7 and 9 and the supplied
`MVC2_quy_tac_va_ky_nang.md`. Only the three requested management modules.

- MainController action keys map to each servlet's URL pattern.
- Each module declares immutable instance ERROR/SUCCESS paths; request state,
  DTOs and DAOs are request-local. The view adapter has instance methods and
  no static data. Immutable SQL/password-format constants are allowed.
- One forward in the shared controller's finally block. Successful POST writes
  use the document's PRG exception: HTTP 303 to MainController to read fresh data.
  Errors forward with ERROR/uiError and retained non-password form values.
- SQL errors propagate from DAO to controller; UNIQUE/FK errors become friendly
  Vietnamese messages. Database read/connection errors render the error JSP.
- Anonymous/inactive requests forward to Login; denied roles render an error
  with HTTP 403. GET mutations return 405; invalid CSRF returns 403.
- User deletion and permission revocation UPDATE is_active=0. History and
  foreign-key rows are retained. Unlocking does not automatically restore rights.
- The actual schema uses permission.is_active as view permission, with
  canControl adding control; there is no separate canView column.
- Account identity comes only from LOGIN_USER. Passwords are verified and hashed,
  never returned in form values. Password whitespace is preserved during
  verification rather than silently changing the user's secret.
- No OTP/demo reset or unrelated controllers are added. Existing HTML/CSS and
  native forms are retained. SQL Server credentials/schema are unchanged.

Verification (isolated copy, JDK 8 / Tomcat 9):

- All production Java files compile successfully.
- ManagementControllerTest: 91 controller/JDBC/password checks passed.
- ManagementErrorTest: 32 MVC2 error/input/forward checks passed.
- All three JSPs compile using Jasper; servlet mappings validate as XML.
- 22 Tomcat HTTP checks passed: page/dialog rendering, direct-JSP guards,
  unauthenticated action routes, UNIQUE/FK messages with retained form values,
  403 role denial and 503 database-read error rendering.
- Tests use fake DAOs/JDBC proxies. SQL Server integration and visual browser
  layout verification remain unverified. No real database writes were requested.
- QA-only smoke servlets are excluded from project files.
- The obsolete DashboardController mapping was removed because that class
  does not exist; no Dashboard implementation was added.
