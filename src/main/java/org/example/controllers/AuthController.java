package org.example.controllers;

import org.example.repositories.UsuarioRepository;
import org.example.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * POST /api/auth/login
     * Body: { "username": "admin", "senha": "Admin@12345" }
     * Resposta: { "token": "eyJ...", "role": "ROLE_ADMIN" }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String senha = body.get("senha");

        if (username == null || username.isBlank() || senha == null || senha.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Usuário e senha são obrigatórios"));
        }

        return usuarioRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(senha, u.getSenha()))
                .map(u -> {
                    String token = jwtUtil.gerarToken(u.getUsername(), u.getRole());
                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "role", u.getRole(),
                            "username", u.getUsername()
                    ));
                })
                .orElse(ResponseEntity.status(401)
                        .body(Map.of("message", "Credenciais inválidas")));
    }

    /**
     * POST /api/auth/refresh
     * Body: { "token": "eyJ..." }
     * Renova o token JWT se ainda estiver dentro da janela de graça (7 dias após expiração).
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Token é obrigatório"));
        }
        try {
            io.jsonwebtoken.Claims claims = jwtUtil.validarParaRefresh(token);
            String username = claims.getSubject();

            return usuarioRepository.findByUsername(username)
                    .map(u -> {
                        String novoToken = jwtUtil.gerarToken(u.getUsername(), u.getRole());
                        return ResponseEntity.ok(Map.of(
                                "token", novoToken,
                                "role", u.getRole(),
                                "username", u.getUsername()
                        ));
                    })
                    .orElse(ResponseEntity.status(401)
                            .body(Map.of("message", "Usuário não encontrado")));
        } catch (JwtException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Token inválido ou expirado"));
        }
    }
}

