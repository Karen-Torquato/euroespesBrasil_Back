package org.example.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final int maxAttempts;
    private final long windowSeconds;

    private final Map<String, AttemptWindow> attemptsByIp = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(
            @Value("${app.security.login.max-attempts:8}") int maxAttempts,
            @Value("${app.security.login.window-seconds:60}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isLoginRequest(request) || maxAttempts <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        long now = Instant.now().getEpochSecond();

        AttemptWindow window = attemptsByIp.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStartEpoch > windowSeconds) {
                return new AttemptWindow(now, 1);
            }
            existing.count++;
            return existing;
        });

        if (window.count > maxAttempts) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Muitas tentativas de login. Tente novamente em instantes.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class AttemptWindow {
        private final long windowStartEpoch;
        private int count;

        private AttemptWindow(long windowStartEpoch, int count) {
            this.windowStartEpoch = windowStartEpoch;
            this.count = count;
        }
    }
}

