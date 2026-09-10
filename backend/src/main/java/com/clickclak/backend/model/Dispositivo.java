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

/**
 * Credencial WebAuthn del dispositivo autorizado de un colaborador. La biometría la
 * verifica el sistema operativo del dispositivo; aquí solo se guarda la clave pública
 * de la credencial para validar la firma de cada marcación — nunca una plantilla biométrica.
 */
@Entity
@Table(name = "dispositivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private String credentialId;

    @Column(columnDefinition = "text")
    private String clavePublica;

    @Builder.Default
    private long contadorFirma = 0L;

    private String nombreDispositivo;

    @Builder.Default
    private boolean activo = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant registradoEn;
}
