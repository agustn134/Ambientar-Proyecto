package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Locale;

/** PUT completo de campos editables; los identificadores y permisos no forman parte de la actualización. */
public record ActualizarClienteRequest(
    @JsonDeserialize(using = TextoEstricto.class)
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 38, message = "El nombre debe tener entre 3 y 38 caracteres")
    @Pattern(regexp = "[\\p{L} ]+", message = "El nombre solo puede contener letras y espacios") String nombre,

    @JsonDeserialize(using = TextoEstricto.class)
    @Size(min = 3, max = 38, message = "El segundo nombre debe tener entre 3 y 38 caracteres")
    @Pattern(regexp = "[\\p{L} ]+", message = "El segundo nombre solo puede contener letras y espacios") String segundoNombre,

    @JsonDeserialize(using = TextoEstricto.class)
    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "[\\p{L} ]+", message = "El apellido paterno solo puede contener letras y espacios") String apellidoPaterno,

    @JsonDeserialize(using = TextoEstricto.class)
    @NotBlank(message = "El apellido materno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido materno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "[\\p{L} ]+", message = "El apellido materno solo puede contener letras y espacios") String apellidoMaterno,

    @JsonDeserialize(using = FechaEstricta.class)
    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser una fecha pasada") LocalDate fechaNacimiento,

    @JsonDeserialize(using = EnteroEstricto.class)
    @NotNull(message = "El sexo es obligatorio")
    @Positive(message = "El ID de sexo debe ser un número positivo")
    @Max(value = 32767, message = "El ID de sexo excede el valor máximo permitido") Integer sexoId,

    @JsonDeserialize(using = EnteroEstricto.class)
    @NotNull(message = "La nacionalidad es obligatoria")
    @Positive(message = "El ID de nacionalidad debe ser un número positivo")
    @Max(value = 32767, message = "El ID de nacionalidad excede el valor máximo permitido") Integer nacionalidadId,

    @JsonDeserialize(using = EnteroEstricto.class)
    @NotNull(message = "El estado civil es obligatorio")
    @Positive(message = "El ID de estado civil debe ser un número positivo")
    @Max(value = 32767, message = "El ID de estado civil excede el valor máximo permitido") Integer estadoCivilId,

    @JsonDeserialize(using = TextoEstricto.class)
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres") String correoElectronico,

    @JsonDeserialize(using = TextoEstricto.class)
    @NotBlank(message = "El teléfono móvil es obligatorio")
    @Pattern(regexp = "[0-9]{10}", message = "El teléfono móvil debe contener exactamente 10 dígitos") String telefonoMovil,

    @JsonDeserialize(using = TextoEstricto.class)
    @Pattern(regexp = "[0-9]{10}", message = "El teléfono alternativo debe contener exactamente 10 dígitos") String telefonoAlternativo,

    @NotNull(message = "El domicilio es obligatorio")
    @Valid RegistroClienteRequest.DomicilioRequest domicilio,

    @NotNull(message = "La información laboral es obligatoria")
    @Valid RegistroClienteRequest.LaboralRequest informacionLaboral,

    @Null(message = "La CURP no se puede modificar") String curp,
    @Null(message = "El RFC no se puede modificar") String rfc,
    @Null(message = "El número de cuenta no se puede modificar") String numeroCuenta,
    @Null(message = "El rol no se modifica por esta petición") String rol,
    @Null(message = "El estatus no se modifica por esta petición") String estatus,
    @Null(message = "Usa el endpoint de cambio de contraseña") String password
) {
    public ActualizarClienteRequest {
        if (correoElectronico != null) correoElectronico = correoElectronico.toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "ActualizarClienteRequest[datos protegidos]"; }
}
