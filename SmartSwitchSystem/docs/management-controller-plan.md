# User / Permission / Account controllers

Status: implemented and verified in an isolated local copy.

Scope follows `huong_dan_de_tai.md`, sections 4, 5, 7 and 9. Implement only the
three requested modules; keep HTML forms without JavaScript and the existing
DAO/DTO/MVC structure. No database schema changes or real database writes were
performed during implementation/testing.

| JSP action key | URLMap value / servlet URL pattern | Controller | Authorization |
| --- | --- | --- | --- |
| USER_MANAGER | /UserManagerController | controller.UserManagerController | Active Admin |
| PERMISSION_MANAGER | /PermissionManagementController | controller.PermissionManagementController | Active Admin / Operator |
| ACCOUNT_MANAGER | /AccountManagementController | controller.AccountManagementController | Active logged-in account |

Forms keep `action=${activePage}`; buttons submit `uiOp` to choose the operation
inside that controller. Navigation/filter/dialog requests are GET. Data changes
are POST with CSRF validation, followed by a 303 redirect.

Implementation:

1. User CRUD: list/pagination, add, edit full name/email/phone/role, change role,
   lock/unlock, reset password, delete. `UserDAO.delete()` updates `is_active=0`
   and revokes device permissions in the same transaction. History rows and
   FK targets remain. The current logged-in account and last active Admin are
   protected from deletion/demotion/deactivation. Profile/password/role updates
   use separate SQL statements to avoid overwriting unrelated fields.
2. Permission CRUD: active Viewer accounts and active Switch/ESP32 records are
   loaded from SQL. The permission's active flag grants both view and control.
   Unchecking Access soft-deletes
   the permission. Re-granting reactivates the existing `(user_id,switch_id)` row,
   respecting its unique constraint. Matrix writes are atomic and rollback on
   failure. Existing native form draft/bulk/select/reset buttons remain usable.
3. Account: reads the logged-in User ID from session, ignores submitted IDs and
   roles, saves full name/email/phone, checks the current password before changing
   it. No sample OTP, sample reset, fabricated creation date or SuperAdmin role.
4. `ManagementController` shares active-account checks, role checks, CSRF, safe
   errors and forwarding. `ManagementViews` adapts real DTOs to existing JSP view
   maps; it does not create or mutate the old demo `PreviewState`.
5. Fixed PermissionDAO INSERT/UPDATE placeholder indexes and resource lifetimes.
   UserDAO includes email/phone and rejects inactive login accounts. Login closes
   its DAO and rotates the session ID. Removed the obsolete servlet mapping for
   the missing `DashboardController` so Tomcat can load the current sources.
6. Shared non-preview request validation/map helpers now live in
   `controller.ManagementSupport`; no controller depends on the deleted `ui`
   package.
7. Device/switch, schedule, and history actions follow the same authenticated
   controller flow. Their SQL remains in DAOs, their forms post through
   `MainController`, and their JSPs receive direct request attributes rather
   than the legacy `ui` map. Schedule writes enforce session-user ownership;
   history reads are scoped to the logged-in Viewer.

Database compatibility:

- Uses existing columns from `Create_Database.sql`: full_name/user_name/email
  length 50, phone_number length 10. Forms validate the same limits.
- User IDs are generated with prefix `U`, maximum 20 characters.
- `PasswordUtil` stores salted PBKDF2-HMAC-SHA256 as `P1$` plus URL-safe Base64
  of a 16-byte salt and 16-byte verifier. The 46-character representation fits
  the existing `password varchar(50)`. P1 fixes 600,000 iterations; future work
  changing parameters must use a new version rather than changing P1 silently.
- The iteration choice follows the [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2).
  Existing plaintext seed credentials still verify for compatibility; new
  creations/resets/password changes always write a salted hash.

Validation:

- Compiled all Java using JDK 8 and Tomcat 9 Servlet API.
- `tests/controller/ManagementControllerTest.java` exercises controller
  authorization, CSRF, GET write rejection, User CRUD, self/last-Admin protection,
  profile ownership, password checks/hash storage, permission grant/revoke,
  reactivation, JDBC bindings and transaction rollback. It uses fakes/proxies
  and never accesses the real database.
- Twelve JSP/page/dialog variants compile and return HTTP 200 on isolated
  Tomcat at 127.0.0.1:18081 with test DTO models; three MainController action
  routes correctly redirect anonymous requests to Login.
- The database-backed management JSPs have no script tags, inline JavaScript or
  JS dependency.
- SQL Server integration against the user's running database and visual browser
  screenshots remain unverified. The test servlet exists only in the temporary
  test deployment and is not delivered into the application.

Run `Clean and Build` then `Run` in NetBeans against the actual project folder.
The database connection remains configured through the existing DBConnection.
No create/drop/migration SQL script needs to be run for this change.

To rerun the standalone checks, compile application Java into a temporary classes
directory, compile `tests/controller/ManagementControllerTest.java` with those
classes and the Tomcat Servlet API on the classpath, then run
`controller.ManagementControllerTest`. The obsolete in-session preview test has
been removed; it asserted behavior that the database-backed application no
longer provides.
