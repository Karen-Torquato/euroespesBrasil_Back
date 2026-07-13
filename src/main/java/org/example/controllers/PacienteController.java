package org.example.controllers;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.NotFoundException;
import org.example.models.Paciente;
import org.example.services.PacienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    @Autowired
    private PacienteService pacienteService;

    private static final String UPLOAD_DIR = "uploads/pacientes/";

    // GET todos os pacientes
    @GetMapping
    public ResponseEntity<List<Paciente>> obterTodosPacientes() {
        List<Paciente> pacientes = pacienteService.obterTodosPacientes();
        return ResponseEntity.ok(pacientes);
    }

    // GET paciente por ID
    @GetMapping("/{id}")
    public ResponseEntity<Paciente> obterPacientePorId(@PathVariable Long id) {
        Paciente paciente = pacienteService.obterPacientePorId(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        return ResponseEntity.ok(paciente);
    }

    // POST novo paciente
    @PostMapping
    public ResponseEntity<Paciente> criarPaciente(@RequestBody Paciente paciente) {
        Paciente criado = pacienteService.criarPaciente(paciente);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    // PUT atualiza paciente
    @PutMapping("/{id}")
    public ResponseEntity<Paciente> atualizarPaciente(@PathVariable Long id, @RequestBody Paciente pacienteAtualizado) {
        Paciente atualizado = pacienteService.atualizarPaciente(id, pacienteAtualizado);
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

            // Criar diretório se não existir
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Gerar nome único com UUID
            String nomeOriginal = arquivo.getOriginalFilename();
            String extensao = nomeOriginal != null && nomeOriginal.contains(".")
                    ? nomeOriginal.substring(nomeOriginal.lastIndexOf("."))
                    : "";
            String nomeArquivo = UUID.randomUUID() + extensao;
            String caminho = UPLOAD_DIR + nomeArquivo;

            // Salvar arquivo
            Files.write(Paths.get(caminho), arquivo.getBytes());

            // Atualizar paciente
            pacienteService.salvarAnexo(id, nomeOriginal, caminho);

            // Retornar paciente atualizado
            Paciente paciente = pacienteService.obterPacientePorId(id)
                    .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
            return ResponseEntity.ok(paciente);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar arquivo", e);
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

        File arquivo = new File(paciente.getAnexoCaminho());
        if (!arquivo.exists()) {
            throw new NotFoundException("Arquivo de anexo não encontrado");
        }

        Resource resource = new FileSystemResource(arquivo);
        String nomeDownload = paciente.getAnexoNome() != null ? paciente.getAnexoNome() : "anexo";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeDownload + "\"")
                .body(resource);
    }
}

