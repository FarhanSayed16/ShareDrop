# Enable Windows Mobile Hotspot via WinRT (no admin required in most cases)
# Usage: powershell -ExecutionPolicy Bypass -File scripts\enable-mobile-hotspot.ps1

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null

function Await-WinRT($WinRtTask, [Type]$ResultType) {
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object {
            $_.Name -eq 'AsTask' -and
            $_.GetParameters().Count -eq 1 -and
            $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
        } | Select-Object -First 1
    if (-not $asTask) { throw "Could not find WindowsRuntime AsTask helper" }
    $netTask = $asTask.MakeGenericMethod($ResultType).Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    return $netTask.Result
}

try {
    $profile = [Windows.Networking.Connectivity.NetworkInformation,Windows.Networking.Connectivity,ContentType=WindowsRuntime]::GetInternetConnectionProfile()
    if (-not $profile) {
        Write-Host "No connection profile. Turn Wi-Fi adapter ON (need not be online), or create a Loopback adapter."
        Write-Host "Opening Mobile Hotspot settings as fallback..."
        Start-Process "ms-settings:network-mobilehotspot"
        exit 2
    }

    $tm = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager,Windows.Networking.NetworkOperators,ContentType=WindowsRuntime]::CreateFromConnectionProfile($profile)
    $state = $tm.TetheringOperationalState
    Write-Host "Current Mobile Hotspot state: $state"

    if ($state -eq "On" -or [int]$state -eq 1) {
        Write-Host "Hotspot already ON."
        exit 0
    }

    Write-Host "Starting Mobile Hotspot..."
    $result = Await-WinRT ($tm.StartTetheringAsync()) ([Windows.Networking.NetworkOperators.NetworkOperatorTetheringOperationResult])
    Write-Host "Result: $($result.Status) $($result.AdditionalErrorMessage)"

    if ($result.Status -eq "Success") {
        Write-Host "Hotspot started. In ClassDrop set Mode=HOTSPOT and wait for OFFLINE READY."
        exit 0
    }

    Write-Host "Could not start automatically. Opening settings..."
    Start-Process "ms-settings:network-mobilehotspot"
    exit 1
}
catch {
    Write-Host "Error: $($_.Exception.Message)"
    Start-Process "ms-settings:network-mobilehotspot"
    exit 1
}
