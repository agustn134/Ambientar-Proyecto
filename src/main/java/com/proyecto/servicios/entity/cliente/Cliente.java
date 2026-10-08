package com.proyecto.servicios.entity.cliente;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Datos Personales
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "segundo_nombre")
    private String segundoNombre;

    @Column(name = "apellido_paterno", nullable = false)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", nullable = false)
    private String apellidoMaterno;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "curp", nullable = false, unique = true)
    private String curp;

    @Column(name = "rfc", nullable = false, unique = true)
    private String rfc;

    @Column(name = "sexo_id", nullable = false)
    private Short sexoId;

    @Column(name = "nacionalidad_id", nullable = false)
    private Short nacionalidadId;

    @Column(name = "estado_civil_id", nullable = false)
    private Short estadoCivilId;

    // Datos de Contacto
    @Column(name = "correo_electronico", nullable = false, unique = true)
    private String correoElectronico;

    @Column(name = "telefono_movil", nullable = false)
    private String telefonoMovil;

    @Column(name = "telefono_alternativo")
    private String telefonoAlternativo;

    // Domicilio
    @OneToOne(mappedBy = "cliente", cascade = CascadeType.ALL)
    private Domicilio domicilio;

    // Información Laboral
    @Embedded
    private InformacionLaboral informacionLaboral;

    @Builder.Default
    @Column(name = "estatus", nullable = false)
    private String estatus = "ACTIVO";
}
