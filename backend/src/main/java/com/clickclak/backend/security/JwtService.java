package com.clickclak.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.clickclak.backend.model.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite y valida los JWT del sistema. Un solo mecanismo (HS256, clave simétrica) para
 * ambos frontends — no hay necesidad de distribuir una clave pública porque solo este
 * backend firma y verifica los tokens.
 */
@Service
public class JwtService {

    private final SecretKey clavePrivada;
    private final long expiracionMinutos;

    public JwtService(
            @Value("${clickclak.seguridad.jwt.secret}") String secreto,
            @Value("${clickclak.seguridad.jwt.expiracion-minutos}") long expiracionMinutos) {
        this.clavePrivada = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMinutos = expiracionMinutos;
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("rol", usuario.getRol().getNombre())
                .claim("correo", usuario.getCorreo())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expiracionMinutos, ChronoUnit.MINUTES)))
                .signWith(clavePrivada)
                .compact();
    }

    /** Lanza {@link io.jsonwebtoken.JwtException} si el token es inválido, alterado o expiró. */
    public Jws<Claims> validar(String token) {
        return Jwts.parser().verifyWith(clavePrivada).build().parseSignedClaims(token);
    }

    public long getExpiracionMinutos() {
        return expiracionMinutos;
    }
}
