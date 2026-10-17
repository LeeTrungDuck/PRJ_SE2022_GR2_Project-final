# MVC2 management pages (Servlet/JSP)

Status: implemented for device/switch management, schedules, and control history.

## Request flow

| MainController action | Servlet | JSP |
| --- | --- | --- |
| `DEVICE_MANAGER` | `DeviceManagementController` | `DeviceManager.jsp` |
| `SCHEDULE_MANAGER` | `ScheduleManagementController` | `ScheduleManager.jsp` |
| `CONTROL_HISTORY` | `ControlHistoryController` | `ControlHistory.jsp` |

Links and forms submit to `MainController?action=...`; `URLMap` forwards to the
mapped servlet, which validates the session and role, reads/writes through DAO
classes, sets request attributes, then forwards to the JSP. JSPs render DTOs and
do not contain SQL or preview/sample records. Direct access to a JSP redirects
to its controller; unauthenticated access redirects to login.

## MVC2 and data rules

- GET is read-only. Mutations use POST, a session CSRF token, and a 303
  redirect back through `MainController` after success.
- Switch controls accept POST only and require both a session CSRF token and a
  one-time token bound to the rendered switch form. Replayed form submissions
  are rejected, and commands for the same switch are serialized in the
  controller to prevent overlapping read-toggle-write operations.
- The authenticated user ID comes from the refreshed session account. Schedule
  edits and soft deletes are restricted to schedules owned by that user; viewers
  can only read their own schedules and history.
- Admins and operators can manage active ESP32 devices, switches and schedules.
  Schedule creation stores the session user ID; clients cannot assign another
  owner. History is read-only.
- SQL is confined to DAOs and uses prepared statements. DAO exceptions are
  propagated to the controller and resources are closed with try-with-resources;
  database failures are logged and shown as a friendly service error.
- Server-side validation checks IDs, field lengths, GPIO range, enum values,
  schedule time and confirmation before soft deletion. Output values use
  `<c:out>`. Delete confirmations use a required checkbox so the pages need no
  JavaScript.
- The schedule schema only stores a daily `run_time`, enabled/active flags, one
  switch, user, and ON/OFF action. The page does not claim weekday or one-time
  scheduling, which the current database cannot represent.
- Schedule create, update and delete operations are synchronized to the
  corresponding ESP32 through `/schedule/add` and `/schedule/delete`. If an
  ESP32 request fails, the page reports the synchronization failure; the
  database change may already have been saved. The Java server must be able to
  reach each device's configured host name over HTTP.
- Device status and switch status are not editable in the
  management forms; live state is maintained by the control/device flow.
- `HardwareClient` reports non-200 responses, timeouts, connection failures,
  invalid GPIO values and malformed/missing status data as `HardwareException`.
  Device-control synchronization and switch control log the exception and show
  a friendly error without treating missing hardware data as success.
- The three JSPs do not depend on the old preview model, `ui.*` data, or demo
  traces. Their controller models are placed directly into request attributes.

## Validation

- Application Java sources and both standalone test classes compile with JDK 8
  and the Tomcat 9 Servlet API.
- `tests/controller/ManagementControllerTest.java` covers management
  authorization/CSRF and verifies the three action-to-servlet routes and safe
  `MainController` fallback behavior. Its DB probes use JDBC proxies (95 checks).
- `tests/utills/HardwareClientTest.java` verifies successful hardware responses
  and that HTTP errors, invalid GPIO, missing state data and connection failures
  consistently throw `HardwareException` (8 checks).
- Jasper compiles the three management JSPs with the project's JSTL jars.
- The retired `NoJavaScriptUiTest` exercised the removed in-session preview
  application and is no longer an acceptance test for database-backed pages.
- No create/drop/migration SQL is needed. The existing DB schema limits the
  management features as described above.
