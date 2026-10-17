package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FiltroClientes extends PaginacionConsulta {
    @Pattern(regexp = "[A-Za-z0-9]{18}", message = "La CURP debe contener exactamente 18 caracteres alfanuméricos")
    private String curp;

    @Pattern(regexp = "[A-Za-zÑñ&]{3,4}[0-9]{6}[A-Za-z0-9]{3}", message = "El RFC no tiene un formato válido")
    private String rfc;

    @Email(message = "El correo electrónico no tiene un formato válido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres")
    private String correo;

    @Pattern(regexp = "[0-9]{20}", message = "El número de cuenta debe contener exactamente 20 dígitos")
    private String numeroCuenta;

    @Size(max = 50, message = "El nombre no debe exceder 50 caracteres")
    @Pattern(regexp = "[\\p{L} ]+", message = "El nombre solo puede contener letras y espacios")
    private String nombre;

    private Boolean activo;

    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}", message = "La fecha 'desde' debe tener formato YYYY-MM-DD")
    private String desde;

    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}", message = "La fecha 'hasta' debe tener formato YYYY-MM-DD")
    private String hasta;
}
