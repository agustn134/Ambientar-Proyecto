package com.proyecto.servicios.model.cliente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Entradas por body; ninguna entidad de persistencia forma parte del contrato. */
public final class OperacionRequest {
    private OperacionRequest() {}
    public record Id(@NotNull(message="El ID es obligatorio") @Positive(message="El ID debe ser mayor que cero") Long id) {}
    public record Cuenta(@NotBlank(message="La cuenta es obligatoria") @Pattern(regexp="[0-9]{20}",message="La cuenta debe contener veinte dígitos") String numeroCuenta) {}
    public record Postal(@NotBlank(message="El código postal es obligatorio") @Pattern(regexp="[0-9]{5}",message="El código postal debe contener cinco dígitos") String codigoPostal) {}
    public record Actualizar(@NotNull(message="El ID es obligatorio") @Positive Long id,
        @NotNull(message="Los datos del cliente son obligatorios") @Valid ActualizarClienteRequest datos) {}
}
