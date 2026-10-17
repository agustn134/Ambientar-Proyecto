package com.proyecto.servicios.model.usuario;

import com.proyecto.servicios.entity.usuario.RolUsuario;
import java.time.LocalDateTime;

/** Vista pública del usuario; excluye credenciales y datos internos de sesión. */
public record UsuarioResponse(Long usuarioId,Long clienteId,String correo,boolean activo,
        RolUsuario rol,LocalDateTime fechaCreacion,LocalDateTime fechaActualizacion) {}
