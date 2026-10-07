package org.example.controllers;

import jakarta.validation.Valid;
import org.example.models.Medico;
import org.example.services.MedicoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private final MedicoService medicoService;

    public MedicoController(MedicoService medicoService) {
        this.medicoService = medicoService;
    }

    @GetMapping
    public ResponseEntity<List<Medico>> listar() {
        return ResponseEntity.ok(medicoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Medico> obter(@PathVariable Long id) {
        return ResponseEntity.ok(medicoService.obterPorId(id));
    }

    @PostMapping
    public ResponseEntity<Medico> criar(@Valid @RequestBody Medico medico) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicoService.criar(medico));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Medico> atualizar(@PathVariable Long id, @Valid @RequestBody Medico medico) {
        return ResponseEntity.ok(medicoService.atualizar(id, medico));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        medicoService.remover(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/estoque")
    public ResponseEntity<List<MedicoService.MedicoEstoqueResumo>> estoque(@PathVariable Long id) {
        return ResponseEntity.ok(medicoService.estoqueDoMedico(id));
    }

    @PostMapping("/{id}/estoque")
    public ResponseEntity<MedicoService.MedicoEstoqueResumo> ajustarEstoque(@PathVariable Long id,
                                                                            @RequestBody AjusteEstoqueMedicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicoService.ajustarEstoque(id, request.getProdutoId(), request.getDelta(), request.getEstoqueMinimo()));
    }

    public static class AjusteEstoqueMedicoRequest {
        private Long produtoId;
        private int delta;
        private Integer estoqueMinimo;

        public Long getProdutoId() {
            return produtoId;
        }

        public void setProdutoId(Long produtoId) {
            this.produtoId = produtoId;
        }

        public int getDelta() {
            return delta;
        }

        public void setDelta(int delta) {
            this.delta = delta;
        }

        public Integer getEstoqueMinimo() {
            return estoqueMinimo;
        }

        public void setEstoqueMinimo(Integer estoqueMinimo) {
            this.estoqueMinimo = estoqueMinimo;
        }
    }
}
