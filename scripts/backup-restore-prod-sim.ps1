$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$container = 'euroespes_postgres_prod_sim'
$user = 'euroespes_app'
$database = 'euroespes_prod_sim'
$backupDir = Join-Path $PWD '.backups'
New-Item -ItemType Directory -Force $backupDir | Out-Null
$dump = Join-Path $backupDir "$database.dump"

Write-Host "Criando backup: $dump"
cmd.exe /c "docker exec $container pg_dump -U $user -d $database -Fc > `"$dump`""
if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou' }

$restore = "${database}_restore_test"
docker exec $container dropdb -U $user --if-exists $restore
docker exec $container createdb -U $user $restore
docker cp $dump "${container}:/tmp/$database.dump"
docker exec $container pg_restore -U $user -d $restore --clean --if-exists "/tmp/$database.dump"
if ($LASTEXITCODE -ne 0) { throw 'pg_restore falhou' }

Write-Host "Restore confirmado em $restore"
docker exec $container dropdb -U $user $restore
