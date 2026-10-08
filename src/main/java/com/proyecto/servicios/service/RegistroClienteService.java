package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.cliente.*;
import com.proyecto.servicios.exception.RegistroClienteException;
import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.repositorys.cliente.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

@Service
@RequiredArgsConstructor
public class RegistroClienteService {
    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;

    @Transactional("sfTransactionManager")
    public RegistroClienteResponse registrar(RegistroClienteRequest r) {
        if (r.fechaNacimiento().plusYears(18).isAfter(LocalDate.now())) {
            throw new RegistroClienteException("fechaNacimiento", "El cliente debe tener al menos 18 años", 400);
        }
        validarFechaIdentificador(r.curp().substring(4, 10), "curp");
        validarFechaIdentificador(r.rfc().substring(4, 10), "rfc");
        if (clientes.existsByCurp(r.curp())) duplicado("curp");
        if (clientes.existsByRfc(r.rfc())) duplicado("rfc");
        if (clientes.existsByCorreoElectronicoIgnoreCase(r.correoElectronico())) duplicado("correoElectronico");
        Cliente c = Cliente.builder().nombre(r.nombre()).segundoNombre(r.segundoNombre())
            .apellidoPaterno(r.apellidoPaterno()).apellidoMaterno(r.apellidoMaterno())
            .fechaNacimiento(r.fechaNacimiento()).curp(r.curp()).rfc(r.rfc()).sexo(r.sexo())
            .nacionalidad(r.nacionalidad()).estadoCivil(r.estadoCivil())
            .correoElectronico(r.correoElectronico()).telefonoMovil(r.telefonoMovil())
            .telefonoAlternativo(r.telefonoAlternativo())
            .informacionLaboral(InformacionLaboral.builder().ocupacion(r.informacionLaboral().ocupacion())
                .empresa(r.informacionLaboral().empresa()).ingresoMensual(r.informacionLaboral().ingresoMensual()).build())
            .estatus("ACTIVO").build();
        var d = r.domicilio();
        c.setDomicilio(Domicilio.builder().cliente(c).calle(d.calle()).numeroExterior(d.numeroExterior())
            .numeroInterior(d.numeroInterior()).colonia(d.colonia()).municipio(d.municipio())
            .estado(d.estado()).codigoPostal(d.codigoPostal()).pais(d.pais()).build());
        clientes.saveAndFlush(c);
        // El ID de la secuencia de clientes es único incluso en peticiones concurrentes.
        String numeroCuenta = String.format(java.util.Locale.ROOT, "%020d", c.getId());
        Cuenta cuenta = cuentas.saveAndFlush(Cuenta.builder().cliente(c).numeroCuenta(numeroCuenta)
            .saldo(BigDecimal.ZERO).estatus("ACTIVA").fechaCreacion(LocalDateTime.now()).build());
        return new RegistroClienteResponse(c.getId(), c.getEstatus(),
            new RegistroClienteResponse.CuentaResponse(cuenta.getNumeroCuenta(), cuenta.getSaldo(), cuenta.getEstatus()));
    }

    private void duplicado(String campo) {
        throw new RegistroClienteException(campo, "Ya existe un cliente con este dato", 409);
    }

    private void validarFechaIdentificador(String fecha, String campo) {
        try {
            // Para validar mes/día se usa el siglo 2000, incluido el 29 de febrero.
            LocalDate.parse("20" + fecha, DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));
        } catch (java.time.DateTimeException ex) {
            throw new RegistroClienteException(campo, "El identificador contiene una fecha inválida", 400);
        }
    }
}
