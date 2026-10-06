package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.cliente.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteService {
    Cliente registrarCliente(Cliente cliente);
    
    // Consulta
    List<Cliente> obtenerTodos();
    Optional<Cliente> obtenerPorId(Long id);
    Optional<Cliente> obtenerPorCurp(String curp);
    Optional<Cliente> obtenerPorRfc(String rfc);
    Optional<Cliente> obtenerPorNumeroCuenta(String numeroCuenta);
    
    // Actualización
    Cliente actualizarCliente(Long id, Cliente datosActualizados);
    
    // Baja Lógica
    void desactivarCliente(Long id);
}
