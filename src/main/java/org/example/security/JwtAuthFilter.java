package org.example.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.Cookie;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = bearerToken(request.getHeader("Authorization"));
        if (token == null) {
            token = cookieToken(request.getCookies());
        }
        if (token != null) {
            if (jwtUtil.isTokenValido(token)) {
                String username = jwtUtil.extrairUsername(token);
                String role = jwtUtil.extrairRole(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String bearerToken(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
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

