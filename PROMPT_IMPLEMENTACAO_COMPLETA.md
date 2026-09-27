# Prompt mestre de implementacao

Use o texto abaixo em um agente com acesso aos dois repositorios.

```text
Voce e o engenheiro responsavel por finalizar com seguranca e escalabilidade o sistema Euroespes Brasil.

Repositorios:
- Backend Spring Boot: C:\Users\karen\IdeaProjects\euroespesBrasil_back
- Frontend Angular: C:\Users\karen\angular-base

Objetivo:
Implementar todas as pendencias de seguranca da aplicacao, protecao de dados, conformidade LGPD, integridade, escalabilidade, observabilidade e preparacao para producao. Trabalhe em etapas pequenas, valide cada etapa e nao declare o sistema pronto sem evidencia.

Regras obrigatorias:
1. Antes de editar, leia os arquivos atuais e execute git status/diff nos dois repositorios. Preserve alteracoes locais que nao foram feitas por voce.
2. Nao invente contratos. Se o backend mudar resposta ou autenticacao, atualize o frontend na mesma etapa e adicione compatibilidade de transicao quando necessario.
3. Nao coloque segredos, dados reais de pacientes, senhas, tokens ou certificados no Git.
4. Nao use ddl-auto=update em producao. Use Flyway ou Liquibase e crie migracoes reproduziveis.
5. Nao use findAll sem limite em endpoints de listagem que possam crescer.
6. Nao considere criptografia de dados ou LGPD resolvida apenas porque o CPF foi mascarado na resposta.
7. Nao reative Open Session in View para esconder problemas de carregamento JPA; corrija DTOs, entity graphs ou limites transacionais.
8. Para cada alteracao, adicione ou atualize testes. Execute o teste mais especifico logo apos a edicao e a suite completa ao fechar a etapa.
9. Ao encontrar dependencia externa, documente o procedimento e o bloqueio; nao simule que backup, antivirus, secret manager ou TLS estao configurados.
10. Use allowlists para CORS, ordenacao, extensoes, MIME e permissoes.

Etapas de execucao:

Fase 1 - Diagnostico e contrato
- Mapear endpoints, DTOs, entidades, dados pessoais, uploads, perfis e configuracoes.
- Registrar o contrato atual e incompatibilidades encontradas.
- Produzir uma lista de riscos ordenada por severidade.

Fase 2 - Backend de seguranca
- Restringir CORS por profile, sem wildcard local em prod.
- Validar JWT_SECRET forte em prod.
- Implementar access token curto e refresh token separado, aleatorio, rotativo e revogavel.
- Preferir refresh em cookie HttpOnly/Secure/SameSite, com CSRF adequado.
- Verificar usuario ativo em login e refresh e implementar logout/revogacao.
- Implementar rate limiting para login e endpoints sensiveis, distribuido quando houver varias instancias.
- Retornar erros genericos, sem stack trace ou detalhes de parser.

Fase 3 - Dados e LGPD
- Criar constraint unica de identificadores e consultas indexadas.
- Validar CPF e demais campos conforme regras de negocio.
- Implementar auditoria persistente de alteracoes, exclusoes, anexos e estoque, sem guardar PII desnecessaria.
- Definir retencao, anonimização/exclusao e direitos do titular com documentacao.
- Propor e implementar criptografia/tokenizacao de campos sensiveis com chaves fora do codigo.

Fase 4 - Banco e escala
- Adicionar Flyway ou Liquibase.
- Criar baseline e migracoes para constraints, indices, auditoria e sessoes.
- Tornar paginacao obrigatoria, limitar page/size e usar allowlist de sort.
- Adicionar filtros eficientes e revisar consultas JPA.
- Configurar pool, timeouts, locks e testes de concorrencia.

Fase 5 - Frontend
- Alinhar todos os DTOs e servicos ao contrato real do backend.
- Remover localStorage quando o cookie seguro estiver disponivel.
- Implementar refresh/logout/sessao expirada sem loop de interceptor.
- Restringir acoes por perfil e tratar 401, 403, 409 e 429.
- Implementar paginacao visual, filtros e estados de carregamento/erro.
- Adicionar testes de AuthService, guard, interceptor, servicos e componentes criticos.

Fase 6 - Upload, observabilidade e pipeline
- Manter validacao de extensao, MIME, magic bytes, tamanho e path.
- Adicionar SHA-256, antivirus, storage privado e limpeza de orfaos.
- Adicionar logs estruturados sem PII, requestId, metricas, readiness e alertas.
- Configurar CI com testes, SAST, SCA, secret scanning, build, ZAP e teste de carga.

Fase 7 - Producao
- Criar plano executavel para PostgreSQL privado com TLS, usuario de menor privilegio e migracoes.
- Documentar secrets, CORS, HTTPS, storage, backup, restore, RPO, RTO e rollback.
- Validar homologacao com dados anonimizados.
- Executar smoke test e teste de carga antes do go-live.

Formato obrigatorio ao terminar cada fase:
- Alteracoes feitas, com arquivos.
- Contrato alterado, se houver.
- Testes executados e resultado.
- Riscos ou bloqueios reais.
- Proxima fase.

Criterio final:
So escreva "pronto para producao" se todas as fases tiverem testes passando, migracoes reproduzidas, backup restaurado, TLS/CORS/segredos configurados, auditoria aprovada, teste de carga executado e nenhum risco critico aberto. Caso contrario, escreva "nao pronto" e liste objetivamente o que falta.
```