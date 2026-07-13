# Documentação Técnica - Euroespes Backend

## 📐 Arquitetura

### Padrão de Projeto: Layered Architecture
```
controllers/
├── PacienteController.java     (API REST endpoints)
├── EstoqueController.java

services/
├── PacienteService.java        (Lógica de negócio)
├── EstoqueService.java

repositories/
├── PacienteRepository.java     (Data access)
├── EstoqueRepository.java
├── MovimentacaoEstoqueRepository.java

models/
├── Paciente.java               (Entities)
├── Estoque.java
├── MovimentacaoEstoque.java
```

---

## 🔑 Decisões Técnicas

### 1. **Banco de Dados: H2 File-Based**
- ✅ Sem necessidade de servidor separado
- ✅ Arquivo persistente em `./data/rotas-entregas.mv.db`
- ✅ `ddl-auto=update` nunca dropa tabelas, só atualiza schema
- ✅ Console de debug em `/h2-console`

### 2. **ORM: Hibernate + Spring Data JPA**
- ✅ Queries automáticas com `findById()`, `findAll()`
- ✅ Métodos custom simples (ex: `findByCodigoIdentificacao`)
- ✅ Batch operations otimizadas
- ✅ Suporte a `@PrePersist`, `@PreUpdate`

### 3. **Timestamps: @PrePersist e @PreUpdate**
```java
@Column(nullable = false, updatable = false)
private LocalDateTime criadoEm;

@PrePersist
protected void onCreate() {
    this.criadoEm = LocalDateTime.now();
    this.atualizadoEm = LocalDateTime.now();
}

@PreUpdate
protected void onUpdate() {
    this.atualizadoEm = LocalDateTime.now();
}
```
- ✅ Setters têm `@JsonIgnore` para não sobrescrever via JSON
- ✅ Garantem que cliente não pode manipular timestamps

### 4. **JSON Ignorance: @JsonIgnoreProperties**
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class Paciente {
    // ...
}
```
- ✅ Campos extras no JSON são silenciosamente ignorados
- ✅ Sem erro 400 por campos desconhecidos
- ✅ Permite evolução da API sem quebrar clientes antigos

### 5. **Validações de Negócio no Serviço**
```java
if (paciente.getNome() == null || paciente.getNome().trim().isEmpty()) {
    throw new IllegalArgumentException("Nome é obrigatório");
}
```
- ✅ Centralizadas em `PacienteService`
- ✅ Reutilizáveis por múltiplos controllers
- ✅ Mensagens de erro consistentes
- ✅ Controllers transformam em HTTP responses apropriadas

### 6. **Transações Atômicas: @Transactional**
```java
@Transactional
public Paciente criarPaciente(Paciente paciente) {
    // PRIMEIRO: Valida e desconta estoque
    estoqueService.decrementarEstoque(paciente.getQuantidadeKits());
    
    // DEPOIS: Salva paciente
    return pacienteRepository.save(paciente);
}
```
- ✅ Se falhar em qualquer ponto, tudo é rollback
- ✅ Garante consistência: estoque + paciente sempre sincronizados
- ✅ Sem risco de duplicate: SAIDA registrada mas paciente não criado

### 7. **Merge em PUT (Atualização Parcial)**
```java
if (pacienteAtualizado.getNome() != null && !pacienteAtualizado.getNome().isEmpty()) {
    paciente.setNome(pacienteAtualizado.getNome());
}
```
- ✅ Cliente envia só os campos a atualizar
- ✅ Campos nulos são ignorados (não sobrescrevem)
- ✅ Não precisa enviar toda a entidade
- ✅ Menor payload, mais eficiente

### 8. **Upload de Arquivos: UUID + Extensão**
```java
String nomeArquivo = UUID.randomUUID() + extensao;
String caminho = UPLOAD_DIR + nomeArquivo;
Files.write(Paths.get(caminho), arquivo.getBytes());
```
- ✅ Nomes únicos: sem colisão mesmo com mesma extensão
- ✅ Seguro: `UUID.randomUUID()` garante aleatoriedade
- ✅ `anexoNome` preserva nome original para download
- ✅ Arquivo anterior é deletado ao substituir

### 9. **CORS: Liberado para localhost:4200**
```java
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOrigins("http://localhost:4200")
                    .allowCredentials(true);
        }
    };
}
```
- ✅ Angular em porta 4200 pode fazer requisições
- ✅ `allowCredentials(true)` se precisar de cookies depois
- ✅ Sem necessidade de `@CrossOrigin` em cada controller

### 10. **Error Handling: Try-Catch com DTO**
```java
try {
    return ResponseEntity.status(HttpStatus.CREATED).body(criado);
} catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
} catch (Exception e) {
    return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
}
```
- ✅ Mensagens de erro estruturadas
- ✅ HTTP status codes apropriados (201, 400, 500)
- ✅ JSON response com `mensagem`
- ✅ Frontend recebe erros estruturados, não exceção Java

### 11. **Lombok: Redução de Boilerplate**
```java
@Data                    // Getters, setters, equals, hashCode, toString
@NoArgsConstructor       // Construtor sem argumentos
@AllArgsConstructor      // Construtor com todos os campos
public class Paciente {
    // ...
}
```
- ✅ Menos código, mais legível
- ✅ Maven automaticamente configura fonte de processamento

### 12. **Configuração Centralizada: application.properties**
```properties
spring.datasource.url=jdbc:h2:file:./data/rotas-entregas
spring.jpa.hibernate.ddl-auto=update
server.port=8080
spring.servlet.multipart.max-file-size=20MB
```
- ✅ Ambiente-specific (dev, prod)
- ✅ Sem hardcodes no código
- ✅ Fácil de modificar

---

## 🔄 Fluxo de Requisição Exemplo

### Criar Paciente Ativo

```
1. Cliente Angular
   POST /api/pacientes
   { nome: "João", quantidadeKits: 2, ... }
   
