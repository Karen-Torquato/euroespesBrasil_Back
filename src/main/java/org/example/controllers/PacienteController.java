package org.example.controllers;

import jakarta.validation.Valid;
import org.example.exceptions.BadRequestException;
import org.example.exceptions.NotFoundException;
import org.example.models.Paciente;
import org.example.services.PacienteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteService pacienteService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.max-size-bytes}")
    private long maxSizeBytes;

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of("pdf", "jpg", "jpeg", "png");

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    // GET todos os pacientes
    @GetMapping
    public ResponseEntity<?> obterTodosPacientes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "criadoEm") String sort,
            @RequestParam(required = false, defaultValue = "desc") String direction) {

        if (page != null) {
            Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
            org.springframework.data.domain.Pageable pageable =
                    PageRequest.of(page, Math.min(size, 100), Sort.by(dir, sort));
            org.springframework.data.domain.Page<Paciente> resultado =
                    pacienteService.listarPaginado(pageable);
            resultado.getContent().forEach(this::mascararDadosSensiveis);
            return ResponseEntity.ok(resultado);
        }

        List<Paciente> pacientes = pacienteService.obterTodosPacientes();
        pacientes.forEach(this::mascararDadosSensiveis);
        return ResponseEntity.ok(pacientes);
    }

    // GET paciente por ID
    @GetMapping("/{id}")
    public ResponseEntity<Paciente> obterPacientePorId(@PathVariable Long id) {
        Paciente paciente = pacienteService.obterPacientePorId(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        mascararDadosSensiveis(paciente);
        return ResponseEntity.ok(paciente);
    }

    // POST novo paciente
    @PostMapping
    public ResponseEntity<Paciente> criarPaciente(@Valid @RequestBody Paciente paciente) {
        Paciente criado = pacienteService.criarPaciente(paciente);
        mascararDadosSensiveis(criado);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    // PUT atualiza paciente
    @PutMapping("/{id}")
    public ResponseEntity<Paciente> atualizarPaciente(
            @PathVariable Long id,
            @Valid @RequestBody Paciente pacienteAtualizado) {
        Paciente atualizado = pacienteService.atualizarPaciente(id, pacienteAtualizado);
        mascararDadosSensiveis(atualizado);
        return ResponseEntity.ok(atualizado);
    }

    // DELETE paciente
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPaciente(@PathVariable Long id) {
        pacienteService.deletarPaciente(id);
        return ResponseEntity.noContent().build();
    }

    // POST upload de anexo
    @PostMapping("/{id}/anexo")
    public ResponseEntity<?> uploadAnexo(
            @PathVariable Long id,
            @RequestParam("arquivo") MultipartFile arquivo) {
        try {
            if (arquivo.isEmpty()) {
                throw new BadRequestException("Arquivo vazio");
            }

            // Validar tamanho
            if (arquivo.getSize() > maxSizeBytes) {
                throw new BadRequestException("Arquivo excede o tamanho máximo permitido (10MB)");
            }

            // Validar extensão (não confiar no MIME do cliente)
            String nomeOriginal = arquivo.getOriginalFilename();
            if (nomeOriginal == null || !nomeOriginal.contains(".")) {
                throw new BadRequestException("Arquivo sem extensão válida");
            }
            String extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf(".") + 1).toLowerCase();
            if (!EXTENSOES_PERMITIDAS.contains(extensao)) {
                throw new BadRequestException("Extensão não permitida. Permitidas: pdf, jpg, jpeg, png");
            }

            // Ler conteúdo uma vez para reutilizar (validação e gravação)
            byte[] conteudo = arquivo.getBytes();

            // Validar magic bytes (assinatura real do arquivo)
            validarMagicBytes(conteudo, extensao);

            // Criar diretório se não existir
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Nome seguro com UUID — nunca usa nome original no disco (evita path traversal)
            String nomeArquivo = UUID.randomUUID() + "." + extensao;
            Path caminhoFinal = uploadPath.resolve(nomeArquivo).normalize();

            // Garantir que o arquivo não sai do diretório de upload
            if (!caminhoFinal.startsWith(uploadPath)) {
                throw new BadRequestException("Caminho de arquivo inválido");
            }

            Files.write(caminhoFinal, conteudo);
            pacienteService.salvarAnexo(id, nomeOriginal, caminhoFinal.toString());

            Paciente paciente = pacienteService.obterPacientePorId(id)
                    .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
            mascararDadosSensiveis(paciente);
            return ResponseEntity.ok(paciente);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar arquivo");
        }
    }

    // GET download de anexo
    @GetMapping("/{id}/anexo/download")
    public ResponseEntity<?> downloadAnexo(@PathVariable Long id) {
        Paciente paciente = pacienteService.obterAnexoPaciente(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));

        if (paciente.getAnexoCaminho() == null || paciente.getAnexoCaminho().isEmpty()) {
            throw new NotFoundException("Paciente não possui anexo");
        }

        // Proteger contra path traversal no download
        Path uploadBase = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path arquivoPath = Paths.get(paciente.getAnexoCaminho()).toAbsolutePath().normalize();
        if (!arquivoPath.startsWith(uploadBase)) {
            throw new BadRequestException("Acesso negado ao arquivo");
        }

        File arquivo = arquivoPath.toFile();
        if (!arquivo.exists()) {
            throw new NotFoundException("Arquivo de anexo não encontrado");
        }

        Resource resource = new FileSystemResource(arquivo);
        // Nome seguro para download (remove caracteres especiais)
        String nomeDownload = paciente.getAnexoNome() != null
                ? paciente.getAnexoNome().replaceAll("[^a-zA-Z0-9._\\-]", "_")
                : "anexo";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeDownload + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    /**
     * Valida os magic bytes (assinatura binária) do arquivo contra a extensão declarada.
     * Impede uploads de arquivos com extensão falsa (ex: executável renomeado para .pdf).
     */
    private void validarMagicBytes(byte[] conteudo, String extensao) {
        if (conteudo == null || conteudo.length < 4) {
            throw new BadRequestException("Arquivo muito pequeno ou corrompido");
        }
        boolean valido = switch (extensao) {
            case "pdf" ->
                conteudo[0] == 0x25 && conteudo[1] == 0x50 && conteudo[2] == 0x44 && conteudo[3] == 0x46;
            case "jpg", "jpeg" ->
                (conteudo[0] & 0xFF) == 0xFF && (conteudo[1] & 0xFF) == 0xD8 && (conteudo[2] & 0xFF) == 0xFF;
            case "png" ->
                (conteudo[0] & 0xFF) == 0x89 && conteudo[1] == 0x50 && conteudo[2] == 0x4E && conteudo[3] == 0x47;
            default -> false;
        };
        if (!valido) {
            throw new BadRequestException("Conteúdo do arquivo não corresponde à extensão declarada");
        }
    }

    /**
     * Mascara CPF na resposta da API — o dado completo fica protegido no banco.
     * Exibe apenas os 2 últimos dígitos: ***.***.***.XX
     */
    private void mascararDadosSensiveis(Paciente paciente) {
        if (paciente.getCpf() != null && !paciente.getCpf().isBlank()) {
            String cpfLimpo = paciente.getCpf().replaceAll("\\D", "");
            if (cpfLimpo.length() == 11) {
                paciente.setCpf("***.***.***-" + cpfLimpo.substring(9));
            } else if (cpfLimpo.length() >= 4) {
                paciente.setCpf("***-" + cpfLimpo.substring(cpfLimpo.length() - 4));
            } else {
                paciente.setCpf("***");
            }
        }
    }
}
