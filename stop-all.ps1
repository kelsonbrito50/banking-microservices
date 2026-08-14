# Stops every service by killing whatever listens on the five service ports.
foreach ($port in 8080,8081,8082,8083,8084) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        $conn.OwningProcess | Select-Object -Unique | ForEach-Object {
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
            Write-Host "stopped process $_ (port $port)"
        }
    }
}
Write-Host "Services stopped. Infra keeps running; use 'docker compose down' to stop it too."
