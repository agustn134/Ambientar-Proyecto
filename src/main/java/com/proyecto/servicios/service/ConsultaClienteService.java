package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Cuenta;
import com.proyecto.servicios.entity.usuario.RolUsuario;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.RegistroClienteException;
import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.model.cliente.ConsultasResponse.*;
import com.proyecto.servicios.repositorys.cliente.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(value="sfTransactionManager",readOnly=true)
public class ConsultaClienteService {
    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;
    private final UsuarioSesionService sesiones;

    public Pagina<ResumenCliente> listar(FiltroClientes filtro,long usuarioId) {
        exigirEjecutivo(usuarioId);
        LocalDate desde=fecha(filtro.getDesde(),"desde");
        LocalDate hasta=fecha(filtro.getHasta(),"hasta");
        if (desde!=null && hasta!=null && desde.isAfter(hasta)) invalido("desde","Desde no puede ser posterior a hasta");
        var pagina=clientes.findAll(filtrarClientes(filtro,desde,hasta),paginacion(filtro,Set.of("id","nombre","fechaRegistro")));
        return new Pagina<>(pagina.getContent().stream().map(this::resumen).toList(),pagina.getNumber(),pagina.getSize(),pagina.getTotalElements(),pagina.getTotalPages());
    }

    public DetalleCliente propio(long usuarioId) { return porId(sesiones.consultar(usuarioId).clienteId(),usuarioId); }

    public DetalleCliente buscar(FiltroClientes f,long usuarioId) {
        if (f.getCurp()==null && f.getRfc()==null && f.getCorreo()==null && f.getNumeroCuenta()==null) {
            invalido("busqueda","Indica CURP, RFC, correo o número de cuenta");
        }
        var sesion=sesiones.consultar(usuarioId);
        LocalDate desde=fecha(f.getDesde(),"desde"),hasta=fecha(f.getHasta(),"hasta");
        if (desde!=null && hasta!=null && desde.isAfter(hasta)) invalido("desde","Desde no puede ser posterior a hasta");
        Specification<Cliente> spec=filtrarClientes(f,desde,hasta);
        if (sesion.rol()!=RolUsuario.EJECUTIVO) spec=spec.and((r,q,cb) -> cb.equal(r.get("id"),sesion.clienteId()));
        return detalle(clientes.findOne(spec).orElseThrow(RecursoNoEncontradoException::new));
    }

    public DetalleCliente porId(long clienteId,long usuarioId) {
        var sesion=sesiones.consultar(usuarioId);
        if (sesion.rol()!=RolUsuario.EJECUTIVO && sesion.clienteId()!=clienteId) throw new RecursoNoEncontradoException();
        return detalle(clientes.findById(clienteId).orElseThrow(RecursoNoEncontradoException::new));
    }

    public List<ConsultasResponse.Cuenta> cuentasDeCliente(long clienteId,long usuarioId) {
        porId(clienteId,usuarioId); // autorización y existencia idénticas a la consulta individual
        return cuentas.findAllByClienteIdOrderByIdAsc(clienteId).stream().map(this::cuenta).toList();
    }

    public ConsultasResponse.Cuenta porNumeroCuenta(String numeroCuenta,long usuarioId) {
        var sesion=sesiones.consultar(usuarioId);
        Cuenta cuenta=cuentas.findByNumeroCuenta(numeroCuenta).orElseThrow(RecursoNoEncontradoException::new);
        if (sesion.rol()!=RolUsuario.EJECUTIVO && !sesion.clienteId().equals(cuenta.getCliente().getId())) throw new RecursoNoEncontradoException();
        return cuenta(cuenta);
    }

