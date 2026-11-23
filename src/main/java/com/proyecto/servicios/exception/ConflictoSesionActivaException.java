package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.auth.ConflictoSesionResponse;
import lombok.Getter;

@Getter
public class ConflictoSesionActivaException extends RuntimeException {
    private final ConflictoSesionResponse conflicto;

    public ConflictoSesionActivaException(ConflictoSesionResponse conflicto) {
        super("Ya existe una sesión activa en otra plataforma; confirma el cierre de la sesión anterior para continuar");
        this.conflicto = conflicto;
    }
}
