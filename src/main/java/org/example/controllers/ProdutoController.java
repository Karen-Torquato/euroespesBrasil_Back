package org.example.controllers;

import jakarta.validation.Valid;
import org.example.models.Produto;
import org.example.services.ProdutoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public ResponseEntity<List<Produto>> listarTodos() {
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Produto> obterPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.obterPorId(id));
    }

    @PostMapping
    public ResponseEntity<Produto> criar(@Valid @RequestBody Produto produto) {
        Produto criado = produtoService.criarProduto(produto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Produto> atualizar(@PathVariable Long id, @RequestBody Produto produto) {
        return ResponseEntity.ok(produtoService.atualizarProduto(id, produto));
    }

    @PostMapping("/{id}/ajustar-estoque")
    public ResponseEntity<Produto> ajustarEstoque(@PathVariable Long id, @RequestBody AjusteEstoqueRequest request) {
        produtoService.ajustarEstoque(id, request.getDelta());
        return ResponseEntity.ok(produtoService.obterPorId(id));
    }

    public static class AjusteEstoqueRequest {
        private int delta;

        public int getDelta() {
            return delta;
        }

        public void setDelta(int delta) {
            this.delta = delta;
        }
    }
}
