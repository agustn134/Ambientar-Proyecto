package com.proyecto.servicios.repositorys.cliente;

import com.proyecto.servicios.entity.cliente.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Cliente> {
    boolean existsByCurp(String curp);
    boolean existsByRfc(String rfc);
    boolean existsByCorreoElectronico(String correoElectronico);
    boolean existsByCorreoElectronicoIgnoreCase(String correoElectronico);
    
    Optional<Cliente> findByCurp(String curp);
    Optional<Cliente> findByRfc(String rfc);
    boolean existsByCorreoElectronicoIgnoreCaseAndIdNot(String correo,Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cliente c where c.id=:id")
    Optional<Cliente> buscarParaModificar(@org.springframework.data.repository.query.Param("id") Long id);
}
