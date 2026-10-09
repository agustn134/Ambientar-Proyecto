package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PaginacionConsulta {
    @NotNull @Min(0) private Integer page=0;
    @NotNull @Min(1) @Max(100) private Integer size=20;
    @NotBlank private String ordenarPor="id";
    @NotBlank @Pattern(regexp="(?i)(ASC|DESC)") private String direccion="ASC";
}
