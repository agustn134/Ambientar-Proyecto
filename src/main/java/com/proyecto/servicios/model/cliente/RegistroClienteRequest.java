package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public record RegistroClienteRequest(
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

    @JsonDeserialize(using = TextoEstricto.class)
    @NotBlank(message = "La CURP es obligatoria")
    @Pattern(regexp = "[A-Z][AEIOUX][A-Z]{2}[0-9]{6}[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]",
        message = "La CURP no tiene un formato válido") String curp,

    @JsonDeserialize(using = TextoEstricto.class)
    @NotBlank(message = "El RFC es obligatorio")
    @Pattern(regexp = "[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}",
        message = "El RFC no tiene un formato válido") String rfc,

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
    @Valid DomicilioRequest domicilio,

    @NotNull(message = "La información laboral es obligatoria")
    @Valid LaboralRequest informacionLaboral,

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @JsonDeserialize(using = com.proyecto.servicios.model.usuario.PasswordEstricto.class)
    @com.proyecto.servicios.validation.PasswordValida(message = "La contraseña debe contener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial") String password
) {
    public RegistroClienteRequest {
        if (curp != null) curp = curp.toUpperCase(Locale.ROOT);
        if (rfc != null) rfc = rfc.toUpperCase(Locale.ROOT);
        if (correoElectronico != null) correoElectronico = correoElectronico.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegistroClienteRequest[datos personales y contraseña protegidos]";
    }

    public record DomicilioRequest(
        @JsonDeserialize(using = TextoEstricto.class)
        @NotBlank(message = "La calle es obligatoria")
        @Size(max = 100, message = "La calle no debe exceder 100 caracteres") String calle,

        @JsonDeserialize(using = TextoEstricto.class)
        @NotBlank(message = "El número exterior es obligatorio")
        @Size(max = 20, message = "El número exterior no debe exceder 20 caracteres") String numeroExterior,

        @JsonDeserialize(using = TextoEstricto.class)
        @Size(max = 20, message = "El número interior no debe exceder 20 caracteres") String numeroInterior,

        @JsonDeserialize(using = EnteroEstricto.class)
        @NotNull(message = "El ID de asentamiento es obligatorio")
        @Positive(message = "El ID de asentamiento debe ser un número positivo") Integer asentamientoId,

        @JsonDeserialize(using = TextoEstricto.class)
        @NotBlank(message = "El código postal es obligatorio")
        @Pattern(regexp = "[0-9]{5}", message = "El código postal debe contener exactamente 5 dígitos") String codigoPostal,

        @JsonDeserialize(using = EnteroEstricto.class)
        @NotNull(message = "El país es obligatorio")
        @Positive(message = "El ID de país debe ser un número positivo")
        @Max(value = 32767, message = "El ID de país excede el valor máximo permitido") Integer paisId
    ) {}

    public record LaboralRequest(
        @JsonDeserialize(using = TextoEstricto.class)
        @NotBlank(message = "La ocupación es obligatoria")
        @Size(max = 100, message = "La ocupación no debe exceder 100 caracteres") String ocupacion,

        @JsonDeserialize(using = TextoEstricto.class)
        @NotBlank(message = "La empresa es obligatoria")
        @Size(max = 100, message = "La empresa no debe exceder 100 caracteres") String empresa,

        @JsonDeserialize(using = DecimalEstricto.class)
        @NotNull(message = "El ingreso mensual es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El ingreso mensual debe ser mayor a cero")
        @Digits(integer = 13, fraction = 2, message = "El ingreso mensual debe tener máximo 13 dígitos enteros y 2 decimales") BigDecimal ingresoMensual
    ) {}
}
