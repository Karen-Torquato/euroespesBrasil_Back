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
public class UploadRateLimitFilter extends OncePerRequestFilter {

    private final int maxUploads;
    private final long windowSeconds;
    private final Map<String, AttemptWindow> attemptsByIp = new ConcurrentHashMap<>();

    public UploadRateLimitFilter(
            @Value("${app.security.upload.max-attempts:20}") int maxUploads,
            @Value("${app.security.upload.window-seconds:60}") long windowSeconds) {
        this.maxUploads = maxUploads;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isUploadRequest(request) || maxUploads <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = Instant.now().getEpochSecond();
        String key = clientIp(request);
        AttemptWindow window = attemptsByIp.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt > windowSeconds) {
                return new AttemptWindow(now, 1);
            }
            current.count++;
            return current;
        });

        if (window.count > maxUploads) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Muitos uploads. Tente novamente em instantes.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isUploadRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/pacientes/[0-9]+/anexo");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }

    private static final class AttemptWindow {
        private final long startedAt;
        private int count;

        private AttemptWindow(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
