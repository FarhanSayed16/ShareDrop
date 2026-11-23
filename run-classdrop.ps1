Set-Location -Path $PSScriptRoot
Write-Host "Project folder: $(Get-Location)"
Write-Host "Starting ClassDrop..."
if (-not (Test-Path ".\target\classdrop-1.0.0.jar")) {
    Write-Host "ERROR: target\classdrop-1.0.0.jar not found" -ForegroundColor Red
    exit 1
}
java -jar ".\target\classdrop-1.0.0.jar"
