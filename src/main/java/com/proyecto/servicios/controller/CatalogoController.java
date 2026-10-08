package com.proyecto.servicios.controller;

import com.proyecto.servicios.repositorys.cliente.CatalogoRepository;
import com.proyecto.servicios.repositorys.cliente.CatalogoRepository.Opcion;
import com.proyecto.servicios.repositorys.cliente.CatalogoRepository.Asentamiento;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.proyecto.servicios.repositorys.cliente.CatalogoRepository.Tipo.*;

@RestController
@RequestMapping("/catalogos")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name="Catálogos", description="Opciones permitidas para el registro en México")
public class CatalogoController {
    private final CatalogoRepository catalogos;
    @GetMapping("/sexos") public List<Opcion> sexos() { return catalogos.opciones(SEXO); }
    @GetMapping("/nacionalidades") public List<Opcion> nacionalidades() { return catalogos.opciones(NACIONALIDAD); }
    @GetMapping("/paises") public List<Opcion> paises() { return catalogos.opciones(PAIS); }
    @GetMapping("/estados-civiles") public List<Opcion> estadosCiviles() { return catalogos.opciones(ESTADO_CIVIL); }
    @GetMapping("/codigos-postales/{codigoPostal}")
    public List<Asentamiento> postal(@PathVariable @jakarta.validation.constraints.Pattern(regexp="[0-9]{5}",message="El código postal debe contener cinco dígitos") String codigoPostal) { return catalogos.porCodigoPostal(codigoPostal); }
}
