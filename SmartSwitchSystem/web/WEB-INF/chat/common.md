## Switch status and filters | DEVICE_CONTROL
Keywords: switch relay GPIO ESP32 status ON OFF filter loc trang thai bat tat thiet bi dong bo.
Device Control displays active switches on active ESP32 devices. Use ESP and Status filters, then Filter. Clear filters resets filters and page selection, not hardware state. Each page contains up to 20 switches.
Displayed state is loaded from the database. This page does not continuously monitor ESP32 or automatically synchronize hardware on reload. ESP32 is contacted when a control is attempted. The assistant can read current permitted saved device/switch records, including IDs, names, GPIO and saved states. It cannot contact ESP32 or confirm physical state. Database row lists may be partial; the supplied totals cover the permitted active scope.
Viewing a switch does not by itself grant control. Control requires an active device-permission row, including for Admin and Operator. A denied operation needs an Admin or Operator to check permissions. For connection errors, check device power, network and configured hostname, then retry from Device Control.

## My switch control access | DEVICE_CONTROL
Keywords: SW switch switches controllable which permissions permission allowed access quyen dieu khien nao duoc phep.
To ask which switches your own account may control, ask "Which switches can I control?" or "Toi co the dieu khien SW nao?". The server lists active personal device permissions on active switches and active ESP32 devices, including for Admin and Operator. General switch visibility is not proof of control. These permission answers come directly from the current filtered database snapshot and link to Device Control; the chatbot does not operate the switches. A partial list includes the full permitted count and how many rows are shown.

## Account settings and password | ACCOUNT_MANAGER
Keywords: account profile password change email phone ho so tai khoan mat khau doi.
Open Account Settings to edit your own full name, email and phone. Username is fixed. Save Changes applies profile edits. Password changes require the current password, a different new password of at least 10 characters with an uppercase letter, digit and special character, and matching confirmation. Passwords are hashed by the backend. Never submit a password to this assistant. It cannot view or change credentials.

## Daily schedules | SCHEDULE_MANAGER
Keywords: schedule time enabled daily lich hen gio tao sua bat tat lap.
Open Schedules, select an active switch, choose ON or OFF, enter a daily run time, set Enabled and submit Add Schedule. Edit a row's command/time/Enabled then Save to update it. Confirm Deactivate to soft-delete a schedule. The current page does not provide Run now. Saving a schedule stores its configuration; the assistant cannot verify automatic execution or firmware scheduling. Viewers can access schedules for switches with active device permissions. Admin and Operator can manage all schedules. The assistant can read permitted saved schedule commands, daily times and enabled state. It cannot create or execute schedules.

## Control history | CONTROL_HISTORY
Keywords: history log command error lich su nhat ky dieu khien.
Control History lists the latest control records and supports search and filters. Admin and Operator can view the latest records across the system. Viewer records are restricted to switches they currently have active permission to access. Failed hardware attempts can be recorded as ERROR. The assistant can retrieve permitted saved history times, commands and results for active switches/devices, but actor identity is removed. It cannot identify or infer who controlled a device or verify hardware execution.
