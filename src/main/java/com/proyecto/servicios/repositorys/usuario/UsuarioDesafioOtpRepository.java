package com.proyecto.servicios.repositorys.usuario;

import com.proyecto.servicios.entity.usuario.EstadoDesafioOtp;
import com.proyecto.servicios.entity.usuario.UsuarioDesafioOtp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioDesafioOtpRepository extends JpaRepository<UsuarioDesafioOtp, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from UsuarioDesafioOtp d where d.id = :id")
    Optional<UsuarioDesafioOtp> buscarPorIdParaModificar(@Param("id") UUID id);

    List<UsuarioDesafioOtp> findAllByUsuarioIdAndEstadoIn(Long usuarioId, Collection<EstadoDesafioOtp> estados);
}
