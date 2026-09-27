# Gates de producao e LGPD

Este documento define os controles que precisam de evidencia antes do go-live.
Testes automatizados verdes nao substituem os gates operacionais abaixo.

## Bloqueadores antes do go-live

- [ ] `SPRING_PROFILES_ACTIVE=prod` confirmado no ambiente produtivo.
- [ ] `JWT_SECRET`, `DB_PASSWORD` e demais segredos vieram de secret manager.
- [ ] `CREATE_DEFAULT_ADMIN=false` e `APP_SEED_ENABLED=false`.
- [ ] `CORS_ORIGINS` contem somente origens HTTPS reais.
- [ ] `DB_URL` usa `sslmode=require` e o certificado do PostgreSQL foi validado.
- [ ] PostgreSQL esta em rede privada e o usuario da aplicacao tem privilegio minimo.
- [ ] TLS termina em proxy confiavel; HSTS, redirecionamento HTTPS e headers foram verificados externamente.
- [ ] Backup criptografado executado e restore testado em ambiente isolado.
- [ ] Uploads ficam em storage privado, com antivirus, hash SHA-256 e limpeza de orfaos.
- [ ] Rate limit distribuido esta ativo para login, uploads e endpoints sensiveis.
- [ ] Teste de carga, SAST, SCA, secret scanning e DAST foram executados no pipeline.

## LGPD: governanca e direitos do titular

- [ ] Inventario de dados, finalidade, base legal, categorias de titulares e destinatarios aprovado.
- [ ] Politica de retencao e descarte definida para pacientes, anexos, logs e backups.
- [ ] Fluxo documentado para acesso, correcao, portabilidade, anonimização e eliminacao.
- [ ] Endpoint/processo de anonimização e eliminacao aprovado pelo responsavel de privacidade.
- [ ] Auditoria persistente registra acesso, alteracao, download e eliminacao sem armazenar PII desnecessaria.
- [ ] Relatorio de incidente, notificacao e plano de resposta definidos.
- [ ] Contratos/DPA com operadores e fornecedores revisados.
- [ ] Encarregado/DPO, canal do titular e evidencias de treinamento definidos.
- [ ] Homologacao usa dados anonimizados ou sinteticos.

## Evidencias minimas

- `npm audit` sem vulnerabilidades criticas/altas sem aceite formal.
- `npm run build` e `npm test -- --run` passando.
- `mvn test` passando.
- Relatorio do teste de carga e limites observados.
- Registro do backup e restore bem-sucedido.
- Resultado de varredura de segredos e imagem/container.
- Aprovacao formal dos riscos residuais.

## Status atual

O codigo possui protecoes adicionais de cookie HttpOnly, CSRF, CORS sem fallback,
validacao de TLS no banco em `prod`, Flyway e testes automatizados. O sistema ainda
nao deve ser chamado de pronto para producao ate que os gates operacionais e de
governanca acima tenham evidencia real.