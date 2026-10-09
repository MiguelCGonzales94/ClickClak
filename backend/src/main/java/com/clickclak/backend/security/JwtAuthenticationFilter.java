package com.clickclak.backend.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.UsuarioRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica cada request a partir del JWT en el header Authorization. Además de validar la firma
 * y consultar {@link AlmacenTokensRevocados} (sin eso, cerrar sesión (HU02) no tendría efecto real
 * hasta que el token expirara solo, hasta 8h después), consulta al usuario en la base en cada
 * llamada (HU04): un usuario desactivado pierde el acceso de inmediato aunque su token siga
 * vigente, y un cambio de rol tiene efecto en la siguiente petición. Cuesta una consulta por
 * request; se aceptó porque la base corre en la misma máquina.
 *
 * <p>Si el usuario tiene una clave temporal sin cambiar ({@code debeCambiarClave}), solo puede
 * llamar a {@link #RUTAS_CON_CLAVE_PENDIENTE}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final Set<String> RUTAS_CON_CLAVE_PENDIENTE =
            Set.of("/api/auth/cambiar-clave", "/api/auth/logout", "/api/auth/yo");

    private final JwtService jwtService;
    private final AlmacenTokensRevocados almacenTokensRevocados;
    private final UsuarioRepository usuarioRepository;
    private final ManejadorErroresAutenticacion manejadorErrores;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AlmacenTokensRevocados almacenTokensRevocados,
            UsuarioRepository usuarioRepository,
            ManejadorErroresAutenticacion manejadorErrores) {
        this.jwtService = jwtService;
        this.almacenTokensRevocados = almacenTokensRevocados;
        this.usuarioRepository = usuarioRepository;
        this.manejadorErrores = manejadorErrores;
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

            Optional<Usuario> usuario = usuarioRepository.findConRolById(usuarioId).filter(Usuario::isActivo);
            if (usuario.isEmpty()) {
                SecurityContextHolder.clearContext();
            } else {
                if (usuario.get().isDebeCambiarClave() && !RUTAS_CON_CLAVE_PENDIENTE.contains(request.getRequestURI())) {
                    SecurityContextHolder.clearContext();
                    manejadorErrores.clavePendiente(response);
                    return;
                }
                var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.get().getRol().getNombre()));
                var autenticacion = new UsernamePasswordAuthenticationToken(usuarioId, null, autoridades);
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
