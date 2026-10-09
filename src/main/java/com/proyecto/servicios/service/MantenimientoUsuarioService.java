package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.usuario.RolUsuario;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.usuario.CambiarPasswordRequest;
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
    public record UsuarioResponse(Long usuarioId,Long clienteId,String correo,boolean activo,RolUsuario rol,LocalDateTime fechaCreacion,LocalDateTime fechaActualizacion) {}

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
