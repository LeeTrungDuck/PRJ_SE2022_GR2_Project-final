# Updated device and permission schema

User-confirmed scope (2026-10-14): device records no longer have a last-seen
timestamp; switch permissions have one active flag for both view and control.
Database changes have already been made by the user. No database mutation is
part of this task.

Acceptance:

- Remove deleted fields from DTOs, SQL SELECT/INSERT/UPDATE and row mapping.
- All permission checks and draft/UI state use the same active flag.
- Checking Access and saving enables view/control; unchecking soft-revokes.
- Preserve role checks, CSRF, POST writes, draft/reset/select flows and atomic
  permission writes. Never physically delete permission rows.
- Keep active-permission filtering in Viewer device/switch/history/schedule
  reads. Preserve device/switch active-state checks.
- Permission UI remains English and presents one View & Control checkbox.
- Compile production sources and validate schema mappings, grant/revoke/reset
  flows, error handling and rendered JSPs with isolated fixtures.

The repository names the existing tables `tblESP32_Device` and
`tblDevice_Permission`. No table rename is inferred from the user's shorthand.

Completed verification:

- All 37 production sources compile with Java 8.
- SchemaCompatibilityTest: 73 mapping, JDBC, draft/reset/revoke/re-grant,
  outdated-form and actual switch-controller permission-guard checks passed.
- ManagementControllerTest: 91 existing checks passed. Its JDBC binding check
  count reflects the reduced number of permission columns/parameters.
- ManagementErrorTest: 32 checks passed; LoginControllerTest: 33 checks passed.
- Isolated Tomcat: 18 HTTP checks passed for combined access, checked/unchecked
  reloads, reset, draft revocation, old forms, invalid switch IDs, CSRF, role
  denial and Device Manager rendering. Fixture DAOs performed all test writes.
- Chrome desktop screenshots of Permissions and Device Manager were inspected.
- A read-only SQL Server metadata query confirmed the exact table names and
  remaining columns. The updated device and permission DAO read methods then
  ran successfully against that database. No real database rows were modified.
- Runtime source/UI scan finds no deleted column/property references. Viewer
  list/history/schedule queries continue filtering active permissions and
  active device/switch rows.

Drafts use a new session key; login clears both draft versions. Permission
forms carry an `active-v1` marker. Old forms, including an old unchecked form,
must reload before saving, preventing accidental revocation after deployment.

Manual check after Clean and Build / redeploy:

1. Sign in as Admin or Operator and open Device Permissions.
2. Select a Viewer. Check View & Control and select Save Permissions.
3. Verify the permission row has `is_active=1`; sign in as the Viewer and check
   that the granted switch appears and can be controlled.
4. Uncheck View & Control and save. Verify `is_active=0`, the row is retained,
   and the Viewer no longer sees or controls that switch after reloading.
5. Re-grant, switch accounts with an unsaved draft, and use Reset Changes to
   confirm the saved state is restored. Device management should load without
   asking for the deleted timestamp column.

Limit: no real grant/revoke or physical ESP32 operation was executed during
verification. Those state-changing paths were tested using isolated fixtures.
