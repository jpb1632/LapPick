$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtimeDir = Join-Path $scriptRoot "runtime"
$pidFile = Join-Path $runtimeDir "mailpit.pid"
$stopped = $false

if (Test-Path $pidFile) {
    $savedPid = Get-Content -Path $pidFile | Select-Object -First 1
    if ($savedPid) {
        $savedProcess = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
        if ($savedProcess) {
            Stop-Process -Id $savedPid -Force
            $stopped = $true
        }
    }

    Remove-Item -Path $pidFile -Force
}

$otherProcesses = Get-Process -Name "mailpit" -ErrorAction SilentlyContinue
if ($otherProcesses) {
    $otherProcesses | Stop-Process -Force
    $stopped = $true
}

if ($stopped) {
    Write-Host "Mailpit stopped."
} else {
    Write-Host "Mailpit is not running."
}
