package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs explícitos: nunca se serializan entidades de usuario, contraseñas o proxies JPA. */
public final class ConsultasResponse {
    private ConsultasResponse() {}
    public record Pagina<T>(List<T> contenido,int page,int size,long totalElementos,int totalPaginas) {}
    public record ResumenCliente(Long clienteId,String nombre,String segundoNombre,String apellidoPaterno,
                                 String apellidoMaterno,String curp,String rfc,String correo,String estatus,LocalDateTime fechaRegistro) {}
    public record Domicilio(String calle,String numeroExterior,String numeroInterior,String colonia,String municipio,
                            String estado,String codigoPostal,Short paisId,Integer asentamientoId) {}
    public record Laboral(String ocupacion,String empresa,BigDecimal ingresoMensual) {}
    public record Cuenta(String numeroCuenta,Long clienteId,BigDecimal saldo,String estatus,LocalDateTime fechaCreacion) {}
    public record Saldo(String numeroCuenta,BigDecimal saldo) {}
    public record DetalleCliente(ResumenCliente cliente,LocalDate fechaNacimiento,Short sexoId,Short nacionalidadId,
                                 Short estadoCivilId,String telefonoMovil,String telefonoAlternativo,Domicilio domicilio,
                                 Laboral informacionLaboral,List<Cuenta> cuentas) {}
}
