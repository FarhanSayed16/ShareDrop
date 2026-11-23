@echo off
REM Allow ClassDrop local HTTP port through Windows Firewall (run as Administrator)
set PORT=8080
netsh advfirewall firewall delete rule name="ClassDrop HTTP %PORT%" >nul 2>&1
netsh advfirewall firewall add rule name="ClassDrop HTTP %PORT%" dir=in action=allow protocol=TCP localport=%PORT%
if errorlevel 1 (
  echo Failed. Right-click this file and choose "Run as administrator".
  pause
  exit /b 1
)
echo ClassDrop TCP %PORT% inbound rule added.
pause
