package com.proyecto.servicios.model.usuario;

import com.proyecto.servicios.entity.usuario.RolUsuario;
import jakarta.validation.constraints.*;

public record BuscarUsuariosRequest(
    @Positive(message="El ID debe ser mayor que cero") Long id,
    @Email(message="El correo no tiene un formato válido") @Size(max=100,message="El correo no debe exceder 100 caracteres") String correo,
    Boolean activo,
    RolUsuario rol,
    @Min(value=0,message="La página no puede ser negativa") int page,
    @Min(value=1,message="El tamaño debe ser mayor que cero") @Max(value=100,message="El tamaño máximo es 100") Integer size
) {
    public BuscarUsuariosRequest { if(size==null) size=20; }
}