    public Pagina<ConsultasResponse.Cuenta> listarCuentas(FiltroCuentas filtro,long usuarioId) {
        exigirEjecutivo(usuarioId);
        Specification<Cuenta> spec=(root,query,cb) -> {
            var condiciones=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (filtro.getClienteId()!=null) condiciones.add(cb.equal(root.get("cliente").get("id"),filtro.getClienteId()));
            if (filtro.getActivo()!=null) condiciones.add(cb.equal(root.get("estatus"),filtro.getActivo()?"ACTIVA":"INACTIVA"));
            return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var pagina=cuentas.findAll(spec,paginacion(filtro,Set.of("id","fechaCreacion","numeroCuenta")));
        return new Pagina<>(pagina.getContent().stream().map(this::cuenta).toList(),pagina.getNumber(),pagina.getSize(),pagina.getTotalElements(),pagina.getTotalPages());
    }

    private void exigirEjecutivo(long usuarioId) {
        if (sesiones.consultar(usuarioId).rol()!=RolUsuario.EJECUTIVO) throw new AccessDeniedException("Acceso no disponible");
    }

    private Specification<Cliente> filtrarClientes(FiltroClientes f,LocalDate desde,LocalDate hasta) {
        return (root,query,cb) -> {
            var p=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (f.getCurp()!=null) p.add(cb.equal(root.get("curp"),f.getCurp().toUpperCase(Locale.ROOT)));
            if (f.getRfc()!=null) p.add(cb.equal(root.get("rfc"),f.getRfc().toUpperCase(Locale.ROOT)));
            if (f.getCorreo()!=null) p.add(cb.equal(cb.lower(root.get("correoElectronico")),f.getCorreo().toLowerCase(Locale.ROOT)));
            if (f.getNombre()!=null) p.add(cb.like(cb.lower(root.get("nombre")),"%"+f.getNombre().toLowerCase(Locale.ROOT)+"%"));
            if (f.getActivo()!=null) p.add(cb.equal(root.get("estatus"),f.getActivo()?"ACTIVO":"INACTIVO"));
            if (desde!=null) p.add(cb.greaterThanOrEqualTo(root.get("fechaRegistro"),desde.atStartOfDay()));
            if (hasta!=null) p.add(cb.lessThan(root.get("fechaRegistro"),hasta.plusDays(1).atStartOfDay()));
            if (f.getNumeroCuenta()!=null) {
                var sub=query.subquery(Long.class);
                var cuenta=sub.from(Cuenta.class);
                sub.select(cuenta.get("id")).where(cb.equal(cuenta.get("cliente").get("id"),root.get("id")),cb.equal(cuenta.get("numeroCuenta"),f.getNumeroCuenta()));
                p.add(cb.exists(sub));
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Pageable paginacion(PaginacionConsulta f,Set<String> permitidos) {
        if (!permitidos.contains(f.getOrdenarPor())) invalido("ordenarPor","Campo de ordenamiento no permitido");
        var direccion=Sort.Direction.fromString(f.getDireccion());
        Sort orden=Sort.by(direccion,f.getOrdenarPor());
        if (!"id".equals(f.getOrdenarPor())) orden=orden.and(Sort.by(direccion,"id"));
        return PageRequest.of(f.getPage(),f.getSize(),orden);
    }

    private LocalDate fecha(String valor,String campo) {
        if (valor==null) return null;
        try { return LocalDate.parse(valor); }
        catch (java.time.DateTimeException ex) { throw new RegistroClienteException(campo,"Fecha inválida; utiliza YYYY-MM-DD",400); }
    }

    private void invalido(String campo,String mensaje) { throw new RegistroClienteException(campo,mensaje,400); }

    private ResumenCliente resumen(Cliente c) {
        return new ResumenCliente(c.getId(),c.getNombre(),c.getSegundoNombre(),c.getApellidoPaterno(),c.getApellidoMaterno(),c.getCurp(),c.getRfc(),c.getCorreoElectronico(),c.getEstatus(),c.getFechaRegistro());
    }

    private DetalleCliente detalle(Cliente c) {
        var d=c.getDomicilio(); var l=c.getInformacionLaboral();
        return new DetalleCliente(resumen(c),c.getFechaNacimiento(),c.getSexoId(),c.getNacionalidadId(),c.getEstadoCivilId(),c.getTelefonoMovil(),c.getTelefonoAlternativo(),
            d==null?null:new ConsultasResponse.Domicilio(d.getCalle(),d.getNumeroExterior(),d.getNumeroInterior(),d.getColonia(),d.getMunicipio(),d.getEstado(),d.getCodigoPostal(),d.getPaisId(),d.getAsentamientoId()),
            l==null?null:new Laboral(l.getOcupacion(),l.getEmpresa(),l.getIngresoMensual()),cuentas.findAllByClienteIdOrderByIdAsc(c.getId()).stream().map(this::cuenta).toList());
    }

    private ConsultasResponse.Cuenta cuenta(Cuenta c) { return new ConsultasResponse.Cuenta(c.getNumeroCuenta(),c.getCliente().getId(),c.getSaldo(),c.getEstatus(),c.getFechaCreacion()); }
}
