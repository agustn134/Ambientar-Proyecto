package com.proyecto.servicios.entity.usuario;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.proyecto.servicios.entity.cliente.Cliente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="usuarios")
@Getter
@Setter
public class Usuario {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="cliente_id", nullable=false, unique=true)
    @JsonIgnore
    private Cliente cliente;

    @Column(nullable=false, unique=true, length=100)
    private String correo;

    @Column(name="password_hash", nullable=false, length=60)
    @JsonIgnore
    private String passwordHash;

    @Column(nullable=false)
    private boolean activo=true;

    @Column(name="intentos_fallidos", nullable=false)
    private int intentosFallidos=0;

    @Column(name="version_token", nullable=false)
    @JsonIgnore
    private long versionToken=0;

    @Column(name="fecha_creacion", nullable=false, updatable=false)
    private LocalDateTime fechaCreacion;

    @Column(name="fecha_actualizacion", nullable=false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    void crear() {
        fechaCreacion=LocalDateTime.now();
        fechaActualizacion=fechaCreacion;
    }

    @PreUpdate
    void actualizar() { fechaActualizacion=LocalDateTime.now(); }
}
