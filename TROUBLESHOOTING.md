# Troubleshooting

## ❌ Problema: "Cannot resolve symbol 'springframework'"

**Causa**: Dependências Maven não foram baixadas

**Solução**:
1. Abra o `pom.xml`
2. Clique em "Maven" (lado direito) → "Reload Projects"
3. Espere o IntelliJ baixar as dependências
4. Build → Rebuild Project

Ou no terminal:
```bash
mvn dependency:resolve
```

---

## ❌ Problema: Porta 8080 já está em uso

**Causa**: Outra aplicação usando a porta

**Solução 1**: Matar o processo
```bash
# Windows (PowerShell)
Get-Process -Name java | Stop-Process -Force

# Ou encontrar o PID
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

**Solução 2**: Mudar a porta
Edite `application.properties`:
```properties
server.port=8081
```

---

## ❌ Problema: H2 Database não está criando arquivos

**Causa**: Diretório `data/` não existe ou permissão negada

**Solução**:
```bash
# Criar diretório
mkdir data

# Se ainda não funcionar, fazer manualmente
# No terminal do H2 Console (http://localhost:8080/h2-console)
# JDBC URL: jdbc:h2:file:./data/rotas-entregas
```

---

## ❌ Problema: "Field 'estoqueRepository' is never assigned"

**Causa**: IDE não reconheceu que `@Autowired` vai injetar

**Solução**: Ignore o warning ou adicione `@SuppressWarnings`:
```java
@SuppressWarnings("all")
@Autowired
private EstoqueRepository estoqueRepository;
```

---

## ❌ Problema: Upload de anexo retorna erro

**Causa 1**: Permissão de escrita no diretório `uploads/`
**Solução 1**: Criar diretório manualmente:
```bash
mkdir uploads/pacientes
```

**Causa 2**: Caminho absoluto vs relativo
**Solução 2**: Verificar `UPLOAD_DIR` em `PacienteController`:
```java
private static final String UPLOAD_DIR = "uploads/pacientes/";
```

---

## ❌ Problema: Request POST retorna 415 (Unsupported Media Type)

**Causa**: `Content-Type` errado na requisição

**Solução**: Verificar header:
```http
POST /api/pacientes HTTP/1.1
Content-Type: application/json  ← CORRETO

# ❌ ERRADO
Content-Type: application/x-www-form-urlencoded
```

---

## ❌ Problema: CORS error no Angular

**Causa**: Angular em `http://localhost:4200` bloqueado

**Solução**: Verificar se CORS está configurado em `EuroespesBrasilApplication.java`:
```java
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOrigins("http://localhost:4200")  ← Adicione se faltando
                    .allowedMethods("*")
                    .allowCredentials(true);
        }
    };
}
```

---

## ⚠️ Problema: Transações não rolando back

**Causa**: `@Transactional` não está na classe/método

**Solução**: Adicione:
```java
@Transactional
public void meuMetodo() {
    // Se lançar exceção, tudo faz rollback
}
```

---

## ✅ Como testar API localmente

### Opção 1: cURL
```bash
curl -X GET http://localhost:8080/api/pacientes
curl -X POST http://localhost:8080/api/pacientes \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teste"}'
```

### Opção 2: Postman
1. Baixar em [postman.com](https://www.postman.com/downloads/)
2. Importar `REQUESTS.http`
3. Clicar "Send" em cada requisição

### Opção 3: REST Client (VS Code)
1. Instalar extensão "REST Client"
2. Usar arquivo `REQUESTS.http` para testar
3. Clicar em "Send Request" acima de cada bloco

### Opção 4: HTTP Client (IntelliJ)
1. Abrir `REQUESTS.http`
2. Clicar no ícone "▶" ao lado de cada request
3. Ver resposta no painel

---

## 🔍 Debug

### Log SQL
Adicione a `application.properties`:
```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
```

### Log HTTP Requests
```properties
logging.level.org.springframework.web=DEBUG
```

### Console H2
Acesse: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/rotas-entregas`
- User: `sa`
- Password: (deixe em branco)

---

## 📊 Performance

### Se aplicação lenta:
1. Adicionar índice no banco:
```sql
CREATE INDEX idx_pacientes_status ON pacientes(statusResultado);
CREATE INDEX idx_pacientes_codigo ON pacientes(codigoIdentificacao);
```

2. Adicionar paginação nos GETs:
```java
@GetMapping
public Page<Paciente> obterTodosPacientes(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size) {
    return pacienteRepository.findAll(PageRequest.of(page, size));
}
```

3. Adicionar lazy loading se múltiplos relacionamentos

---

## 🚀 Deploy

### Para Producão:
1. Mudar `application.properties`:
```properties
spring.jpa.hibernate.ddl-auto=validate  # Não criar tabelas
spring.datasource.url=jdbc:postgresql://host:5432/euroespes  # PostgreSQL
```

2. Compilar:
```bash
mvn clean package -DskipTests
```

3. Executar JAR:
```bash
java -jar target/euroespesBrasil_back-1.0-SNAPSHOT.jar
```

---

## 📝 Logs Importantes

Procure por esses logs na startup:

✅ Correto:
```
Started EuroespesBrasilApplication in 2.345 seconds
Tomcat started on port(s): 8080 (http)
```

❌ Erros comuns:
```
Error starting ApplicationContext
Port 8080 already in use
SQLException: Database not found
```

---

## 💡 Dicas

1. **Sempre fazer `mvn clean` antes de `mvn install`** para limpar builds antigos
2. **Use `@Transactional` em operações críticas** (estoque, pacientes)
3. **Logar exceções** para debug:
   ```java
   catch (Exception e) {
       log.error("Erro ao criar paciente", e);  // Adicione se tiver logger
       throw e;
   }
   ```
4. **Testar endpoints com Postman antes** de integrar com frontend
5. **Documentar mudanças no schema** se fizer migrations

---

Problemas não resolvidos? Verifique:
1. ✓ Java 17+ instalado? `java -version`
2. ✓ Maven instalado? `mvn -v`
3. ✓ Portas livres? `netstat -an`
4. ✓ Permissões de escrita? `ls -la data/`
5. ✓ Logs da aplicação? Ver console da IDE

