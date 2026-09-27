# Guia de Segurança — Para o Frontend

> **Atenção**: A partir desta versão, **todas as rotas da API exigem autenticação JWT**.
> O frontend precisa fazer login primeiro e incluir o token em todas as requisições.

---

## 1. Autenticação — Como fazer login

**POST** `/api/auth/login`

```json
{
  "username": "admin",
  "senha": "Admin@12345"
}
```

Resposta de sucesso (`200 OK`):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_ADMIN",
  "username": "admin"
}
```

Resposta de erro (`401 Unauthorized`):
```json
{ "message": "Credenciais inválidas" }
```

---

## 2. Como usar o token em todas as requisições

Adicione o header `Authorization` em **cada** chamada à API:

```
Authorization: Bearer {token}
```

Exemplo com fetch:
```js
fetch('/api/pacientes', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
```

> O token expira em **24 horas**. Ao receber `401`, faça novo login.

---

## 3. CPF mascarado

A API retorna o CPF **sempre mascarado**: `***.***.***-XX`

O dado completo fica protegido no banco. O frontend deve exibir somente o valor
mascarado que recebe — não tente "desmascarar" no front.

Se precisar do CPF completo para algum fluxo específico, solicite ao administrador
do backend criar um endpoint dedicado com controle de acesso.

---

## 4. Upload de Anexos

- **Extensões permitidas**: `pdf`, `jpg`, `jpeg`, `png`
- **Tamanho máximo**: 10 MB
- **Header obrigatório**: `Authorization: Bearer {token}`

Exemplo:
```js
const formData = new FormData();
formData.append('arquivo', file);

fetch(`/api/pacientes/${id}/anexo`, {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}` },
  body: formData
});
```

---

## 5. Formato de erros

Todos os erros seguem o mesmo padrão — **nunca haverá stack trace exposto**:

```json
{ "message": "Descrição do erro" }
```

| Status | Significado                                     |
|--------|-------------------------------------------------|
| `400`  | Dados inválidos (validação de campos)           |
| `401`  | Não autenticado / token expirado                |
| `403`  | Sem permissão para este recurso                 |
| `404`  | Recurso não encontrado                          |
| `409`  | Conflito (ex.: código de identificação duplicado)|
| `429`  | Muitas tentativas de login (rate limit)          |
| `500`  | Erro interno — exibe mensagem genérica          |

---

## 6. Perfis de acesso (RBAC)

Perfis disponíveis:

- `ROLE_ADMIN`
- `ROLE_MEDICO`
- `ROLE_RECEPCAO`
- `ROLE_LEITURA`

Regras principais:

- `DELETE /api/pacientes/**` → apenas `ROLE_ADMIN`
- `POST /api/estoque/movimentar` → `ROLE_ADMIN` ou `ROLE_RECEPCAO`
- leitura de pacientes/estoque → todos os perfis acima
- criação/edição de pacientes → `ROLE_ADMIN`, `ROLE_MEDICO`, `ROLE_RECEPCAO`

---

## 7. CORS

Apenas `http://localhost:4200` está liberado em desenvolvimento.

Em produção, o administrador deve definir a variável de ambiente `CORS_ORIGINS`
com a URL real do frontend (ex.: `https://app.euroespesbrasilapp.com.br`).

---

## 8. Variáveis de ambiente para produção

| Variável           | Descrição                                             |
|--------------------|-------------------------------------------------------|
| `JWT_SECRET`       | Segredo JWT (mínimo 64 chars) — **obrigatório trocar** |
| `JWT_EXPIRATION_MS`| Expiração do token em ms (padrão: 86400000 = 24h)    |
| `SPRING_PROFILES_ACTIVE` | Ambiente ativo (`prod` em produção)           |
| `DB_URL`           | URL JDBC do PostgreSQL                               |
| `DB_USERNAME`      | Usuário do banco de dados                            |
| `ADMIN_USERNAME`   | Usuário admin criado no primeiro boot                  |
| `ADMIN_PASSWORD`   | Senha admin — **troque após o primeiro login**         |
| `CORS_ORIGINS`     | URL do frontend em produção                           |
| `DB_PASSWORD`      | Senha do banco de dados                               |
| `UPLOAD_DIR`       | Diretório para armazenar anexos de pacientes           |
| `REQUIRE_SSL`      | Força HTTPS (`true` em produção)                      |
| `APP_SEED_ENABLED` | Mantém massa fake (deve ser `false` em produção)      |

---

## 9. Checklist do Frontend

- [ ] Implementar tela de login chamando `POST /api/auth/login`
- [ ] Salvar token no `localStorage` ou `sessionStorage` (preferencialmente com expiração controlada)
- [ ] Criar interceptor HTTP para injetar `Authorization: Bearer {token}` automaticamente
- [ ] Tratar respostas `401/403/429` com fluxo de sessão adequado
- [ ] Exibir CPF sempre mascarado como recebido da API
- [ ] Não enviar arquivos maiores que 10MB ou fora das extensões permitidas
- [ ] Em produção, usar **HTTPS** (o backend rejeitará origens não autorizadas)

