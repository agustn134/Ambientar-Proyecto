package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PaginacionConsulta {
    @NotNull(message = "El número de página es obligatorio")
    @Min(value = 0, message = "El número de página debe ser mayor o igual a 0")
    private Integer page = 0;

    @NotNull(message = "El tamaño de página es obligatorio")
    @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
    @Max(value = 100, message = "El tamaño de página no debe exceder 100")
    private Integer size = 20;

    @NotBlank(message = "El campo de ordenamiento es obligatorio")
    private String ordenarPor = "id";

    @NotBlank(message = "La dirección de ordenamiento es obligatoria")
    @Pattern(regexp = "(?i)(ASC|DESC)", message = "La dirección debe ser ASC o DESC")
    private String direccion = "ASC";
}
