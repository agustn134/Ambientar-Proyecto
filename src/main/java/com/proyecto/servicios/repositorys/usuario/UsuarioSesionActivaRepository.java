package com.proyecto.servicios.repositorys.usuario;

import com.proyecto.servicios.entity.usuario.UsuarioSesionActiva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioSesionActivaRepository extends JpaRepository<UsuarioSesionActiva, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UsuarioSesionActiva s where s.usuarioId = :usuarioId")
    Optional<UsuarioSesionActiva> buscarPorUsuarioIdParaModificar(@Param("usuarioId") Long usuarioId);

    boolean existsByUsuarioIdAndSesionIdAndVersionTokenAndActivaTrueAndExpiraEnAfter(
        Long usuarioId,
        UUID sesionId,
        long versionToken,
        LocalDateTime ahora
    );
}
