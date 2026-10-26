$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path ".env")) { throw "Missing .env. Run .\scripts\setup-windows.ps1 first." }
$VenvPython = Join-Path $Root ".venv\Scripts\python.exe"
if (-not (Test-Path $VenvPython)) { throw "Missing virtual environment. Run .\scripts\setup-windows.ps1 first." }

Write-Host "Starting JARVIS on 127.0.0.1:8765 (loopback only)."
Write-Host "Keep this window open while JARVIS is running."
& $VenvPython -m uvicorn backend.main:app --host 127.0.0.1 --port 8765
