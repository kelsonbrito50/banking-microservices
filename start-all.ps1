# Launches all five services, each in its own PowerShell window.
# Prerequisite: docker compose up -d postgres-auth postgres-account postgres-transaction redis kafka
$root = $PSScriptRoot
foreach ($svc in 'auth-service','account-service','transaction-service','notification-service','api-gateway') {
    Start-Process powershell -ArgumentList '-NoExit','-Command',
        "`$Host.UI.RawUI.WindowTitle = '$svc'; Set-Location '$root'; mvn -f $svc/pom.xml spring-boot:run"
}
Write-Host "Launched 5 service windows. Ready when each shows 'Started ...Application'."
Write-Host "Gateway will answer on http://localhost:8080 (a 404/401 in the browser means it's UP)."
