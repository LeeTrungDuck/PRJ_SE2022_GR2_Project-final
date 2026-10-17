# Product

## Register

product

## Users and purpose

SmartSwitchSystem is the existing Java Servlet/JSP dashboard for Admin, Operator and Viewer accounts managing ESP32 switches, permissions, schedules and account settings. Authorization follows the existing controllers and active permissions.

## Established design direction

Preserve the English dashboard, dark surfaces, blue primary actions and left sidebar. The user has explicitly requested readable text and consistent screens. The requested assistant is a small floating chat entry at the bottom right.

## Design principles

- Keep device workflows visible while using help.
- Reuse the current visual vocabulary and native HTML forms.
- Distinguish actual device state, documentation and unavailable data.
- Avoid low-contrast headings, unlabelled controls and invented success states.
- Make new controls keyboard accessible and usable on small screens.

## Chat scope

The user selected Gemini through the OpenAI-compatible API. The assistant answers questions using role-appropriate bundled documentation and approved, current saved database projections. Device/switch state, schedules and anonymized history follow server-enforced operational permissions. Account/contact/credential/network fields are excluded before generation. It does not execute device commands, generated SQL or writes. JavaScript is not required for the widget or message submission.
