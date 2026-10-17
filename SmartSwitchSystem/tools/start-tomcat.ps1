param([switch]$CheckOnly)

$ErrorActionPreference = 'Stop'
$chatTomcatHome = $env:CATALINA_HOME
if (-not $chatTomcatHome -or -not (Test-Path -LiteralPath (Join-Path $chatTomcatHome 'bin/catalina.bat'))) {
    $chatTomcatHome = Join-Path $env:USERPROFILE 'Desktop/PRJ/tomcat-9.0.113-windows-x64/apache-tomcat-9.0.113'
}
if (-not (Test-Path -LiteralPath (Join-Path $chatTomcatHome 'bin/catalina.bat'))) { throw 'Set CATALINA_HOME to your Tomcat 9 installation.' }
$chatJavaHome = $env:JAVA_HOME
if (-not $chatJavaHome -or -not (Test-Path -LiteralPath (Join-Path $chatJavaHome 'bin/javac.exe'))) {
    $chatJavaHome = 'C:/Program Files/Java/jdk1.8.0_172'
}
if (-not (Test-Path -LiteralPath (Join-Path $chatJavaHome 'bin/javac.exe'))) { throw 'Set JAVA_HOME to your JDK 8 installation.' }

# Read the saved settings even when VS Code was started before setup.
# Never print the API key or put it in a command-line/JVM argument.
foreach ($chatName in 'CHAT_API_BASE_URL','CHAT_API_KEY','CHAT_MODEL','CHAT_TOKEN_FIELD') {
    $chatSaved = [Environment]::GetEnvironmentVariable($chatName, 'User')
    if (-not [string]::IsNullOrWhiteSpace($chatSaved)) { [Environment]::SetEnvironmentVariable($chatName, $chatSaved, 'Process') }
}
$env:JAVA_HOME = $chatJavaHome
$env:CATALINA_HOME = $chatTomcatHome
if (-not $env:CATALINA_BASE -or -not (Test-Path -LiteralPath (Join-Path $env:CATALINA_BASE 'conf/server.xml'))) {
    $env:CATALINA_BASE = $chatTomcatHome
}

if ($CheckOnly) {
    [pscustomobject]@{
        JavaHome=$chatJavaHome
        TomcatHome=$chatTomcatHome
        TomcatBase=$env:CATALINA_BASE
        BaseUrl=$env:CHAT_API_BASE_URL
        Model=$env:CHAT_MODEL
        TokenField=$env:CHAT_TOKEN_FIELD
        ApiKeyPresent= -not [string]::IsNullOrWhiteSpace($env:CHAT_API_KEY)
    } | Format-List
    exit
}

if ([string]::IsNullOrWhiteSpace($env:CHAT_API_KEY) -or [string]::IsNullOrWhiteSpace($env:CHAT_MODEL)) {
    Write-Warning 'Chat is not configured yet. Run the Configure Gemini chatbot task to enter the key locally.'
}
Write-Host 'Starting Tomcat. Stop an already-running Tomcat before using this launch configuration.'
& (Join-Path $chatTomcatHome 'bin/catalina.bat') run
exit $LASTEXITCODE
