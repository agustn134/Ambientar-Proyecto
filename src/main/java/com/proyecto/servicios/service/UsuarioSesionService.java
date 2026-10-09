package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.CredencialesInvalidasException;
import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioSesionService {
    private final UsuarioRepository usuarios;
    public record Perfil(Long usuarioId, Long clienteId, String correo, boolean activo, com.proyecto.servicios.entity.usuario.RolUsuario rol) {}

    @Transactional(value="sfTransactionManager",readOnly=true)
    public Perfil consultar(long usuarioId) {
        var usuario=usuarios.findById(usuarioId).orElseThrow(CredencialesInvalidasException::new);
        if (!usuario.isActivo() || !"ACTIVO".equals(usuario.getCliente().getEstatus())) throw new CredencialesInvalidasException();
        return new Perfil(usuario.getId(), usuario.getCliente().getId(), usuario.getCorreo(), usuario.isActivo(),usuario.getRol());
    }
}
