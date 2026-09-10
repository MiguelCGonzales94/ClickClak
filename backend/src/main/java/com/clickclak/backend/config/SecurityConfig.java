package com.clickclak.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.clickclak.backend.security.JwtAuthenticationFilter;
import com.clickclak.backend.security.ManejadorErroresAutenticacion;

/**
 * API REST sin estado: sin CSRF (no hay cookies de sesión que proteger), sin sesión de
 * servidor, autenticación exclusivamente por JWT. Los colaboradores no pasan por aquí con
 * usuario/contraseña — su flujo es WebAuthn (pendiente, ver dispositivo).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ManejadorErroresAutenticacion manejadorErrores;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ManejadorErroresAutenticacion manejadorErrores) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.manejadorErrores = manejadorErrores;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Orígenes de los dos frontends en desarrollo local (Vite): 5174 es el de campo y
     * 5173 el panel administrativo. Se aceptan localhost y 127.0.0.1 porque el navegador
     * integrado de Codex suele abrir los servidores locales con la IP loopback.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of(
                "http://localhost:5173", "http://localhost:5174",
                "http://127.0.0.1:5173", "http://127.0.0.1:5174"));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(CsrfConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Ojo: solo /login es público. "/api/auth/**" también cubriría "/api/auth/yo"
                        // y lo dejaría sin exigir autenticación — se detectó con un test real.
                        .requestMatchers("/api/auth/login").permitAll()
                        // HU03: quien pide o completa una recuperación de clave, por definición,
                        // todavía no puede autenticarse.
                        .requestMatchers("/api/auth/recuperacion/**").permitAll()
                        // La autenticación WebAuthn ES el mecanismo de login: no puede exigir un
                        // JWT previo. El enrolamiento ("/api/webauthn/registro/**") sí lo exige
                        // (además del rol vía @PreAuthorize) y por eso NO está aquí.
                        .requestMatchers("/api/webauthn/autenticacion/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(manejo -> manejo
                        .authenticationEntryPoint(manejadorErrores)
                        .accessDeniedHandler(manejadorErrores))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
