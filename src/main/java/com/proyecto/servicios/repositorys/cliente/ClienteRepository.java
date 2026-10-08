package com.proyecto.servicios.repositorys.cliente;

import com.proyecto.servicios.entity.cliente.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByCurp(String curp);
    boolean existsByRfc(String rfc);
    boolean existsByCorreoElectronico(String correoElectronico);
    boolean existsByCorreoElectronicoIgnoreCase(String correoElectronico);
    
    Optional<Cliente> findByCurp(String curp);
    Optional<Cliente> findByRfc(String rfc);
}
