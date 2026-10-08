package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Cuenta;
import com.proyecto.servicios.repositorys.cliente.ClienteRepository;
import com.proyecto.servicios.repositorys.cliente.CuentaRepository;
import com.proyecto.servicios.service.ClienteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ClienteServiceImpl implements ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    private static final String NOMBRES_REGEX = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]{2,50}$";
    private static final String TELEFONO_REGEX = "^[0-9]{10}$";

    @Override
    @Transactional
    public Cliente registrarCliente(Cliente cliente) {
        validarCliente(cliente);
        
        // 1. Validaciones previas de unicidad
        if (clienteRepository.existsByCurp(cliente.getCurp())) {
            throw new RuntimeException("El cliente con CURP " + cliente.getCurp() + " ya existe.");
        }
        if (clienteRepository.existsByRfc(cliente.getRfc())) {
            throw new RuntimeException("El cliente con RFC " + cliente.getRfc() + " ya existe.");
        }
        if (clienteRepository.existsByCorreoElectronico(cliente.getCorreoElectronico())) {
            throw new RuntimeException("El cliente con Correo " + cliente.getCorreoElectronico() + " ya existe.");
        }
        
        cliente.setEstatus("ACTIVO");

        // El domicilio es dueño de la FK cliente_id.
        if (cliente.getDomicilio() != null) cliente.getDomicilio().setCliente(cliente);
        // 2. Guardar el cliente
        Cliente clienteGuardado = clienteRepository.save(cliente);
        log.info("Cliente registrado con ID: {}", clienteGuardado.getId());
        
        // 3. Crear cuenta automáticamente
        String numeroCuenta = generarNumeroCuentaUnico();
        
        Cuenta nuevaCuenta = Cuenta.builder()
                .numeroCuenta(numeroCuenta)
                .saldo(BigDecimal.ZERO)
                .estatus("ACTIVA")
                .fechaCreacion(LocalDateTime.now())
                .cliente(clienteGuardado)
                .build();
                
        cuentaRepository.save(nuevaCuenta);
        log.info("Cuenta generada exitosamente para el cliente ID: {}. Numero de Cuenta: {}", clienteGuardado.getId(), numeroCuenta);
        
        return clienteGuardado;
    }

    @Override
    public List<Cliente> obtenerTodos() {
        return clienteRepository.findAll();
    }

    @Override
    public Optional<Cliente> obtenerPorId(Long id) {
        return clienteRepository.findById(id);
    }

    @Override
    public Optional<Cliente> obtenerPorCurp(String curp) {
        return clienteRepository.findByCurp(curp);
    }

    @Override
    public Optional<Cliente> obtenerPorRfc(String rfc) {
        return clienteRepository.findByRfc(rfc);
    }

    @Override
    public Optional<Cliente> obtenerPorNumeroCuenta(String numeroCuenta) {
        Optional<Cuenta> cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta);
        return cuenta.map(Cuenta::getCliente);
    }

    @Override
    @Transactional
    public Cliente actualizarCliente(Long id, Cliente datosActualizados) {
        Cliente clienteExistente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + id));

        // Actualizar datos personales (excepto los bloqueados)
        if (datosActualizados.getNombre() != null) clienteExistente.setNombre(datosActualizados.getNombre());
        if (datosActualizados.getSegundoNombre() != null) clienteExistente.setSegundoNombre(datosActualizados.getSegundoNombre());
        if (datosActualizados.getApellidoPaterno() != null) clienteExistente.setApellidoPaterno(datosActualizados.getApellidoPaterno());
        if (datosActualizados.getApellidoMaterno() != null) clienteExistente.setApellidoMaterno(datosActualizados.getApellidoMaterno());
        if (datosActualizados.getFechaNacimiento() != null) clienteExistente.setFechaNacimiento(datosActualizados.getFechaNacimiento());
        if (datosActualizados.getSexoId() != null) clienteExistente.setSexoId(datosActualizados.getSexoId());
        if (datosActualizados.getNacionalidadId() != null) clienteExistente.setNacionalidadId(datosActualizados.getNacionalidadId());
        if (datosActualizados.getEstadoCivilId() != null) clienteExistente.setEstadoCivilId(datosActualizados.getEstadoCivilId());

        // Actualizar datos de contacto y validación
        if (datosActualizados.getCorreoElectronico() != null && !datosActualizados.getCorreoElectronico().equals(clienteExistente.getCorreoElectronico())) {
            if (clienteRepository.existsByCorreoElectronico(datosActualizados.getCorreoElectronico())) {
                throw new RuntimeException("El correo ya se encuentra registrado");
            }
            clienteExistente.setCorreoElectronico(datosActualizados.getCorreoElectronico());
        }
        if (datosActualizados.getTelefonoMovil() != null) clienteExistente.setTelefonoMovil(datosActualizados.getTelefonoMovil());
        if (datosActualizados.getTelefonoAlternativo() != null) clienteExistente.setTelefonoAlternativo(datosActualizados.getTelefonoAlternativo());

        // Actualizar domicilio e información laboral
        if (datosActualizados.getDomicilio() != null) {
            actualizarDomicilio(clienteExistente, datosActualizados);
        }
        if (datosActualizados.getInformacionLaboral() != null) clienteExistente.setInformacionLaboral(datosActualizados.getInformacionLaboral());

        // Revalidar para asegurar la consistencia del objeto actualizado
        validarCliente(clienteExistente);

        return clienteRepository.save(clienteExistente);
    }

    @Override
    @Transactional
    public void desactivarCliente(Long id) {
        Cliente clienteExistente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + id));
        clienteExistente.setEstatus("INACTIVO");
        clienteRepository.save(clienteExistente);

        // Desactivar cuenta si es necesario (Solo clientes activos pueden tener cuentas activas)
        Optional<Cuenta> cuentaOpt = cuentaRepository.findByClienteId(id);
        if (cuentaOpt.isPresent()) {
            Cuenta cuenta = cuentaOpt.get();
            cuenta.setEstatus("INACTIVA");
            cuentaRepository.save(cuenta);
        }
    }

    private void actualizarDomicilio(Cliente cliente, Cliente datos) {
        var nuevo = datos.getDomicilio();
        if (cliente.getDomicilio() != null) nuevo.setId(cliente.getDomicilio().getId());
        nuevo.setCliente(cliente);
        cliente.setDomicilio(nuevo);
    }

    private void validarCliente(Cliente cliente) {
        // Validar Edad
        if (cliente.getFechaNacimiento() == null || cliente.getFechaNacimiento().plusYears(18).isAfter(LocalDate.now())) {
            throw new RuntimeException("El cliente debe ser mayor de edad (18 años o más)");
        }

        // Validar Nombres
        if (cliente.getNombre() == null || !Pattern.matches(NOMBRES_REGEX, cliente.getNombre())) {
            throw new RuntimeException("El nombre es obligatorio, solo debe contener letras y medir entre 2 y 50 caracteres.");
        }
        if (cliente.getApellidoPaterno() == null || !Pattern.matches(NOMBRES_REGEX, cliente.getApellidoPaterno())) {
            throw new RuntimeException("El apellido paterno es obligatorio, solo debe contener letras y medir entre 2 y 50 caracteres.");
        }
        if (cliente.getApellidoMaterno() == null || !Pattern.matches(NOMBRES_REGEX, cliente.getApellidoMaterno())) {
            throw new RuntimeException("El apellido materno es obligatorio, solo debe contener letras y medir entre 2 y 50 caracteres.");
        }

        // Validar Teléfono
        if (cliente.getTelefonoMovil() == null || !Pattern.matches(TELEFONO_REGEX, cliente.getTelefonoMovil())) {
            throw new RuntimeException("El teléfono móvil debe contener exactamente 10 dígitos.");
        }
    }

    private String generarNumeroCuentaUnico() {
        String numeroCuenta;
        do {
            numeroCuenta = String.format("%010d", (long) (Math.random() * 10000000000L));
        } while (cuentaRepository.existsByNumeroCuenta(numeroCuenta));
        
        return numeroCuenta;
    }
}
