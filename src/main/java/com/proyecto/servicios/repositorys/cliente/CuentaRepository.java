package com.proyecto.servicios.repositorys.cliente;

import com.proyecto.servicios.entity.cliente.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
    boolean existsByNumeroCuenta(String numeroCuenta);
    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
    Optional<Cuenta> findByClienteId(Long clienteId);
}
