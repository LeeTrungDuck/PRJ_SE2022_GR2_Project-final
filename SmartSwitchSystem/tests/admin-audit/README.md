# ADMIN acceptance audit

Tests A1-A10 from the user's ADMIN checklist through production Servlet routing, controllers, DAO SQL bindings/mapping and rendered JSPs in an isolated Tomcat.

The only replaced production dependency is `utills.DBConnection` in the **generated audit application**. It returns in-memory JDBC connections. A loopback HTTP server simulates ESP32. No SQL Server driver is packaged in the audit application. Never copy these fixtures into the real application.

The original Admin is seeded as U1; another active Admin is U4. A1 checks all active switches on active ESP32 devices. Inactive records are intentionally excluded, matching the existing DAO contract. Unique hostname, device/GPIO, username, email, phone and user/switch fixture rules were compared with live SQL Server metadata using SELECT. This does not verify real SQL Server writes, locking, rollback or hardware behavior.

Run from the project directory:

```powershell
& ([scriptblock]::Create([IO.File]::ReadAllText((Join-Path (Get-Location).Path 'tests/admin-audit/run.ps1'))))
```

Optional parameters: `-TomcatHome`, `-JavaHome`, `-Port` (default 18090), `-KeepServer`. By default the runner stops only its own Tomcat process. Existing acceptance failures produce exit 1; they are not inverted into passing expectations. HardwareClient probes run even when acceptance failures remain.

Evidence: `build/admin-audit/audit.log`, `evidence/results.tsv`, saved HTML and server logs. The workflow also compiles all production Java sources and runs the existing eight HardwareClient checks.

No production code is modified. A false HTTP-200 acknowledgement and the same IP written with/without `http://` are deliberately injected boundary cases, not claims about the live firmware/data.
