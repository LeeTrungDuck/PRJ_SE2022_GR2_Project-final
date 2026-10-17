param(
    [string]$TomcatHome = 'C:\Users\tagor\Desktop\PRJ\tomcat-9.0.113-windows-x64\apache-tomcat-9.0.113',
    [string]$JavaHome = 'C:\Program Files\Java\jdk1.8.0_172',
    [int]$Port = 18091,
    [switch]$KeepServer
)
$ErrorActionPreference = 'Stop'
$fixtureRoot = if($PSScriptRoot) { $PSScriptRoot } else { Join-Path (Get-Location).Path 'tests\chat-audit' }
$project = [IO.Path]::GetFullPath((Join-Path $fixtureRoot '..\..'))
$qa = Join-Path $project 'build\chat-ui-qa'
$base = Join-Path $qa 'tomcat'
$app = Join-Path $base 'webapps\SmartSwitchSystem'
$classes = Join-Path $app 'WEB-INF\classes'
$servlet = Join-Path $TomcatHome 'lib\servlet-api.jar'
@($qa,$base,"$base\conf","$base\logs","$base\temp","$base\work",$app,$classes,"$app\WEB-INF\lib","$qa\production-classes","$qa\evidence") | ForEach-Object { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
$sources = @(Get-ChildItem -LiteralPath "$project\src\java" -Recurse -Filter *.java | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
[IO.File]::WriteAllLines("$qa\sources.txt",$sources,[Text.Encoding]::ASCII)
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -cp $servlet -d "$qa\production-classes" "@$qa\sources.txt"
if($LASTEXITCODE -ne 0) { throw 'Production compilation failed.' }
Copy-Item -Path "$qa\production-classes\*" -Destination $classes -Recurse -Force
Copy-Item -LiteralPath "$project\web\web","$project\web\css","$project\web\img" -Destination $app -Recurse -Force
Copy-Item -LiteralPath "$project\web\WEB-INF\web.xml" -Destination "$app\WEB-INF\web.xml" -Force
Copy-Item -LiteralPath "$project\lib\jstl\jstl-api.jar","$project\lib\jstl\jstl-impl.jar" -Destination "$app\WEB-INF\lib" -Force
# This application deliberately contains no SQL Server driver.
if(Test-Path "$app\WEB-INF\lib\sqljdbc4.jar") { throw 'Audit application must not contain a real JDBC driver.' }
$fixtureSources = @(Get-ChildItem -LiteralPath $fixtureRoot -Recurse -Filter *.java | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
$fixtureSources += '"' + (Join-Path $project 'tests\admin-audit\adminaudit\AdminFixture.java').Replace('\','/') + '"'
$fixtureSources += '"' + (Join-Path $project 'tests\admin-audit\utills\DBConnection.java').Replace('\','/') + '"'
Copy-Item -LiteralPath "$project\web\WEB-INF\chat" -Destination "$app\WEB-INF" -Recurse -Force
[IO.File]::WriteAllLines("$qa\fixture-sources.txt",$fixtureSources,[Text.Encoding]::ASCII)
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -cp "$classes;$servlet" -d $classes "@$qa\fixture-sources.txt"
if($LASTEXITCODE -ne 0) { throw 'Audit compilation failed.' }
Get-ChildItem -LiteralPath "$TomcatHome\conf" -File | Copy-Item -Destination "$base\conf" -Force
$serverXml = @"
<Server port="-1"><Service name="Audit"><Connector address="127.0.0.1" port="$Port" protocol="HTTP/1.1" connectionTimeout="20000"/><Engine name="Audit" defaultHost="localhost"><Host name="localhost" appBase="webapps" unpackWARs="false" autoDeploy="false"/></Engine></Service></Server>
"@
[IO.File]::WriteAllText("$base\conf\server.xml",$serverXml,[Text.Encoding]::UTF8)
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$Port)
try { $listener.Start() } finally { $listener.Stop() }
$arguments = @("`"-Dcatalina.home=$TomcatHome`"","`"-Dcatalina.base=$base`"","`"-Djava.io.tmpdir=$base\temp`"",'-cp',"`"$TomcatHome\bin\bootstrap.jar;$TomcatHome\bin\tomcat-juli.jar`"",'org.apache.catalina.startup.Bootstrap','start')
$process = Start-Process -FilePath "$JavaHome\bin\java.exe" -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput "$qa\server.out.log" -RedirectStandardError "$qa\server.err.log"
$process.Id | Set-Content "$qa\server.pid"
$process.StartTime.ToUniversalTime().ToString('o') | Set-Content "$qa\server.start"
try {
    $ready = $false
    for($attempt=0;$attempt -lt 40;$attempt++) {
        try { $response=Invoke-WebRequest "http://127.0.0.1:$Port/SmartSwitchSystem/__chatfixture" -UseBasicParsing -TimeoutSec 2; if($response.StatusCode -eq 200) { $ready=$true;break } } catch { if($process.HasExited) { throw 'Audit Tomcat exited. See server.err.log.' }; Start-Sleep -Milliseconds 400 }
    }
    if(-not $ready) { throw 'Audit Tomcat did not become ready.' }
    & "$JavaHome\bin\java.exe" -cp "$classes;$servlet" chataudit.ChatHttpSmoke "http://127.0.0.1:$Port/SmartSwitchSystem" "$qa\evidence" 2>&1 | Tee-Object -FilePath "$qa\audit.log"
    $auditExit = $LASTEXITCODE
    & "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp "$qa\production-classes" -d "$qa\production-classes" "$project\tests\utills\HardwareClientTest.java"
    if($LASTEXITCODE -ne 0) { throw 'HardwareClientTest compilation failed.' }
    & "$JavaHome\bin\java.exe" -cp "$qa\production-classes" utills.HardwareClientTest 2>&1 | Tee-Object -FilePath "$qa\hardware-client.log"
    if($LASTEXITCODE -ne 0) { throw 'HardwareClient probes failed. See hardware-client.log.' }
    Write-Output "AUDIT_EXIT=$auditExit; EVIDENCE=$qa\evidence; SERVER_PID=$($process.Id)"
} finally {
    if(-not $KeepServer) {
        $ownedProcess = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
        if($ownedProcess -and $ownedProcess.StartTime -eq $process.StartTime) { Stop-Process -Id $process.Id }
    }
}
if($auditExit -ne 0) { throw "Chat integration checks failed (exit $auditExit). See $qa\audit.log and evidence\results.tsv." }
