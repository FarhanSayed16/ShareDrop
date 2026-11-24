Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

Write-Host "================================================"
Write-Host "  ClassDrop - Offline Classroom Host"
Write-Host "================================================"
Write-Host "Project folder: $(Get-Location)"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: Java 17+ not found in PATH." -ForegroundColor Red
    exit 1
}

$jar = Join-Path $PSScriptRoot "target\classdrop-1.0.0.jar"
if (-not (Test-Path $jar)) {
    Write-Host "JAR missing — building..." -ForegroundColor Yellow
    $mvn = $null
    foreach ($c in @(
        ".\.tools\apache-maven-3.9.6\bin\mvn.cmd",
        ".\.tools\apache-maven-3.9.9\bin\mvn.cmd"
    )) {
        if (Test-Path $c) { $mvn = (Resolve-Path $c).Path; break }
    }
    if (-not $mvn) {
        $cmd = Get-Command mvn -ErrorAction SilentlyContinue
        if ($cmd) { $mvn = $cmd.Source }
    }
    if (-not $mvn) {
        Write-Host "ERROR: Maven not found; cannot build JAR." -ForegroundColor Red
        exit 1
    }
    & $mvn -f (Join-Path $PSScriptRoot "pom.xml") -q package "-DskipTests"
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $jar)) {
        Write-Host "ERROR: Build failed." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Starting ClassDrop..."
Write-Host "TIP: Offline = Mobile Hotspot + OFFLINE READY + Fix Firewall"
Write-Host "TIP: Local check http://127.0.0.1:8080"
java -jar $jar @args
