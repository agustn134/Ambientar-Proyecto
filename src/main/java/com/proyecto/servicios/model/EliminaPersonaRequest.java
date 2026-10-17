package com.proyecto.servicios.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EliminaPersonaRequest {

    @NotBlank(message = "El nombre de la persona a eliminar es obligatorio")
    private String nombre;
}
