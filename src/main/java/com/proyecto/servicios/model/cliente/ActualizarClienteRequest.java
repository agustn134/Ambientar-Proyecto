package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Locale;

/** PUT completo de campos editables; los identificadores y permisos no forman parte de la actualización. */
public record ActualizarClienteRequest(
    @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(min=3,max=38) @Pattern(regexp="[\\p{L} ]+") String nombre,
    @JsonDeserialize(using=TextoEstricto.class) @Size(min=3,max=38) @Pattern(regexp="[\\p{L} ]+") String segundoNombre,
    @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(min=2,max=50) @Pattern(regexp="[\\p{L} ]+") String apellidoPaterno,
    @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(min=2,max=50) @Pattern(regexp="[\\p{L} ]+") String apellidoMaterno,
    @JsonDeserialize(using=FechaEstricta.class) @NotNull @Past LocalDate fechaNacimiento,
    @JsonDeserialize(using=EnteroEstricto.class) @NotNull @Positive @Max(32767) Integer sexoId,
    @JsonDeserialize(using=EnteroEstricto.class) @NotNull @Positive @Max(32767) Integer nacionalidadId,
    @JsonDeserialize(using=EnteroEstricto.class) @NotNull @Positive @Max(32767) Integer estadoCivilId,
    @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Email @Size(max=100) String correoElectronico,
    @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Pattern(regexp="[0-9]{10}") String telefonoMovil,
    @JsonDeserialize(using=TextoEstricto.class) @Pattern(regexp="[0-9]{10}") String telefonoAlternativo,
    @NotNull @Valid RegistroClienteRequest.DomicilioRequest domicilio,
    @NotNull @Valid RegistroClienteRequest.LaboralRequest informacionLaboral,
    @Null(message="CURP no se puede modificar") String curp,
    @Null(message="RFC no se puede modificar") String rfc,
    @Null(message="Número de cuenta no se puede modificar") String numeroCuenta,
    @Null(message="El rol no se modifica por esta petición") String rol,
    @Null(message="El estatus no se modifica por esta petición") String estatus,
    @Null(message="Usa el endpoint de cambio de contraseña") String password
) {
    public ActualizarClienteRequest {
        if (correoElectronico!=null) correoElectronico=correoElectronico.toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "ActualizarClienteRequest[datos protegidos]"; }
}
