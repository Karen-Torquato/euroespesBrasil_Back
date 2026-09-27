# Operacao de producao

## Deploy

1. Criar secrets fora do Git: `DB_PASSWORD`, `JWT_SECRET`, `PII_ENCRYPTION_KEY`.
2. Configurar `SPRING_PROFILES_ACTIVE=prod`, `DB_URL` com `sslmode=require`, `CORS_ORIGINS` HTTPS e seeds desabilitados.
3. Publicar a imagem/artefato do backend.
4. Executar Flyway antes de liberar trafego.
5. Fazer smoke test de login, leitura, alteracao, upload, download e anonimização.

## Rollback

- Nao reverta migracoes Flyway destrutivamente.
- Retire o trafego da versao atual.
- Restaure a versao anterior do artefato.
- Se houver mudanca de schema, restaure backup somente com procedimento aprovado.
- Registre request ID, horario e impacto.

## Backup e restore local

Subir PostgreSQL:

```powershell
docker compose up -d postgres
```

Backup:

```powershell
New-Item -ItemType Directory -Force .backups | Out-Null
docker exec euroespes_postgres_homolog pg_dump -U euroespes -d euroespes_homolog -Fc > .backups/euroespes_homolog.dump
```

Restore em banco descartavel:

```powershell
docker exec -it euroespes_postgres_homolog createdb -U euroespes euroespes_restore_test
docker cp .backups/euroespes_homolog.dump euroespes_postgres_homolog:/tmp/euroespes_homolog.dump
docker exec euroespes_postgres_homolog pg_restore -U euroespes -d euroespes_restore_test --clean --if-exists /tmp/euroespes_homolog.dump
```

Remover o banco de teste depois da validação:

```powershell
docker exec euroespes_postgres_homolog dropdb -U euroespes euroespes_restore_test
```

## Carga local

Use um gerador HTTP aprovado no ambiente de homologacao, nunca em producao sem janela e limites definidos. Registre concorrencia, latencia p95/p99, erros 4xx/5xx e saturacao do banco.

## LGPD

- `DELETE /api/pacientes/{id}/anonimizar` anonimiza e remove anexo.
- `GET /api/pacientes/{id}/exportar` exporta dados para operador ADMIN e registra auditoria.
- A chave `PII_ENCRYPTION_KEY` deve ser permanente; perder a chave impede a leitura dos dados.
- Retencao, base legal, canal do titular, DPO e contratos de operadores precisam de aprovacao externa.
