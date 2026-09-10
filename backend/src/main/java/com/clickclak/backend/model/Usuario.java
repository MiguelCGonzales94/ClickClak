package com.clickclak.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombres;

    private String apellidos;

    private String tipoDocumento;

    private String numeroDocumento;

    private String correo;

    /** Nulo para colaboradores: su acceso es solo por WebAuthn (ver {@link Dispositivo}). */
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Builder.Default
    private boolean activo = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    private Instant actualizadoEn;
}
