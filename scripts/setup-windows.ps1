$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$PythonCommand = Get-Command py -ErrorAction SilentlyContinue
if ($PythonCommand) {
    $Python = "py"
    $PythonArgs = @("-3")
} else {
    $PythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if (-not $PythonCommand) { throw "Python 3.11+ is required. Install Python from python.org, then rerun this script." }
    $Python = "python"
    $PythonArgs = @()
}

& $Python @PythonArgs -c "import sys; assert sys.version_info >= (3,11), 'Python 3.11 or newer is required'; print('Python', sys.version.split()[0])"
if ($LASTEXITCODE -ne 0) { throw "Python version check failed." }

if (-not (Test-Path ".venv")) { & $Python @PythonArgs -m venv .venv }
$VenvPython = Join-Path $Root ".venv\Scripts\python.exe"
& $VenvPython -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "Could not upgrade pip." }
& $VenvPython -m pip install -r backend/requirements.txt
if ($LASTEXITCODE -ne 0) { throw "Could not install JARVIS dependencies." }

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    $Token = (& $VenvPython -c "import secrets; print(secrets.token_urlsafe(48))").Trim()
    $DataKey = (& $VenvPython -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode('ascii'))").Trim()
    $EnvPath = Join-Path $Root ".env"
    $EnvText = [System.IO.File]::ReadAllText($EnvPath)
    $EnvText = $EnvText.Replace("replace_with_at_least_32_random_characters", $Token)
    $EnvText = $EnvText.Replace("replace_with_locally_generated_32_byte_key", $DataKey)
    [System.IO.File]::WriteAllText($EnvPath, $EnvText, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Created .env with fresh local API and encryption keys."
} else {
    Write-Host ".env already exists; leaving it unchanged."
}

Write-Host ""
Write-Host "Setup finished."
Write-Host "1. Install/start Ollama, then run: ollama pull qwen3.5:4b"
Write-Host "2. Start JARVIS with: .\scripts\start-windows.ps1"
Write-Host "3. Open http://127.0.0.1:8765/ in this computer's browser."
Write-Host "4. Keep .env private. Never commit it or expose port 8765 publicly."
