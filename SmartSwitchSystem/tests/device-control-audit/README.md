# Device Control audit

Runs the production Java controllers, DAO mapping/bindings, and JSPs in an isolated Tomcat application. Only `utills.DBConnection` is replaced in the **generated audit app** by in-memory JDBC fixtures. ESP32 HTTP endpoints use an actual local HTTP server. No live database or physical device is used. This does not establish SQL Server transaction/constraint behavior or physical relay operation.

Do not copy the fixture classes into the real application. The runner does not edit production sources and deliberately omits the SQL Server driver from the audit app. The fixture endpoint is only for the loopback audit server.

From the project directory, using Windows PowerShell:

```powershell
& .\tests\device-control-audit\run.ps1
```

If the machine blocks unsigned local scripts, run the reviewed local script without changing execution policy:

```powershell
$auditScript = (Resolve-Path .\tests\device-control-audit\run.ps1).Path
& ([scriptblock]::Create([IO.File]::ReadAllText($auditScript)))
```

Run the script-block form from the project directory so the runner can resolve its fixture folder.

The runner supports `-TomcatHome`, `-JavaHome`, `-Port` and `-KeepServer`. Without `-KeepServer` it stops only its own server. The Java audit exits 1 when acceptance checks fail; the wrapper prints `AUDIT_EXIT`, still runs HardwareClient probes, then reports failure to its caller.

Results: `build/device-control-audit/audit.log`, `evidence/results.tsv`, saved rendered HTML, and `hardware-client.log`. Failing cases describe current defects; they are not inverted to make the suite pass. Fixtures contain 45 active switches on two ESP32 devices, one inactive switch, one switch on an inactive ESP32, active/revoked Viewer permissions, stale hardware, failed HTTP, malformed state, a false acknowledgement and synchronized duplicate requests.
