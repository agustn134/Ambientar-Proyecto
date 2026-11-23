package com.proyecto.servicios.entity.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario_sesiones_activas")
@Getter
@Setter
public class UsuarioSesionActiva {
    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "sesion_id", nullable = false, unique = true)
    private UUID sesionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlataformaCliente plataforma;

    @Column(name = "version_token", nullable = false)
    private long versionToken;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "actualizada_en", nullable = false)
    private LocalDateTime actualizadaEn;
}
