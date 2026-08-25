package org.example.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityAuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        filterChain.doFilter(request, response);

        if (!isAuditablePath(request.getRequestURI())) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = (authentication != null && authentication.isAuthenticated())
                ? authentication.getName()
                : "anonymous";

        // Audit log intentionally avoids request/response bodies to prevent PII leakage.
        log.info("AUDIT method={} path={} status={} principal={} elapsedMs={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                principal,
                System.currentTimeMillis() - start);
    }

    private boolean isAuditablePath(String path) {
        return path.startsWith("/api/pacientes")
                || path.startsWith("/api/estoque")
                || path.startsWith("/api/auth/login");
    }
}

