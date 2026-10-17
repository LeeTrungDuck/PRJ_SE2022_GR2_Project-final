param([int]$Port=18091,[int]$DebugPort=19224)
$ErrorActionPreference='Stop'
$project=(Get-Location).Path
$qa=Join-Path $project 'build/chat-ui-qa'
$chrome='C:/Program Files/Google/Chrome/Application/chrome.exe'
$arguments=@('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--disable-background-networking',"--remote-debugging-port=$DebugPort","`"--user-data-dir=$qa/chrome-profile`"",'about:blank')
$browser=Start-Process -FilePath $chrome -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput "$qa/browser.out.log" -RedirectStandardError "$qa/browser.err.log"
$started=$browser.StartTime
$socket=$null
try {
    $target=$null
    for($i=0;$i -lt 30;$i++) {
        try{$targets=Invoke-RestMethod "http://127.0.0.1:$DebugPort/json" -TimeoutSec 2;$target=$targets|Where-Object {$_.type -eq 'page'}|Select-Object -First 1;if($target){break}}catch{}
        Start-Sleep -Milliseconds 200
    }
    if(-not $target){throw 'Chrome debugging endpoint did not become ready.'}
    $socket=[Net.WebSockets.ClientWebSocket]::new()
    $socket.ConnectAsync([Uri]$target.webSocketDebuggerUrl,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
    $script:nextId=0
    function Cdp([string]$method,$params) {
        $script:nextId++;$current=$script:nextId
        $payload=@{id=$current;method=$method;params=$params}|ConvertTo-Json -Depth 15 -Compress
        $bytes=[Text.Encoding]::UTF8.GetBytes($payload)
        $socket.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
        while($true) {
            $stream=[IO.MemoryStream]::new()
            do {$buffer=[byte[]]::new(65536);$read=$socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),[Threading.CancellationToken]::None).GetAwaiter().GetResult();$stream.Write($buffer,0,$read.Count)}while(-not $read.EndOfMessage)
            $response=[Text.Encoding]::UTF8.GetString($stream.ToArray())|ConvertFrom-Json
            $stream.Dispose()
            if($response.id -eq $current){if($response.error){throw($response.error|ConvertTo-Json -Compress)};return $response.result}
        }
    }
    function Eval([string]$expression) {
        $result=Cdp 'Runtime.evaluate' @{expression=$expression;returnByValue=$true}
        if($result.exceptionDetails){throw($result.exceptionDetails|ConvertTo-Json -Depth 8)}
        return $result.result.value
    }
    function Until([string]$expression) {
        for($attempt=0;$attempt -lt 60;$attempt++){if(Eval $expression){return};Start-Sleep -Milliseconds 150}
        throw "Browser state did not appear: $expression"
    }
    function Shot([string]$name) {
        $shot=Cdp 'Page.captureScreenshot' @{format='png';captureBeyondViewport=$false}
        [IO.File]::WriteAllBytes("$qa/evidence/$name.png",[Convert]::FromBase64String($shot.data))
    }
    Cdp 'Page.enable' @{} | Out-Null
    Cdp 'Runtime.enable' @{} | Out-Null
    Cdp 'Emulation.setDeviceMetricsOverride' @{width=1440;height=1000;deviceScaleFactor=1;mobile=$false} | Out-Null
    Invoke-WebRequest "http://127.0.0.1:$Port/SmartSwitchSystem/__chatfixture?mode=ready" -UseBasicParsing | Out-Null
    Cdp 'Page.navigate' @{url="http://127.0.0.1:$Port/SmartSwitchSystem/__admin?op=reset&actor=U1&view=DEVICE_CONTROL"} | Out-Null
    Until "document.querySelector('.chat-launcher') !== null"
    $initialUrl=Eval 'location.href'
    Shot 'desktop-closed'
    Eval "document.querySelector('.chat-launcher').click()" | Out-Null
    Until "!!document.querySelector('iframe').contentDocument.querySelector('#chat-question')"
    Shot 'desktop-welcome'
    Eval "document.querySelector('iframe').contentDocument.querySelector('#chat-question').value='How do I create a schedule?';document.querySelector('iframe').contentDocument.querySelector('.chat-send-form').requestSubmit()" | Out-Null
    Until "document.querySelector('iframe').contentDocument.body.innerText.includes('daily run time')"
    if((Eval 'location.href') -ne $initialUrl){throw 'Sending chat replaced the dashboard URL.'}
    if(-not(Eval "document.querySelector('.chat-widget').open")){throw 'Sending chat closed the parent widget.'}
    Shot 'desktop-conversation'
    Cdp 'Emulation.setDeviceMetricsOverride' @{width=768;height=1024;deviceScaleFactor=1;mobile=$false} | Out-Null
    Start-Sleep -Milliseconds 200
    Shot 'tablet-conversation'
    Cdp 'Emulation.setDeviceMetricsOverride' @{width=390;height=844;deviceScaleFactor=1;mobile=$true} | Out-Null
    Start-Sleep -Milliseconds 200
    $geometry=Eval "JSON.stringify({width:innerWidth, panel:document.querySelector('.chat-panel').getBoundingClientRect().toJSON(), composer:document.querySelector('iframe').contentDocument.querySelector('.chat-composer').getBoundingClientRect().toJSON()})"
    Shot 'mobile-conversation'
    $parsed=$geometry|ConvertFrom-Json
    if($parsed.width -ne 390 -or $parsed.panel.x -lt 0 -or $parsed.panel.right -gt 390){throw 'Chat panel overflows the actual mobile viewport.'}
    Invoke-WebRequest "http://127.0.0.1:$Port/SmartSwitchSystem/__chatfixture?mode=missing" -UseBasicParsing | Out-Null
    Eval "document.querySelector('iframe').contentDocument.querySelector('.chat-new').click()" | Out-Null
    Until "document.querySelector('iframe').contentDocument.body.innerText.includes('not configured yet')"
    if(-not(Eval "document.querySelector('iframe').contentDocument.querySelector('.chat-send').disabled")){throw 'Missing configuration was presented as ready.'}
    Shot 'mobile-missing-configuration'
    Eval "document.querySelector('.chat-launcher').click()" | Out-Null
    if(Eval "document.querySelector('.chat-widget').open"){throw 'Launcher did not close the widget.'}
    @('VERIFIED: native launcher open/close; chat sends without replacing dashboard; missing configuration disables generation; desktop 1440x1000, tablet 768x1024, actual mobile viewport 390x844.',$geometry)|Set-Content "$qa/browser-validation.log" -Encoding UTF8
    Get-Content "$qa/browser-validation.log"
} finally {
    if($socket){$socket.Dispose()}
    $owned=Get-Process -Id $browser.Id -ErrorAction SilentlyContinue
    if($owned -and $owned.StartTime -eq $started){Stop-Process -Id $browser.Id}
}
