package org.example.controllers;

import org.example.repositories.UsuarioRepository;
import org.example.models.Usuario;
import org.example.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final boolean secureCookie;
    private final long expirationMs;

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          org.springframework.core.env.Environment environment) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.secureCookie = Boolean.parseBoolean(environment.getProperty("app.auth.cookie.secure", "false"));
        this.expirationMs = Long.parseLong(environment.getProperty("jwt.expiration.ms", "3600000"));
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
                .filter(Usuario::isAtivo)
                .map(u -> {
                    String token = jwtUtil.gerarToken(u.getUsername(), u.getRole());
                        return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, accessCookie(token).toString())
                            .body(Map.of(
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
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        String token = cookieToken(request.getCookies());
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Sessão inválida ou expirada"));
        }
        try {
            io.jsonwebtoken.Claims claims = jwtUtil.validarParaRefresh(token);
            String username = claims.getSubject();

            return usuarioRepository.findByUsername(username)
                    .filter(Usuario::isAtivo)
                    .map(u -> {
                        String novoToken = jwtUtil.gerarToken(u.getUsername(), u.getRole());
                        return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, accessCookie(novoToken).toString())
                            .body(Map.of(
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

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAccessCookie().toString())
                .body(Map.of("message", "Sessão encerrada"));
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName(),
                "token", csrfToken.getToken()
        ));
    }

    private ResponseCookie accessCookie(String token) {
        return ResponseCookie.from("euroespes_access", token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMillis(expirationMs))
                .build();
    }

    private ResponseCookie expiredAccessCookie() {
        return ResponseCookie.from("euroespes_access", "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String cookieToken(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("euroespes_access".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