2. PacienteController.criarPaciente()
   - Recebe @RequestBody Paciente
   - Chama pacienteService.criarPaciente()
   
3. PacienteService.criarPaciente()
   - Valida: nome não vazio ✓
   - Valida: não é Rascunho ✓
   - Valida: quantidadeKits >= 1 ✓
   - Valida: estoque disponível >= 2 ✓
   - Chama estoqueService.decrementarEstoque(2)
   
4. EstoqueService.decrementarEstoque()
   @Transactional
   - Chama registrarMovimentacao("SAIDA", 2, ...)
   - Obtém estoque atual (ex: 100)
   - totalKits = 100 - 2 = 98
   - Salva Estoque(totalKits: 98)
   - Cria MovimentacaoEstoque(SAIDA, 2)
   - Salva MovimentacaoEstoque
   - Se erro: ROLLBACK tudo
   
5. PacienteService.criarPaciente()
   - Salva Paciente com criadoEm = NOW()
   - @PrePersist é executado
   - Retorna Paciente(id: 1, ...)
   
6. PacienteController.criarPaciente()
   - HTTP 201 Created
   - Body: Paciente completo (com id)
   
7. Cliente Angular
   - Recebe status 201
   - Atualiza lista de pacientes
   - UI mostra novo paciente
   - Estoque atualizado (98 kits)
```

---

## 🛡️ Segurança

### ✓ Implementado
- [x] Validações de entrada no backend
- [x] Tratamento de exceções estruturado
- [x] Transações atômicas
- [x] Limite de upload (20MB)
- [x] Isolamento de dados (sem SQL injection com JPA)

### ⚠️ Não Implementado (Futuro)
- [ ] Autenticação (JWT/OAuth2)
- [ ] Autorização (roles/permissions)
- [ ] Rate limiting
- [ ] Audit log
- [ ] Encryption de dados sensíveis

---

## 📊 Escalabilidade

### Atual
- Single-node H2
- Memory-backed queries
- Sem caching

### Futuro
- Migrar para PostgreSQL/MySQL
- Adicionar Redis para cache
- Implementar paginação em endpoints
- Índices no banco de dados

---

## 🧪 Testes (Futura Implementação)

```java
// Exemplo de teste unitário
@Test
public void testCriarPacienteSemEstoque() {
    Paciente p = new Paciente();
    p.setNome("Teste");
    p.setQuantidadeKits(1000);
    
    assertThrows(IllegalArgumentException.class, () -> {
        pacienteService.criarPaciente(p);
    });
}
```

---

## 📚 Recursos Úteis

- [Spring Boot Official Docs](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [H2 Database](https://www.h2database.com/)
- [Lombok Documentation](https://projectlombok.org/)

---

**Última atualização**: 27/06/2024

