# Plano de execucao: seguranca, conformidade e escala

## Objetivo

Levar o sistema a uma condicao verificavel de producao, com protecao de dados de pacientes, integracao estavel entre Angular e Spring Boot, capacidade de crescimento horizontal e operacao recuperavel.

O sistema so pode ser declarado pronto quando todos os gates obrigatorios tiverem evidencia: codigo revisado, testes passando, migracao reproduzida, backup restaurado e ambiente de homologacao validado.

## Fase 0 - Inventario e contrato

**Dependencias:** nenhuma.

1. Congelar o contrato atual dos endpoints e registrar mudancas pendentes.
2. Verificar `git status` dos dois repositorios e preservar alteracoes locais.
3. Levantar entidades, relacionamentos, dados sensiveis, uploads e perfis de acesso.
4. Definir RPO, RTO, retencao de dados, responsavel pelo tratamento e perfis autorizados.

**Aceite:** matriz de dados, matriz de permissoes e contrato API versionado/documentado.

## Fase 1 - Seguranca do backend

**Dependencias:** Fase 0.

1. Remover defaults inseguros e validar tamanho/qualidade do `JWT_SECRET` em `prod`.
2. Manter CORS restrito por profile, sem wildcard local em producao.
3. Reduzir access token para curta duracao.
4. Implementar refresh token aleatorio, persistido, rotativo e revogavel.
5. Entregar refresh em cookie `HttpOnly`, `Secure`, `SameSite` e adicionar protecao CSRF adequada.
6. Verificar usuario ativo em login e refresh.
7. Criar endpoint de logout que revogue a sessao.
8. Adicionar rate limiting distribuido para login, upload, download, consultas e operacoes destrutivas.
9. Limitar e validar todos os parametros de pagina, tamanho e ordenacao.
10. Manter mensagens de erro genericas e logs sem PII.

**Aceite:** testes de 401, 403, 429, CORS, token expirado, token revogado, refresh rotativo e logout.

## Fase 2 - Integridade de dados e LGPD

**Dependencias:** Fase 0 e definicao juridica de retencao.

1. Criar constraint unica para identificadores de paciente e tratar conflito como 409.
2. Substituir consultas de duplicidade que carreguem tabelas inteiras.
3. Validar CPF, telefone e campos de entrada conforme regras de negocio.
4. Criar auditoria persistente de CREATE, UPDATE, DELETE, upload, download e estoque.
5. Registrar usuario, acao, entidade, id, timestamp, request id e valores protegidos.
6. Definir exclusao logica, anonimização ou exclusao fisica conforme obrigacao legal.
7. Criar politica de retencao e processo para solicitacoes do titular.
8. Criptografar dados em repouso, backups e anexos; manter chaves fora do Git.

**Aceite:** auditoria consultavel, teste de autorizacao, teste de anonimização e evidencias de que logs/respostas nao expõem PII indevida.

## Fase 3 - Banco, schema e migracoes

**Dependencias:** Fase 2 e inventario do schema atual.

1. Adicionar Flyway ou Liquibase.
2. Criar baseline das tabelas atuais e chaves estrangeiras.
3. Criar migracoes para indices, constraints e tabelas de auditoria/sessoes.
4. Remover `ddl-auto=update` de todos os ambientes de deploy.
5. Usar `validate` em producao.
6. Criar usuario de migracao separado do usuario da aplicacao.
7. Testar a migracao em banco vazio e em copia anonimizada dos dados reais.

**Aceite:** schema reproduzivel a partir das migracoes e rollback documentado para cada release.

## Fase 4 - Escala da API e do frontend

**Dependencias:** Fase 3.

1. Tornar paginação obrigatoria em listas grandes.
2. Adicionar filtros por status, nome, codigo e datas.
3. Usar allowlist para ordenacao e limites de pagina.
4. Criar indices para filtros e ordenacoes reais.
5. Atualizar o Angular para consumir `Page<T>` com total de itens e navegacao de paginas.
6. Evitar `findAll()` sem limite em endpoints de negocio.
7. Configurar pool de conexoes, timeouts e limites de request.
8. Testar concorrencia de estoque, identificadores e atualizacoes.
9. Preparar storage compartilhado para anexos em execucao com varias instancias.

**Aceite:** teste de carga com volume esperado, latencia alvo definida e nenhuma consulta sem limite nos endpoints publicos de listagem.

## Fase 5 - Uploads e arquivos

**Dependencias:** Fase 2 e storage de producao.

1. Manter whitelist de extensao, MIME real, magic bytes e tamanho.
2. Adicionar hash SHA-256 e verificacao de integridade.
3. Adicionar antivirus/ClamAV ou servico equivalente.
4. Salvar fora da pasta publica, com nome aleatorio e acesso autorizado.
5. Garantir compensacao quando o arquivo for salvo e o banco falhar.
6. Criar limpeza de arquivos orfaos e politica de retencao.

**Aceite:** testes de arquivo falso, arquivo malicioso, path traversal, arquivo grande e falha no meio da transacao.

## Fase 6 - Observabilidade e CI/CD

**Dependencias:** Fases 1 a 5.

1. Adicionar logs estruturados com `requestId` e mascaramento de PII.
2. Publicar metricas de latencia, erros, autenticacao, banco, fila e storage.
3. Configurar health, readiness e liveness reais.
4. Criar alertas para 5xx, 401/403 anormais, 429, latencia e espaco.
5. Executar no CI testes unitarios, integracao, SAST, SCA, secret scanning e build.
6. Executar OWASP ZAP ou avaliacao equivalente em homologacao.
7. Executar teste de carga e registrar resultados por release.

**Aceite:** pipeline bloqueia merge com falha de teste, vulnerabilidade critica ou segredo detectado.

## Fase 7 - Producao e recuperacao

**Dependencias:** todas as fases anteriores.

1. Criar PostgreSQL privado com TLS e usuario de menor privilegio.
2. Configurar secrets por secret manager.
3. Executar migracoes durante janela controlada.
4. Ativar HTTPS no proxy reverso.
5. Publicar o Angular e colocar o dominio em `CORS_ORIGINS`.
6. Configurar backup criptografado do banco e storage.
7. Testar restore em ambiente isolado.
8. Executar smoke test de login, RBAC, pacientes, estoque, produtos e anexos.
9. Definir rollback da aplicacao e da migracao.

**Aceite:** checklist de go-live assinado, backup restaurado, monitoramento ativo e teste de carga aprovado.

## Ordem imediata recomendada

1. Fechar refresh token/cookie e CORS.
2. Criar migracoes e constraint de identificador.
3. Implementar auditoria e politica de dados.
4. Concluir paginacao visual no Angular.
5. Proteger anexos e configurar storage.
6. Implantar observabilidade e pipeline.
7. Provisionar PostgreSQL e executar homologacao.

## Regra de conclusao

Nenhuma fase deve ser marcada como concluida apenas por compilacao. Cada fase precisa de teste automatizado, evidencia operacional ou decisao formal documentada quando depender de infraestrutura, seguranca ou juridico.