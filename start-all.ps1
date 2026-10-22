# Launches all five services, each in its own PowerShell window.
# Prerequisites: .env filled in (see .env.example), then
#   docker compose up -d postgres-auth postgres-account postgres-transaction redis kafka
$root = $PSScriptRoot

# Load JWT_SECRET and POSTGRES_PASSWORD from .env; the five child windows inherit them.
$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw "Missing .env. Copy .env.example to .env and fill in both values." }
Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
}
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
if (-not $env:JWT_SECRET -or -not $env:DB_PASSWORD) { throw "JWT_SECRET and POSTGRES_PASSWORD must both be set in .env." }
foreach ($svc in 'auth-service','account-service','transaction-service','notification-service','api-gateway') {
    Start-Process powershell -ArgumentList '-NoExit','-Command',
        "`$Host.UI.RawUI.WindowTitle = '$svc'; Set-Location '$root'; mvn -f $svc/pom.xml spring-boot:run"
}
Write-Host "Launched 5 service windows. Ready when each shows 'Started ...Application'."
Write-Host "Gateway will answer on http://localhost:8080 (a 404/401 in the browser means it's UP)."
