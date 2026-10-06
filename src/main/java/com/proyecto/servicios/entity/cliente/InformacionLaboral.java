package com.proyecto.servicios.entity.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class InformacionLaboral {

    @Column(name = "ocupacion", nullable = false)
    private String ocupacion;

    @Column(name = "empresa", nullable = false)
    private String empresa;

    @Column(name = "ingreso_mensual", nullable = false)
    private BigDecimal ingresoMensual;
}
