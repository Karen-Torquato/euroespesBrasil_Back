package org.example.controllers;

import org.example.exceptions.BadRequestException;
import org.example.services.EstoqueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/estoque")
public class EstoqueController {

    @Autowired
    private EstoqueService estoqueService;

    // GET estoque com histórico
    @GetMapping
    public ResponseEntity<Map<String, Object>> obterEstoque() {
        return ResponseEntity.ok(estoqueService.obterEstoqueComHistorico());
    }

    // POST registrar movimentação
    @PostMapping("/movimentar")
    public ResponseEntity<Map<String, Object>> registrarMovimentacao(@RequestBody MovimentacaoRequest request) {
        if (request.getTipo() == null || request.getTipo().isBlank()) {
            throw new BadRequestException("Tipo é obrigatório (ENTRADA ou SAIDA)");
        }

        if (request.getQuantidade() == null || request.getQuantidade() <= 0) {
            throw new BadRequestException("Quantidade deve ser maior que zero");
        }

        estoqueService.registrarMovimentacao(request.getTipo(), request.getQuantidade(), request.getMotivo());
        return ResponseEntity.ok(estoqueService.obterEstoqueComHistorico());
    }

    // Classe para requisição de movimentação
    public static class MovimentacaoRequest {
        private String tipo;
        private Integer quantidade;
        private String motivo;

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public Integer getQuantidade() {
            return quantidade;
        }

        public void setQuantidade(Integer quantidade) {
            this.quantidade = quantidade;
        }

        public String getMotivo() {
            return motivo;
        }

        public void setMotivo(String motivo) {
            this.motivo = motivo;
        }
    }

}

