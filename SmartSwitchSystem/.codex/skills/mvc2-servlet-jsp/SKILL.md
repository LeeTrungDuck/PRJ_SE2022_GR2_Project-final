---
name: mvc2-servlet-jsp
description: Apply the project's MVC2 rules when creating, editing, reviewing, or debugging Java Servlet/JSP code with JDBC and SQL Server.
metadata:
  short-description: Enforce MVC2 Servlet/JSP project rules
---

# MVC2 Servlet/JSP project skill

Use this skill for this SmartSwitchSystem project and similar PRJ301-style MVC2 code. Preserve the project's existing naming and URL conventions when they differ from textbook examples.

## Layer boundaries

- Controllers validate input, enforce authentication/authorization, call DAOs, choose the next controller, and forward or redirect.
- DAOs contain all SQL and JDBC resource handling. Use `PreparedStatement`; never concatenate user input into SQL.
- DTOs contain data, accessors, and small data-only helpers; they do not contain business workflows.
- JSP pages render request/session data and forms; they do not contain SQL or security decisions.
- Use the project's existing `MainController`, `URLMap`, package names, attribute names, and role codes rather than inventing replacements.

## Controller rules

- Route forms and links through `MainController?action=...` when the project convention supports it.
- Use a safe error URL as the default, validate and trim parameters before calling a DAO, and keep one final response path where practical.
- Use `request` for page-scoped data and `session` for identity such as `LOGIN_USER`.
- After a successful write, return to a controller that reads fresh data; do not forward directly to a stale JSP.
- Use POST for state changes and GET for reads. Use PRG redirects after successful login/create/update/delete when appropriate; preserve validation errors with forward.
- Compare constants safely, for example `"AD".equals(role)`.
- Do not expose SQL, Java exception details, or account-existence information to users. Log technical details and show safe messages.
- Check authorization in the servlet even if buttons are hidden in the JSP. Do not trust URL parameters or hidden fields for ownership or permissions.

## DAO and SQL rules

- Obtain connections through the project's DB utility. Close `ResultSet`, `PreparedStatement`, and `Connection` in reverse order.
- Match every `?` with the correct `setX` order, including the final `WHERE` key in updates.
- Multi-row reads return an empty list, not `null`; single-row reads may return `null`; writes use `executeUpdate() > 0`.
- Prefer JOINs over queries inside loops; use `LEFT JOIN` for nullable relationships and aliases for repeated tables.
- Avoid selecting passwords unless strictly required. Whitelist any dynamic sort column or direction.

## JSP and request rules

- Protect restricted JSPs at the top with a session/role check, redirect, and immediate `return`.
- Login uses POST and `type="password"`; search uses GET; mutations use POST.
- Use `readonly` rather than `disabled` when a value must still be submitted.
- Treat UI hiding as convenience only, not authorization. Confirm destructive actions.
- Preserve old input and search/price values after validation errors.

## Review and implementation workflow

When reviewing or changing code:

1. Identify the layer and trace its request, session, DAO, JSP, and `web.xml` dependencies.
2. Check authentication, authorization, validation, SQL safety, resource cleanup, and response flow.
3. For a new action, update the action constant, `MainController`, servlet, both `web.xml` entries, JSP, and navigation as needed.
4. For `LoginController` specifically, verify trimmed input, safe failed-login messaging, session fixation protection, `LOGIN_USER`, and the redirect/forward behavior.
5. Report concrete violations with file and line references, then make the smallest compatible fix requested by the user.

## Project compatibility

The source rules target JDK 8, `javax.servlet`, Tomcat 9, and SQL Server. Do not migrate to `jakarta.servlet` or Tomcat 10 unless the user explicitly requests a platform migration.
