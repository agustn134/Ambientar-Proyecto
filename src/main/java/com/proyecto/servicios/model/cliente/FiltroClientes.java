package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class FiltroClientes extends PaginacionConsulta {
    @Pattern(regexp="[A-Za-z0-9]{18}") private String curp;
    @Pattern(regexp="[A-Za-zÑñ&]{3,4}[0-9]{6}[A-Za-z0-9]{3}") private String rfc;
    @Email @Size(max=100) private String correo;
    @Pattern(regexp="[0-9]{20}") private String numeroCuenta;
    @Size(max=50) @Pattern(regexp="[\\p{L} ]+") private String nombre;
    private Boolean activo;
    @Pattern(regexp="[0-9]{4}-[0-9]{2}-[0-9]{2}") private String desde;
    @Pattern(regexp="[0-9]{4}-[0-9]{2}-[0-9]{2}") private String hasta;
}
