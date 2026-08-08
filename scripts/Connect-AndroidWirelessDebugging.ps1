[CmdletBinding()]
param(
    # The IPv4 address shown on the phone's Wireless debugging page.
    [string]$DeviceIp,

    # The temporary port from "Pair device with pairing code".
    [int]$PairingPort,

    # The current port beside "IP address & Port" on Wireless debugging.
    [int]$ConnectPort,

    # Use this after the computer and phone have already been paired once.
    [switch]$SkipPairing,

    # Override automatic ADB discovery if the SDK is installed elsewhere.
    [string]$AdbPath
)

$ErrorActionPreference = 'Stop'

function Find-Adb {
    param([string]$RequestedPath)

    if ($RequestedPath) {
        if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
            throw "ADB was not found at: $RequestedPath"
        }
        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $candidates = @()
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $candidates += $adbCommand.Source
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe')
    }
    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe')
    }
    $candidates += 'D:\Android\platform-tools\adb.exe'
    $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')

    $found = $candidates |
        Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } |
        Select-Object -First 1
    if (-not $found) {
        throw 'Could not find adb.exe. Install Android SDK Platform-Tools or supply -AdbPath.'
    }
    return (Resolve-Path -LiteralPath $found).Path
}

function Read-RequiredValue {
    param(
        [string]$CurrentValue,
        [string]$Prompt,
        [string]$ValidationMessage,
        [scriptblock]$IsValid
    )

    $value = $CurrentValue
    while (-not (& $IsValid $value)) {
        $value = Read-Host $Prompt
        if (-not (& $IsValid $value)) {
            Write-Warning $ValidationMessage
        }
    }
    return $value
}

function Test-Ipv4Address {
    param([object]$Value)

    $parsedAddress = $null
    return [System.Net.IPAddress]::TryParse([string]$Value, [ref]$parsedAddress) -and
        $parsedAddress.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork
}

function Test-Port {
    param([object]$Value)

    $parsedPort = 0
    return [int]::TryParse([string]$Value, [ref]$parsedPort) -and
        $parsedPort -gt 0 -and
        $parsedPort -le 65535
}

$adb = Find-Adb $AdbPath

$DeviceIp = Read-RequiredValue $DeviceIp `
    'Pixel IP address (from Wireless debugging)' `
    'Enter a valid IPv4 address, for example 192.168.0.105.' `
    { param($value) Test-Ipv4Address $value }

if (-not $SkipPairing) {
    Write-Host ''
    Write-Host 'On the phone: Developer options > Wireless debugging > Pair device with pairing code.' -ForegroundColor Cyan
    $PairingPort = [int](Read-RequiredValue $PairingPort `
        'Temporary pairing port' `
        'Enter the port shown in the pairing-code dialog.' `
        { param($value) Test-Port $value })

    Write-Host "Pairing with ${DeviceIp}:$PairingPort ..." -ForegroundColor Cyan
    & $adb pair "${DeviceIp}:$PairingPort"
    if ($LASTEXITCODE -ne 0) {
        throw "Pairing failed (adb exit code $LASTEXITCODE). Generate a new pairing code on the phone and try again."
    }
} else {
    Write-Host 'Skipping pairing; using the existing pairing record.' -ForegroundColor Yellow
}

Write-Host ''
Write-Host 'Return to the main Wireless debugging page on the phone.' -ForegroundColor Cyan
Write-Host 'Use the port beside "IP address & Port". It is different from the temporary pairing port.' -ForegroundColor Cyan
$ConnectPort = [int](Read-RequiredValue $ConnectPort `
    'ADB connection port' `
    'Enter the port shown beside IP address & Port.' `
    { param($value) Test-Port $value })

$endpoint = "${DeviceIp}:$ConnectPort"
Write-Host "Connecting to $endpoint ..." -ForegroundColor Cyan
& $adb connect $endpoint
if ($LASTEXITCODE -ne 0) {
    throw "Connection failed (adb exit code $LASTEXITCODE). Confirm Wireless debugging is on and use its current IP address & Port."
}

$devices = & $adb devices -l
$escapedEndpoint = [regex]::Escape($endpoint)
$connected = $devices | Where-Object { $_ -match "^$escapedEndpoint\s+device\b" }
if (-not $connected) {
    Write-Host ($devices -join [Environment]::NewLine)
    throw "ADB did not report $endpoint as a connected device."
}

Write-Host ''
Write-Host 'Connected successfully:' -ForegroundColor Green
$connected | ForEach-Object { Write-Host $_ }
Write-Host "`nUseful command: & '$adb' -s '$endpoint' shell getprop ro.product.model"
