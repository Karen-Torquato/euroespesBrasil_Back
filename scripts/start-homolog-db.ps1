$ErrorActionPreference = 'Stop'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker not found. Install Docker Desktop or Docker Engine before starting the PostgreSQL homologation database."
}

if (-not (Test-Path "$PSScriptRoot\..\.env")) {
    Copy-Item "$PSScriptRoot\..\.env.example" "$PSScriptRoot\..\.env"
}

Set-Location "$PSScriptRoot\.."
docker compose up -d postgres

Write-Host "PostgreSQL homologation is starting."
Write-Host "Use: jdbc:postgresql://localhost:5432/euroespes_homolog"
Write-Host "Credentials are defined in .env"
