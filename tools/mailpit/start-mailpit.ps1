param(
    [switch]$OpenUi = $true,
    [switch]$ForceDownload
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtimeDir = Join-Path $scriptRoot "runtime"
$downloadDir = Join-Path $runtimeDir "downloads"
$extractDir = Join-Path $runtimeDir "mailpit"
$pidFile = Join-Path $runtimeDir "mailpit.pid"

function Ensure-Directory {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

Ensure-Directory -Path $runtimeDir
Ensure-Directory -Path $downloadDir

$running = Get-Process -Name "mailpit" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($running) {
    Set-Content -Path $pidFile -Value $running.Id
    Write-Host "Mailpit is already running. Web UI: http://127.0.0.1:8025"
    if ($OpenUi) {
        Start-Process "http://127.0.0.1:8025" | Out-Null
    }
    return
}

$exePath = Get-ChildItem -Path $extractDir -Recurse -Filter "mailpit.exe" -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $exePath -or $ForceDownload) {
    $headers = @{ "User-Agent" = "LapPick-Mailpit-Setup" }
    $release = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/axllent/mailpit/releases/latest"
    $asset = $release.assets |
        Where-Object { $_.name -match "windows-amd64.*\.zip$" } |
        Select-Object -First 1

    if (-not $asset) {
        throw "Could not find a Mailpit Windows asset in the latest release."
    }

    $zipPath = Join-Path $downloadDir $asset.name
    if (-not (Test-Path $zipPath) -or $ForceDownload) {
        Invoke-WebRequest -Headers $headers -Uri $asset.browser_download_url -OutFile $zipPath
    }

    if (Test-Path $extractDir) {
        Remove-Item -Path $extractDir -Recurse -Force
    }

    Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force
    $exePath = Get-ChildItem -Path $extractDir -Recurse -Filter "mailpit.exe" |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not $exePath) {
    throw "Mailpit executable was not found after download."
}

$process = Start-Process -FilePath $exePath -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 2

if ($process.HasExited) {
    throw "Mailpit started and exited immediately. Check whether ports 1025 or 8025 are already in use."
}

Set-Content -Path $pidFile -Value $process.Id

Write-Host "Mailpit started."
Write-Host "SMTP: 127.0.0.1:1025"
Write-Host "Web UI: http://127.0.0.1:8025"

if ($OpenUi) {
    Start-Process "http://127.0.0.1:8025" | Out-Null
}
