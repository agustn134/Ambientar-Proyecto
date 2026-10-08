package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.service.RegistroClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {
    private final RegistroClienteService registro;

    @PostMapping(consumes="application/json", produces="application/json")
    public ResponseEntity<RegistroClienteResponse> registrar(@Valid @RequestBody RegistroClienteRequest request) {
        // GET /clientes/{id} se incorporará en la siguiente etapa.
        return ResponseEntity.status(201).body(registro.registrar(request));
    }
}
