# Plano de banco e integracao em producao

Este documento descreve uma implantacao minima para o backend Spring Boot, o PostgreSQL e o frontend Angular. Ele nao substitui uma revisao de seguranca, um teste de carga ou a validacao operacional da equipe responsavel pelos dados.

## 1. Arquitetura recomendada

```text
Navegador -> HTTPS -> proxy reverso -> frontend Angular estatico
                                  -> backend Spring Boot
                                          -> PostgreSQL privado
                                          -> storage privado de anexos
```

- O PostgreSQL nao deve ser exposto diretamente a internet.
- O frontend deve usar o mesmo dominio publico ou um dominio explicitamente listado em `CORS_ORIGINS`.
- O backend deve executar com `SPRING_PROFILES_ACTIVE=prod`.
- O proxy reverso deve terminar TLS e encaminhar apenas trafego necessario ao backend.
- Anexos devem ficar em storage persistente, privado e incluido na estrategia de backup.

## 2. Criacao do PostgreSQL

1. Criar uma instancia PostgreSQL gerenciada ou um servidor privado com criptografia em repouso.
2. Criar banco e usuario exclusivos da aplicacao:

```sql
CREATE DATABASE euroespes;
CREATE USER euroespes_app WITH PASSWORD '<senha-gerada-no-secret-manager>';
GRANT CONNECT ON DATABASE euroespes TO euroespes_app;
```

3. Restringir a rede do banco ao backend e ao operador de migracao.
4. Habilitar TLS na conexao do backend e exigir certificados conforme a infraestrutura escolhida.
5. Criar uma rotina separada para migracoes. O usuario da aplicacao nao deve receber privilegios administrativos.

## 3. Schema e migracoes

Antes do primeiro deploy, usar Flyway ou Liquibase para versionar o schema. Nao usar `ddl-auto=update` em producao.

O baseline deve conter as tabelas atuais (`usuarios`, `pacientes`, `produtos`, `estoque`, `movimentacoes_estoque` e `itens_pedido`), chaves estrangeiras, indices e a constraint unica de `pacientes.codigo_identificacao`.

Ordem recomendada:

1. Fazer backup do banco atual, se houver dados.
2. Inspecionar duplicidades de `codigo_identificacao` antes de criar a constraint.
3. Corrigir ou anonimizar duplicidades com aprovacao do responsavel pelos dados.
4. Executar a migracao em homologacao.
5. Validar os testes de integracao.
6. Executar a migracao em producao durante uma janela controlada.
7. Manter o rollback da aplicacao e o backup anterior disponiveis.

## 4. Variaveis de producao

Definir no secret manager ou no ambiente do servico, nunca em arquivo versionado:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://postgres-privado:5432/euroespes?sslmode=require
DB_USERNAME=euroespes_app
DB_PASSWORD=<secret>
JWT_SECRET=<secret-forte-e-aleatorio>
CORS_ORIGINS=https://app.exemplo.com.br
REQUIRE_SSL=true
APP_SEED_ENABLED=false
CREATE_DEFAULT_ADMIN=false
UPLOAD_DIR=/var/lib/euroespes/uploads
```

O primeiro usuario administrador deve ser criado por procedimento controlado, com senha temporaria e troca obrigatoria. Depois, desabilitar o bootstrap automatico.

## 5. Backup e recuperacao

- Configurar backup automatico do PostgreSQL com retencao definida pelo responsavel legal.
- Criptografar backups e restringir quem pode restaura-los.
- Incluir anexos no backup ou usar storage com versionamento e replicacao.
- Definir RPO e RTO antes do go-live.
- Testar restauracao em ambiente isolado antes da liberacao.
- Registrar data, resultado e responsavel por cada teste de restore.

## 6. Deploy e integracao do frontend

1. Executar `mvn clean test` no backend.
2. Aplicar migracoes no PostgreSQL.
3. Iniciar o backend com o profile `prod`.
4. Validar `/actuator/health` e conectividade com o banco.
5. Fazer build do frontend com a URL da API definida pelo proxy ou pelo ambiente.
6. Publicar os arquivos estaticos do Angular no proxy/CDN.
7. Confirmar que o dominio publicado esta em `CORS_ORIGINS`.
8. Validar login, renovacao de sessao, listagem paginada, upload e descarga de anexos.
9. Conferir respostas `401`, `403`, `409` e `429` sem dados sensiveis.

## 7. Gates antes do go-live

- [ ] Nenhum segredo ou dado real em Git, logs, imagens ou arquivos de exemplo.
- [ ] CORS contem somente dominios reais.
- [ ] HTTPS funciona e redireciona trafego HTTP.
- [ ] Banco privado, com TLS e usuario de menor privilegio.
- [ ] Migracao reproduz o schema em ambiente limpo.
- [ ] Backup e restore foram testados.
- [ ] Testes de RBAC, uploads, paginacao e concorrencia passam.
- [ ] Teste de carga executado com volume esperado e margem de crescimento.
- [ ] Monitoramento e alertas ativos.
- [ ] Politica de retencao, exclusao e auditoria de dados aprovada.

## 8. Pendencias de codigo ainda abertas

- Substituir o token JWT no `localStorage` por access token curto e refresh token revogavel em cookie protegido.
- Implementar auditoria persistente de alteracoes de dados, alem do log de requisicoes.
- Adicionar rate limiting distribuido para endpoints sensiveis.
- Adicionar criptografia ou tokenizacao de campos sensiveis conforme a avaliacao de risco LGPD.
- Integrar scanner de malware para anexos.
- Adicionar metricas, alertas, SAST, SCA, secret scanning e teste de carga ao CI/CD.