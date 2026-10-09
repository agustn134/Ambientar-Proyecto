package com.proyecto.servicios.repositorys.usuario;

import com.proyecto.servicios.entity.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    boolean existsByCorreo(String correo);
    Optional<Usuario> findByCorreo(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.correo = :correo")
    Optional<Usuario> buscarParaLogin(@Param("correo") String correo);

    boolean existsByIdAndActivoTrueAndVersionTokenAndClienteEstatus(Long id, long versionToken, String estatus);

    boolean existsByIdAndActivoTrueAndVersionTokenAndClienteEstatusAndRol(Long id, long versionToken, String estatus, com.proyecto.servicios.entity.usuario.RolUsuario rol);

    boolean existsByCorreoAndIdNot(String correo,Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.cliente.id=:clienteId")
    Optional<Usuario> buscarPorClienteParaModificar(@Param("clienteId") Long clienteId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id=:id")
    Optional<Usuario> buscarParaModificar(@Param("id") Long id);
}
