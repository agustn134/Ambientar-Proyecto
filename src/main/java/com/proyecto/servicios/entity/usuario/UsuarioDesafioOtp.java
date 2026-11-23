package com.proyecto.servicios.entity.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario_desafios_otp")
@Getter
@Setter
public class UsuarioDesafioOtp {
    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "otp_hash", nullable = false, length = 60)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlataformaCliente plataforma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EstadoDesafioOtp estado;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(nullable = false)
    private int reenvios;

    @Column(name = "token_confirmacion_hash", length = 60)
    private String tokenConfirmacionHash;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
