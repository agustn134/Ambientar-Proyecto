package com.proyecto.servicios.exception;

/** Respuesta uniforme para inexistente y fuera del ámbito propio, sin revelar datos ajenos. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException() { super("Recurso no encontrado"); }
}
