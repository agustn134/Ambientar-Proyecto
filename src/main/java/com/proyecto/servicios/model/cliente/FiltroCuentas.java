package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class FiltroCuentas extends PaginacionConsulta {
    @Positive private Long clienteId;
    private Boolean activo;
}
