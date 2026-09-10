package com.clickclak.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica cada request a partir del JWT en el header Authorization. Confía en los claims
 * firmados sin volver a consultar la base de datos en cada llamada, salvo por una
 * comprobación contra {@link AlmacenTokensRevocados} — sin eso, cerrar sesión (HU02) no
 * tendría ningún efecto real hasta que el token expirara solo, hasta 8h después.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AlmacenTokensRevocados almacenTokensRevocados;

    public JwtAuthenticationFilter(JwtService jwtService, AlmacenTokensRevocados almacenTokensRevocados) {
        this.jwtService = jwtService;
        this.almacenTokensRevocados = almacenTokensRevocados;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            if (almacenTokensRevocados.estaRevocado(token)) {
                throw new JwtException("Token revocado por cierre de sesión");
            }
            Claims claims = jwtService.validar(token).getPayload();
            Long usuarioId = Long.valueOf(claims.getSubject());
            String rol = claims.get("rol", String.class);

            var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
            var autenticacion = new UsernamePasswordAuthenticationToken(usuarioId, null, autoridades);
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
