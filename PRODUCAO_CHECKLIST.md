# Checklist de Producao Segura

Este documento resume as adequacoes aplicadas para operacao em producao e o que falta configurar no ambiente.

## 1) Segregacao de ambientes

- `application.properties`: configuracao base compartilhada
- `application-dev.properties`: H2 local, seed habilitado, SSL opcional
- `application-prod.properties`: PostgreSQL, seed desabilitado, SSL obrigatorio
- `application-test.properties`: H2 em memoria para testes

Ative em producao com:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
```

## 2) Banco de dados

- Driver PostgreSQL adicionado no `pom.xml`
- Producao usa:
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
- `spring.jpa.hibernate.ddl-auto=validate` no profile `prod`

## 3) Politicas de seguranca aplicadas

- JWT stateless + Spring Security
- Autorizacao por papel (RBAC):
  - DELETE de pacientes: `ROLE_ADMIN`
  - Movimentacao de estoque: `ROLE_ADMIN` ou `ROLE_RECEPCAO`
  - Leitura de pacientes/estoque: `ROLE_ADMIN`, `ROLE_MEDICO`, `ROLE_RECEPCAO`, `ROLE_LEITURA`
  - Criacao/edicao de pacientes: `ROLE_ADMIN`, `ROLE_MEDICO`, `ROLE_RECEPCAO`
- SSL obrigatorio controlado por `app.security.require-ssl`
- Throttling de login (`/api/auth/login`) por IP
- Auditoria de seguranca (sem corpo de request/response)

## 4) Controles de seguranca em startup (prod)

`ProductionSafetyValidator` bloqueia startup em `prod` se detectar:

- `JWT_SECRET` padrao
- `CORS_ORIGINS` vazio ou contendo `localhost`
- senha admin padrao com `CREATE_DEFAULT_ADMIN=true`

## 5) Dados e seed

- Massa fake de pacientes controlada por `app.seed.enabled`
- Em producao: **desabilitada**
- Criacao automatica de admin controlada por `app.seed.create-default-admin`

## 6) Operacao recomendada

- Usar proxy reverso com TLS (Nginx/Traefik/ALB)
- Manter banco e app em rede privada
- Habilitar backup criptografado de banco + uploads
- Armazenar segredos em secret manager (nao em arquivo versionado)
- Integrar monitoramento dos endpoints:
  - `/actuator/health`
  - `/actuator/info`

## 7) Variaveis obrigatorias em producao

Veja `.env.prod.example` como referencia.

Minimo obrigatorio:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET` (forte)
- `CORS_ORIGINS` (sem localhost)
- `REQUIRE_SSL=true`
- `APP_SEED_ENABLED=false`

