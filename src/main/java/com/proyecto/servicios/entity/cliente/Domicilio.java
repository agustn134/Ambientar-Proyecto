package com.proyecto.servicios.entity.cliente;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "domicilios")
public class Domicilio {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false, unique = true)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Cliente cliente;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calle", nullable = false)
    private String calle;

    @Column(name = "numero_exterior", nullable = false)
    private String numeroExterior;

    @Column(name = "numero_interior")
    private String numeroInterior;

    @Column(name = "colonia", nullable = false)
    private String colonia;

    @Column(name = "municipio", nullable = false)
    private String municipio;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "codigo_postal", nullable = false)
    private String codigoPostal;

    @Column(name = "pais_id", nullable = false)
    private Short paisId;

    @Column(name = "asentamiento_id")
    private Integer asentamientoId;
}
