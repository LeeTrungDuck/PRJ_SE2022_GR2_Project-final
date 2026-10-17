## ESP32 and switch management | DEVICE_MANAGER
Keywords: device switch GPIO hostname IP add edit deactivate thiet bi them sua xoa.
Admin and Operator can add ESP32 devices and switches, rename them, and confirm deactivation. Device IDs begin with ESP; switch IDs begin with SW. A switch belongs to an active ESP32 and uses an integer GPIO between 0 and 255. The database enforces unique hostname strings and unique device/GPIO pairs. Deactivation sets is_active=false and keeps stored rows. The assistant cannot add, rename, deactivate or control hardware.

## Granting and revoking access | PERMISSION_MANAGER
Keywords: permission access grant revoke save draft phan quyen cap thu hoi luu.
Admin and Operator can open Device Permissions and select an active Admin, Operator or Viewer account. Tick View & Control for the desired switches, then Save Permissions. Unticking and saving revokes access by setting is_active=false. Revoke All updates a draft until Save Permissions is selected. Reset restores saved permissions. A single active flag grants combined view/control access. Operator cannot open User Management.
