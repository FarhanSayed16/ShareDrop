@echo off
REM Allow ClassDrop local HTTP port through Windows Firewall (run as Administrator)
REM profile=any is required: Windows Mobile Hotspot is often classified as Public
set PORT=8080
netsh advfirewall firewall delete rule name="ClassDrop HTTP %PORT%" >nul 2>&1
netsh advfirewall firewall add rule name="ClassDrop HTTP %PORT%" dir=in action=allow protocol=TCP localport=%PORT% profile=any
if errorlevel 1 (
  echo Failed. Right-click this file and choose "Run as administrator".
  echo Or run:
  echo   netsh advfirewall firewall add rule name="ClassDrop HTTP %PORT%" dir=in action=allow protocol=TCP localport=%PORT% profile=any
  pause
  exit /b 1
)
echo ClassDrop TCP %PORT% inbound rule added for ALL profiles ^(Private/Public/Hotspot^).
pause
