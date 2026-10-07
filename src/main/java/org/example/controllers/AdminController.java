package org.example.controllers;

import org.example.models.AuditoriaEvento;
import org.example.models.Permissao;
import org.example.services.AdminManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminManagementService adminManagementService;

    public AdminController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<AdminManagementService.UsuarioResumo>> listarUsuarios() {
        return ResponseEntity.ok(adminManagementService.listarUsuarios());
    }

    @PostMapping("/usuarios")
    public ResponseEntity<AdminManagementService.UsuarioResumo> criarUsuario(@RequestBody AdminManagementService.CriarUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminManagementService.criarUsuario(request));
    }

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<AdminManagementService.UsuarioResumo> atualizarUsuario(@PathVariable Long id,
                                                                                 @RequestBody AdminManagementService.AtualizarUsuarioRequest request) {
        return ResponseEntity.ok(adminManagementService.atualizarUsuario(id, request));
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<Void> removerUsuario(@PathVariable Long id) {
        adminManagementService.removerUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/usuarios/{id}/permissoes")
    public ResponseEntity<Void> atualizarPermissoes(@PathVariable Long id, @RequestBody AtualizarPermissoesRequest request) {
        adminManagementService.atribuirPermissoesUsuario(id, request.permissoes());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissoes")
    public ResponseEntity<List<Permissao>> listarPermissoes() {
        return ResponseEntity.ok(adminManagementService.listarPermissoes());
    }

    @GetMapping("/auditoria")
    public ResponseEntity<List<AuditoriaEvento>> listarAuditoria() {
        return ResponseEntity.ok(adminManagementService.listarAuditoria());
    }

    public record AtualizarPermissoesRequest(List<String> permissoes) {}
}
