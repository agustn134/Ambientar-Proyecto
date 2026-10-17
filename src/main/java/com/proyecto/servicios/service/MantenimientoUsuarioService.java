package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.usuario.RolUsuario;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.usuario.CambiarPasswordRequest;
import com.proyecto.servicios.model.usuario.UsuarioResponse;
import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MantenimientoUsuarioService {
    private final UsuarioRepository usuarios;
    private final UsuarioSesionService sesiones;
    private final PasswordEncoder passwords;
    @PersistenceContext(unitName="sfDatasource") private EntityManager entityManager;

    @Transactional(value="sfTransactionManager",readOnly=true)
    public com.proyecto.servicios.model.cliente.ConsultasResponse.Pagina<UsuarioResponse> buscar(
            com.proyecto.servicios.model.usuario.BuscarUsuariosRequest filtro,long actorId) {
        var actor=sesiones.consultar(actorId);
        if(filtro.id()!=null) consultar(filtro.id(),actorId);
        org.springframework.data.jpa.domain.Specification<com.proyecto.servicios.entity.usuario.Usuario> especificacion=(root,query,cb)-> {
            var condiciones=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if(actor.rol()!=RolUsuario.EJECUTIVO) condiciones.add(cb.equal(root.get("id"),actorId));
            if(filtro.id()!=null) condiciones.add(cb.equal(root.get("id"),filtro.id()));
            if(filtro.correo()!=null) condiciones.add(cb.equal(cb.lower(root.get("correo")),filtro.correo().trim().toLowerCase(java.util.Locale.ROOT)));
            if(filtro.activo()!=null) condiciones.add(cb.equal(root.get("activo"),filtro.activo()));
            if(filtro.rol()!=null) condiciones.add(cb.equal(root.get("rol"),filtro.rol()));
            return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var pagina=usuarios.findAll(especificacion,org.springframework.data.domain.PageRequest.of(filtro.page(),filtro.size(),org.springframework.data.domain.Sort.by("id")));
        var contenido=pagina.getContent().stream().map(u->new UsuarioResponse(u.getId(),u.getCliente().getId(),u.getCorreo(),u.isActivo(),u.getRol(),u.getFechaCreacion(),u.getFechaActualizacion())).toList();
        return new com.proyecto.servicios.model.cliente.ConsultasResponse.Pagina<>(contenido,pagina.getNumber(),pagina.getSize(),pagina.getTotalElements(),pagina.getTotalPages());
    }
    @Transactional(value="sfTransactionManager",readOnly=true)
    public UsuarioResponse consultar(long id,long actorId) {
        var sesion=sesiones.consultar(actorId);
        if (sesion.rol()!=RolUsuario.EJECUTIVO && id!=actorId) throw new RecursoNoEncontradoException();
        var u=usuarios.findById(id).orElseThrow(RecursoNoEncontradoException::new);
        return new UsuarioResponse(u.getId(),u.getCliente().getId(),u.getCorreo(),u.isActivo(),u.getRol(),u.getFechaCreacion(),u.getFechaActualizacion());
    }

    @Transactional("sfTransactionManager")
    public void cambiarPassword(long id,CambiarPasswordRequest r,long actorId) {
        sesiones.consultar(actorId);
        // EJECUTIVO también necesita ser propietario; no es un endpoint de restablecimiento administrativo.
        if (id!=actorId) throw new RecursoNoEncontradoException();
        var u=usuarios.buscarParaModificar(id).orElseThrow(RecursoNoEncontradoException::new);
        entityManager.refresh(u,LockModeType.PESSIMISTIC_WRITE);
        if (!u.isActivo() || !"ACTIVO".equals(u.getCliente().getEstatus()) || !passwords.matches(r.passwordActual(),u.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        u.setPasswordHash(passwords.encode(r.passwordNueva()));
        u.setVersionToken(u.getVersionToken()+1);
        usuarios.saveAndFlush(u);
    }
}
