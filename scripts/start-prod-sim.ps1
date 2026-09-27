$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

docker compose -f docker-compose.prod-sim.yml up -d

docker compose -f docker-compose.prod-sim.yml ps
Write-Host 'PostgreSQL: localhost:55432'
Write-Host 'Redis: localhost:56379'
Write-Host 'Proxy HTTPS: https://localhost:8443'
Write-Host 'O certificado e autoassinado; use -k no curl.'
