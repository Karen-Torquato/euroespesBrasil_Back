# ⚡ QUICK START - 3 MINUTOS PARA COMEÇAR

## 1️⃣ Sincronizar Dependências (1 min)

```
Na IDE IntelliJ:
1. Clique com botão direito em pom.xml
2. Selecione "Maven" → "Reload Projects"
3. Aguarde aparecer mensagem "Projects imported successfully"
```

## 2️⃣ Executar Aplicação (30 sec)

```
Na IDE IntelliJ:
1. Menu: Run → Run 'EuroespesBrasilApplication'
   (ou pressione Shift + F10)
2. Terminal deve mostrar:
   "Tomcat started on port(s): 8080 (http)"
```

## 3️⃣ Testar um Endpoint (1.5 min)

### Opção A: Copiar no browser (mais rápido)
```
Abra no navegador:
http://localhost:8080/api/pacientes
```

### Opção B: Usar cURL
```bash
curl http://localhost:8080/api/pacientes
```

### Opção C: Usar REQUESTS.http
```
1. Abrir arquivo REQUESTS.http na IDE
2. Clicar em "Send Request" acima de "### 1. Listar todos os pacientes"
3. Ver resposta no painel
```

---

## ✅ PRONTO!

Se viu `[]` (lista vazia) ou sem erro, a aplicação está **100% funcionando**! 🚀

---

## 📌 PRÓXIMOS PASSOS

### Para testar CRIAR paciente:

```bash
curl -X POST http://localhost:8080/api/pacientes \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "João Silva",
    "quantidadeKits": 2,
    "codigoIdentificacao": "JOAO001",
    "statusResultado": "Pendente"
  }'
```

Ou abra `REQUESTS.http` e procure por "### 3. Criar novo paciente"

### Ver dados no banco:
```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/rotas-entregas
User: sa
Pass: (deixar em branco)
```

---

## 📚 DOCUMENTAÇÃO

| Arquivo | Para |
|---------|------|
| START_HERE.md | Você está aqui! |
| README.md | Como executar |
| REQUESTS.http | Exemplos de requisições |
| TROUBLESHOOTING.md | Se algo der errado |
| ARQUITETURA.md | Entender o código |

---

## ⚠️ Se der erro?

### "Cannot resolve symbol 'springframework'"
→ Rode passo 1️⃣ novamente (Reload Projects)

### "Port 8080 already in use"
→ Abra TROUBLESHOOTING.md seção "Port already in use"

### Outro erro?
→ Veja TROUBLESHOOTING.md

---

## 🎯 Endpoints Disponíveis

```
Pacientes:
  GET    /api/pacientes              ← Teste isto primeiro!
  POST   /api/pacientes              ← Depois isto
  PUT    /api/pacientes/{id}         ← Depois isto
  DELETE /api/pacientes/{id}         ← E isto

Estoque:
  GET    /api/estoque                ← Ver estoque
  POST   /api/estoque/movimentar     ← Registrar movimentação
```

Veja `REQUESTS.http` para exemplos de cada um.

---

## 💡 DICA

Use o arquivo `REQUESTS.http` diretamente na IDE:
- Clique em um endpoint
- Clicar em "Send Request"
- Ver resposta no lado direito

É muito mais prático que terminal/Postman!

---

**Tempo total**: ~3 minutos
**Resultado**: Backend funcionando 100%
**Próximo**: Integrar com Angular frontend

Bom desenvolvimento! 🚀

