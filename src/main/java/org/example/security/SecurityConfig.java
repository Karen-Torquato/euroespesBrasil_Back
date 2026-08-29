package org.example.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final SecurityAuditFilter securityAuditFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.security.require-ssl:false}")
    private boolean requireSsl;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          LoginRateLimitFilter loginRateLimitFilter,
                          SecurityAuditFilter securityAuditFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.loginRateLimitFilter = loginRateLimitFilter;
        this.securityAuditFilter = securityAuditFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF desabilitado — API stateless com JWT
            .csrf(csrf -> csrf.disable())

            // CORS centralizado nesta config
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Sem sessão HTTP (stateless)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Regras de autorização
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/pacientes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/estoque/movimentar").hasAnyRole("ADMIN", "RECEPCAO")
                .requestMatchers(HttpMethod.GET, "/api/pacientes/**", "/api/estoque/**")
                    .hasAnyRole("ADMIN", "MEDICO", "RECEPCAO", "LEITURA")
                .requestMatchers(HttpMethod.POST, "/api/pacientes/**")
                    .hasAnyRole("ADMIN", "MEDICO", "RECEPCAO")
                .requestMatchers(HttpMethod.PUT, "/api/pacientes/**")
                    .hasAnyRole("ADMIN", "MEDICO", "RECEPCAO")
                .anyRequest().authenticated()
            )

            // Security headers
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(ct -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(ref ->
                    ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            )

            .requiresChannel(channel -> {
                if (requireSsl) {
                    channel.anyRequest().requiresSecure();
                }
            })

            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            // Filtro JWT antes do filtro padrão
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(securityAuditFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = List.of(allowedOrigins.split(","))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        if (origins.isEmpty()) {
            origins = List.of("http://localhost:4200", "http://127.0.0.1:4200");
        }

        config.setAllowedOrigins(origins);
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

