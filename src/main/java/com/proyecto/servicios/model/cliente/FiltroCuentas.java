package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FiltroCuentas extends PaginacionConsulta {
    @Positive(message = "El ID del cliente debe ser un número positivo")
    private Long clienteId;

    private Boolean activo;
}
