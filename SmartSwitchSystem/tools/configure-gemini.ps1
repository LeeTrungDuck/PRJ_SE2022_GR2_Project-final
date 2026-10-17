param([switch]$CheckOnly)

$ErrorActionPreference = 'Stop'
$chatRoot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$chatStatusPath = Join-Path $chatRoot 'nbproject/private/chat-setup-status.json'
$chatBaseUrl = 'https://generativelanguage.googleapis.com/v1beta/openai'
$chatDefaultModel = 'gemini-3.5-flash-lite'
$chatTokenField = 'max_tokens'

if ($CheckOnly) {
    $chatKey = [Environment]::GetEnvironmentVariable('CHAT_API_KEY', 'User')
    [pscustomobject]@{
        BaseUrl = [Environment]::GetEnvironmentVariable('CHAT_API_BASE_URL', 'User')
        Model = [Environment]::GetEnvironmentVariable('CHAT_MODEL', 'User')
        TokenField = [Environment]::GetEnvironmentVariable('CHAT_TOKEN_FIELD', 'User')
        ApiKeyPresent = -not [string]::IsNullOrWhiteSpace($chatKey)
    } | Format-List
    exit
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
[Windows.Forms.Application]::EnableVisualStyles()

$chatForm = New-Object Windows.Forms.Form
$chatForm.Text = 'SmartSwitchSystem - Gemini setup'
$chatForm.ClientSize = New-Object Drawing.Size(570, 385)
$chatForm.StartPosition = 'CenterScreen'
$chatForm.FormBorderStyle = 'FixedDialog'
$chatForm.MaximizeBox = $false
$chatForm.MinimizeBox = $false

$chatInstructions = New-Object Windows.Forms.Label
$chatInstructions.SetBounds(20, 18, 530, 55)
$chatInstructions.Text = 'Enter your Gemini API key here, not in the conversation. The key is saved only in your Windows user environment. Restart Tomcat after saving.'
$chatForm.Controls.Add($chatInstructions)

$chatModelLabel = New-Object Windows.Forms.Label
$chatModelLabel.SetBounds(20, 80, 525, 20)
$chatModelLabel.Text = 'Model ID (must be available to your Google AI Studio project)'
$chatForm.Controls.Add($chatModelLabel)
$chatModelInput = New-Object Windows.Forms.TextBox
$chatModelInput.SetBounds(20, 103, 530, 25)
$chatExistingBase = [Environment]::GetEnvironmentVariable('CHAT_API_BASE_URL', 'User')
$chatExistingModel = [Environment]::GetEnvironmentVariable('CHAT_MODEL', 'User')
$chatModelInput.Text = if ($chatExistingBase -eq $chatBaseUrl -and $chatExistingModel) { $chatExistingModel } else { $chatDefaultModel }
$chatForm.Controls.Add($chatModelInput)

$chatKeyLabel = New-Object Windows.Forms.Label
$chatKeyLabel.SetBounds(20, 142, 530, 20)
$chatKeyLabel.Text = 'Gemini API key (hidden; leave empty to keep an existing Gemini key)'
$chatForm.Controls.Add($chatKeyLabel)
$chatKeyInput = New-Object Windows.Forms.TextBox
$chatKeyInput.SetBounds(20, 165, 530, 25)
$chatKeyInput.UseSystemPasswordChar = $true
$chatKeyInput.MaxLength = 4096
$chatForm.Controls.Add($chatKeyInput)

$chatTest = New-Object Windows.Forms.CheckBox
$chatTest.SetBounds(20, 204, 530, 25)
$chatTest.Text = 'Test a short greeting before saving (uses Gemini API quota)'
$chatTest.Checked = $true
$chatForm.Controls.Add($chatTest)

$chatFeedback = New-Object Windows.Forms.Label
$chatFeedback.SetBounds(20, 239, 530, 65)
$chatFeedback.Text = 'Endpoint: ' + $chatBaseUrl + '/chat/completions'
$chatForm.Controls.Add($chatFeedback)
$chatSave = New-Object Windows.Forms.Button
$chatSave.SetBounds(300, 325, 120, 35)
$chatSave.Text = 'Save settings'
$chatForm.Controls.Add($chatSave)
$chatCancel = New-Object Windows.Forms.Button
$chatCancel.SetBounds(430, 325, 120, 35)
$chatCancel.Text = 'Close'
$chatCancel.Add_Click({ $chatForm.Close() })
$chatForm.Controls.Add($chatCancel)
$chatForm.AcceptButton = $chatSave
$chatForm.CancelButton = $chatCancel

$chatSave.Add_Click({
    $chatSecret = $chatKeyInput.Text.Trim()
    $chatModel = $chatModelInput.Text.Trim()
    if (-not $chatSecret -and [Environment]::GetEnvironmentVariable('CHAT_API_BASE_URL', 'User') -eq $chatBaseUrl) {
        $chatSecret = [Environment]::GetEnvironmentVariable('CHAT_API_KEY', 'User')
    }
    if ([string]::IsNullOrWhiteSpace($chatSecret) -or $chatSecret -match '[\s\x00-\x1f\x7f]' -or $chatSecret.Length -gt 4096) {
        $chatFeedback.Text = 'Enter a valid API key. Do not paste it into the conversation.'
        return
    }
    if ($chatModel.Length -gt 128 -or $chatModel -notmatch '^gemini-[A-Za-z0-9._-]+$') {
        $chatFeedback.Text = 'Enter a Gemini model ID, for example gemini-3.5-flash-lite.'
        return
    }
    $chatSave.Enabled = $false
    $chatCancel.Enabled = $false
    $chatFeedback.Text = 'Checking configuration...'
    $chatForm.Refresh()
    $chatVerified = $false
    try {
        if ($chatTest.Checked) {
            $chatPayload = @{
                model = $chatModel
                stream = $false
                max_tokens = 1024
                messages = @(
                    @{role='system';content='Reply briefly. This is a connection check.'},
                    @{role='user';content='Say hello in one short sentence.'}
                )
            } | ConvertTo-Json -Depth 5 -Compress
            try {
                $chatReply = Invoke-RestMethod -Method Post -Uri ($chatBaseUrl + '/chat/completions') -Headers @{Authorization=('Bearer ' + $chatSecret)} -ContentType 'application/json; charset=utf-8' -Body $chatPayload -TimeoutSec 20 -MaximumRedirection 0
                $chatAnswer = $chatReply.choices[0].message.content
                if (-not ($chatAnswer -is [string]) -or [string]::IsNullOrWhiteSpace($chatAnswer) -or $chatAnswer.Length -gt 6000) {
                    $chatFeedback.Text = 'Gemini returned no usable text. Check the model and retry. Settings were not saved.'
                    return
                }
                $chatVerified = $true
            } catch {
                $chatHttpCode = 0
                if ($_.Exception.Response) { $chatHttpCode = [int]$_.Exception.Response.StatusCode }
                $chatFeedback.Text = switch ($chatHttpCode) {
                    400 { 'Gemini rejected the request. Check your API key and model. Settings were not saved.' }
                    401 { 'API key was rejected. Settings were not saved.' }
                    403 { 'API access was denied. Check the Google AI Studio project. Settings were not saved.' }
                    404 { 'Model not available. Enter a model available to your project. Settings were not saved.' }
                    429 { 'Gemini quota or rate limit reached. Check your project quota. Settings were not saved.' }
                    default { 'Could not verify Gemini. Check your connection and retry. Settings were not saved.' }
                }
                return
            }
        }
        $chatNewSettings = @{CHAT_API_BASE_URL=$chatBaseUrl;CHAT_MODEL=$chatModel;CHAT_TOKEN_FIELD=$chatTokenField;CHAT_API_KEY=$chatSecret}
        $chatPrevious = @{}
        foreach ($chatName in $chatNewSettings.Keys) { $chatPrevious[$chatName] = [Environment]::GetEnvironmentVariable($chatName, 'User') }
        try {
            foreach ($chatName in $chatNewSettings.Keys) { [Environment]::SetEnvironmentVariable($chatName, $chatNewSettings[$chatName], 'User') }
        } catch {
            foreach ($chatName in $chatPrevious.Keys) { [Environment]::SetEnvironmentVariable($chatName, $chatPrevious[$chatName], 'User') }
            throw
        }
        # This status file contains no key, response body, question, or authorization header.
        [IO.Directory]::CreateDirectory((Split-Path -Parent $chatStatusPath)) | Out-Null
        @{state='saved';verified=$chatVerified;provider='Gemini';model=$chatModel;utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath $chatStatusPath -Encoding UTF8
        $chatKeyInput.Clear()
        $chatFeedback.Text = if ($chatVerified) { 'Saved. Gemini replied successfully. Restart Tomcat to activate the settings.' } else { 'Saved without a connection check. Restart Tomcat, then test the assistant.' }
    } catch {
        $chatFeedback.Text = 'Could not complete setup. Check Windows user-environment permissions and retry.'
    } finally {
        $chatSecret = $null
        $chatNewSettings = $null
        $chatPrevious = $null
        $chatReply = $null
        $chatSave.Enabled = $true
        $chatCancel.Enabled = $true
    }
})

try { [void]$chatForm.ShowDialog() } finally { $chatKeyInput.Clear(); $chatForm.Dispose() }
