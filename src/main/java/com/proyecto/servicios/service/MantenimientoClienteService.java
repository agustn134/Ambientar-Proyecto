package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.cliente.*;
import com.proyecto.servicios.entity.usuario.*;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.repositorys.cliente.*;
import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional("sfTransactionManager")
public class MantenimientoClienteService {
    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final UsuarioSesionService sesiones;
    private final ValidacionDatosCliente validacion;
    private final ConsultaClienteService consultas;
    @PersistenceContext(unitName="sfDatasource") private EntityManager entityManager;

    private record Objetivo(Cliente cliente,Usuario usuario) {}
    private Objetivo bloquearAutorizado(long id,long actorId) {
        var sesion=sesiones.consultar(actorId);
        if (sesion.rol()!=RolUsuario.EJECUTIVO && sesion.clienteId()!=id) throw new RecursoNoEncontradoException();
        // Mismo orden que login/provisión: usuario antes del cliente, sin resucitar estados obsoletos.
        var usuario=usuarios.buscarPorClienteParaModificar(id).orElse(null);
        if (usuario!=null) entityManager.refresh(usuario,LockModeType.PESSIMISTIC_WRITE);
        var cliente=clientes.buscarParaModificar(id).orElseThrow(RecursoNoEncontradoException::new);
        entityManager.refresh(cliente,LockModeType.PESSIMISTIC_WRITE);
        if (sesion.rol()!=RolUsuario.EJECUTIVO && !"ACTIVO".equals(cliente.getEstatus())) throw new RecursoNoEncontradoException();
        return new Objetivo(cliente,usuario);
    }

    public ConsultasResponse.DetalleCliente actualizar(long id,ActualizarClienteRequest r,long actorId) {
        var objetivo=bloquearAutorizado(id,actorId);
        var c=objetivo.cliente(); var usuario=objetivo.usuario();
        var postal=validacion.validar(r.fechaNacimiento(),r.sexoId(),r.nacionalidadId(),r.estadoCivilId(),r.domicilio());
        if (clientes.existsByCorreoElectronicoIgnoreCaseAndIdNot(r.correoElectronico(),id)
                || (usuario==null?usuarios.existsByCorreo(r.correoElectronico()):usuarios.existsByCorreoAndIdNot(r.correoElectronico(),usuario.getId()))) {
            throw new RegistroClienteException("correoElectronico","Ya existe un cliente con este dato",409);
        }
        boolean cambiaCorreo=!r.correoElectronico().equals(c.getCorreoElectronico());
        c.setNombre(r.nombre());c.setSegundoNombre(r.segundoNombre());c.setApellidoPaterno(r.apellidoPaterno());c.setApellidoMaterno(r.apellidoMaterno());
        c.setFechaNacimiento(r.fechaNacimiento());c.setSexoId(r.sexoId().shortValue());c.setNacionalidadId(r.nacionalidadId().shortValue());c.setEstadoCivilId(r.estadoCivilId().shortValue());
        c.setCorreoElectronico(r.correoElectronico());c.setTelefonoMovil(r.telefonoMovil());c.setTelefonoAlternativo(r.telefonoAlternativo());
        c.setInformacionLaboral(InformacionLaboral.builder().ocupacion(r.informacionLaboral().ocupacion()).empresa(r.informacionLaboral().empresa()).ingresoMensual(r.informacionLaboral().ingresoMensual()).build());
        Domicilio domicilio=c.getDomicilio();
        if (domicilio==null) { domicilio=new Domicilio();domicilio.setCliente(c);c.setDomicilio(domicilio); }
        var d=r.domicilio();
        domicilio.setCalle(d.calle());domicilio.setNumeroExterior(d.numeroExterior());domicilio.setNumeroInterior(d.numeroInterior());
        domicilio.setColonia(postal.colonia());domicilio.setMunicipio(postal.municipio());domicilio.setEstado(postal.estado());
        domicilio.setCodigoPostal(d.codigoPostal());domicilio.setAsentamientoId(d.asentamientoId());domicilio.setPaisId(d.paisId().shortValue());
        clientes.saveAndFlush(c);
        if (usuario!=null && cambiaCorreo) {
            usuario.setCorreo(r.correoElectronico());usuario.setVersionToken(usuario.getVersionToken()+1);
            usuarios.saveAndFlush(usuario);
        }
        return consultas.porId(id,actorId);
    }

    public void desactivar(long id,long actorId) {
        var objetivo=bloquearAutorizado(id,actorId);
        var c=objetivo.cliente();var usuario=objetivo.usuario();
        boolean cambia=!"INACTIVO".equals(c.getEstatus()) || (usuario!=null && usuario.isActivo());
        c.setEstatus("INACTIVO");
        for (var cuenta:cuentas.findAllByClienteIdOrderByIdAsc(id)) cuenta.setEstatus("INACTIVA");
        if (usuario!=null) {
            usuario.setActivo(false);
            if (cambia) usuario.setVersionToken(usuario.getVersionToken()+1);
        }
        clientes.flush();
    }
}
