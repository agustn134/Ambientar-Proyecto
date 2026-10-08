package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.cliente.*;
import com.proyecto.servicios.exception.RegistroClienteException;
import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.repositorys.cliente.*;
import com.proyecto.servicios.entity.usuario.Usuario;
import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistroClienteService {
    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final CatalogoRepository catalogos;
    private static final DateTimeFormatter FECHA_IDENTIFICADOR = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    @Transactional("sfTransactionManager")
    public RegistroClienteResponse registrar(RegistroClienteRequest request) {
        validarDatosDeNegocio(request);
        verificarDuplicados(request);
        String passwordHash = passwordEncoder.encode(request.password());
        Cliente cliente = crearCliente(request);
        cliente.setDomicilio(crearDomicilio(request.domicilio(), cliente));
        clientes.saveAndFlush(cliente);
        Cuenta cuenta = cuentas.saveAndFlush(crearCuenta(cliente));
        Usuario usuario = usuarios.saveAndFlush(crearUsuario(cliente, passwordHash));
        return crearRespuesta(cliente, cuenta, usuario);
    }

    private void validarDatosDeNegocio(RegistroClienteRequest request) {
        validarCatalogo(CatalogoRepository.Tipo.SEXO,request.sexoId(),"sexoId");
        validarCatalogo(CatalogoRepository.Tipo.NACIONALIDAD,request.nacionalidadId(),"nacionalidadId");
        validarCatalogo(CatalogoRepository.Tipo.ESTADO_CIVIL,request.estadoCivilId(),"estadoCivilId");
        validarCatalogo(CatalogoRepository.Tipo.PAIS,request.domicilio().paisId(),"domicilio.paisId");
        var asentamiento=asentamiento(request.domicilio().asentamientoId());
        if (!asentamiento.codigoPostal().equals(request.domicilio().codigoPostal())) {
            throw new RegistroClienteException("domicilio.asentamientoId","El asentamiento no corresponde al código postal",400);
        }
        if (request.fechaNacimiento().plusYears(18).isAfter(LocalDate.now())) {
            throw new RegistroClienteException("fechaNacimiento", "El cliente debe tener al menos 18 años", 400);
        }
        validarFechaIdentificador(request.curp().substring(4, 10), "curp");
        validarFechaIdentificador(request.rfc().substring(request.rfc().length()-9, request.rfc().length()-3), "rfc");
    }

    private void verificarDuplicados(RegistroClienteRequest request) {
        if (clientes.existsByCurp(request.curp())) duplicado("curp");
        if (clientes.existsByRfc(request.rfc())) duplicado("rfc");
        String correo = request.correoElectronico();
        if (clientes.existsByCorreoElectronicoIgnoreCase(correo) || usuarios.existsByCorreo(correo)) {
            duplicado("correoElectronico");
        }
    }

    private Cliente crearCliente(RegistroClienteRequest request) {
        var laboral = request.informacionLaboral();
        return Cliente.builder().nombre(request.nombre()).segundoNombre(request.segundoNombre())
            .apellidoPaterno(request.apellidoPaterno()).apellidoMaterno(request.apellidoMaterno())
            .fechaNacimiento(request.fechaNacimiento()).curp(request.curp()).rfc(request.rfc()).sexoId(request.sexoId().shortValue())
            .nacionalidadId(request.nacionalidadId().shortValue()).estadoCivilId(request.estadoCivilId().shortValue())
            .correoElectronico(request.correoElectronico()).telefonoMovil(request.telefonoMovil())
            .telefonoAlternativo(request.telefonoAlternativo())
            .informacionLaboral(InformacionLaboral.builder().ocupacion(laboral.ocupacion())
                .empresa(laboral.empresa()).ingresoMensual(laboral.ingresoMensual()).build())
            .estatus("ACTIVO").build();
    }

    private Domicilio crearDomicilio(RegistroClienteRequest.DomicilioRequest request, Cliente cliente) {
        var asentamiento=asentamiento(request.asentamientoId());
        return Domicilio.builder().cliente(cliente).calle(request.calle()).numeroExterior(request.numeroExterior())
            .numeroInterior(request.numeroInterior()).colonia(asentamiento.colonia()).municipio(asentamiento.municipio())
            .estado(asentamiento.estado()).codigoPostal(request.codigoPostal()).paisId(request.paisId().shortValue())
            .asentamientoId(asentamiento.id()).build();
    }

    private void validarCatalogo(CatalogoRepository.Tipo tipo, Integer id, String campo) {
        if (!catalogos.habilitado(tipo,id)) throw new RegistroClienteException(campo,"Selecciona una opción habilitada del catálogo",400);
    }

    private CatalogoRepository.Asentamiento asentamiento(Integer id) {
        return catalogos.porId(id).orElseThrow(() -> new RegistroClienteException("domicilio.asentamientoId","Selecciona un asentamiento del catálogo postal",400));
    }

    private Cuenta crearCuenta(Cliente cliente) {
        // El ID de la secuencia de clientes es único incluso en peticiones concurrentes.
        String numeroCuenta = String.format(Locale.ROOT, "%020d", cliente.getId());
        return Cuenta.builder().cliente(cliente).numeroCuenta(numeroCuenta)
            .saldo(BigDecimal.ZERO).estatus("ACTIVA").fechaCreacion(LocalDateTime.now()).build();
    }

    private Usuario crearUsuario(Cliente cliente, String passwordHash) {
        Usuario usuario = new Usuario();
        usuario.setCliente(cliente);
        usuario.setCorreo(cliente.getCorreoElectronico());
        usuario.setPasswordHash(passwordHash);
        return usuario;
    }

    private RegistroClienteResponse crearRespuesta(Cliente cliente, Cuenta cuenta, Usuario usuario) {
        return new RegistroClienteResponse(cliente.getId(), cliente.getEstatus(),
            new RegistroClienteResponse.CuentaResponse(cuenta.getNumeroCuenta(), cuenta.getSaldo(), cuenta.getEstatus()),
            new RegistroClienteResponse.UsuarioResponse(usuario.getId(), usuario.getCorreo(), usuario.isActivo()));
    }

    private void duplicado(String campo) {
        throw new RegistroClienteException(campo, "Ya existe un cliente con este dato", 409);
    }

    private void validarFechaIdentificador(String fecha, String campo) {
        try {
            // Para validar mes/día se usa el siglo 2000, incluido el 29 de febrero.
            LocalDate.parse("20" + fecha, FECHA_IDENTIFICADOR);
        } catch (java.time.DateTimeException ex) {
            throw new RegistroClienteException(campo, "El identificador contiene una fecha inválida", 400);
        }
    }
}
