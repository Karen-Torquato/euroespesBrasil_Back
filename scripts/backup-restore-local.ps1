param(
    [string]$Container = 'euroespes_postgres_homolog',
    [string]$User = 'euroespes',
    [string]$Database = 'euroespes_homolog'
)

$ErrorActionPreference = 'Stop'
$backupDir = Join-Path $PSScriptRoot '..\.backups'
New-Item -ItemType Directory -Force $backupDir | Out-Null
$dump = Join-Path $backupDir "$Database.dump"

Write-Host "Criando backup em $dump"
cmd.exe /c "docker exec $Container pg_dump -U $User -d $Database -Fc > `"$dump`""
if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou' }

$restoreDatabase = "${Database}_restore_test"
docker exec $Container dropdb -U $User --if-exists $restoreDatabase
docker exec $Container createdb -U $User $restoreDatabase
docker cp $dump "${Container}:/tmp/$Database.dump"
docker exec $Container pg_restore -U $User -d $restoreDatabase --clean --if-exists "/tmp/$Database.dump"
if ($LASTEXITCODE -ne 0) { throw 'pg_restore falhou' }

Write-Host "Restore validado em $restoreDatabase"
docker exec $Container dropdb -U $User $restoreDatabase
