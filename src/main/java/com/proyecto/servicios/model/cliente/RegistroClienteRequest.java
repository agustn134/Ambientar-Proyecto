package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public record RegistroClienteRequest(
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Size(min=3, max=38) @Pattern(regexp="[\\p{L} ]+") String nombre,
    @JsonDeserialize(using = TextoEstricto.class) @Size(min=3, max=38) @Pattern(regexp="[\\p{L} ]+") String segundoNombre,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Size(min=2, max=50) @Pattern(regexp="[\\p{L} ]+") String apellidoPaterno,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Size(min=2, max=50) @Pattern(regexp="[\\p{L} ]+") String apellidoMaterno,
    @JsonDeserialize(using = FechaEstricta.class) @NotNull @Past LocalDate fechaNacimiento,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Pattern(regexp="[A-Z][AEIOUX][A-Z]{2}[0-9]{6}[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]") String curp,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Pattern(regexp="[A-ZÑ&]{4}[0-9]{6}[A-Z0-9]{3}") String rfc,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Size(max=20) String sexo,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Size(max=50) String nacionalidad,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Size(max=30) String estadoCivil,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Email @Size(max=100) String correoElectronico,
    @JsonDeserialize(using = TextoEstricto.class) @NotBlank @Pattern(regexp="[0-9]{10}") String telefonoMovil,
    @JsonDeserialize(using = TextoEstricto.class) @Pattern(regexp="[0-9]{10}") String telefonoAlternativo,
    @NotNull @Valid DomicilioRequest domicilio,
    @NotNull @Valid LaboralRequest informacionLaboral
) {
    public RegistroClienteRequest {
        if (curp != null) curp = curp.toUpperCase(Locale.ROOT);
        if (rfc != null) rfc = rfc.toUpperCase(Locale.ROOT);
        if (correoElectronico != null) correoElectronico = correoElectronico.toLowerCase(Locale.ROOT);
    }

    public record DomicilioRequest(
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=100) String calle,
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=20) String numeroExterior,
        @JsonDeserialize(using=TextoEstricto.class) @Size(max=20) String numeroInterior,
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=100) String colonia,
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=100) String municipio,
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=100) String estado,
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Pattern(regexp="[0-9]{5}") String codigoPostal,
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=50) String pais
    ) {}

    public record LaboralRequest(
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=100) String ocupacion,
        @JsonDeserialize(using=TextoEstricto.class) @NotBlank @Size(max=100) String empresa,
        @JsonDeserialize(using=DecimalEstricto.class) @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=13, fraction=2) BigDecimal ingresoMensual
    ) {}
}
