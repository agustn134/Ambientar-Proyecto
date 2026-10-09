package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.RegistroClienteException;
import com.proyecto.servicios.model.cliente.RegistroClienteRequest.DomicilioRequest;
import com.proyecto.servicios.repositorys.cliente.CatalogoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ValidacionDatosCliente {
    private final CatalogoRepository catalogos;
    public CatalogoRepository.Asentamiento validar(LocalDate nacimiento,Integer sexo,Integer nacionalidad,Integer estadoCivil,DomicilioRequest domicilio) {
        if (nacimiento.plusYears(18).isAfter(LocalDate.now())) throw new RegistroClienteException("fechaNacimiento","El cliente debe tener al menos 18 años",400);
        opcion(CatalogoRepository.Tipo.SEXO,sexo,"sexoId");
        opcion(CatalogoRepository.Tipo.NACIONALIDAD,nacionalidad,"nacionalidadId");
        opcion(CatalogoRepository.Tipo.ESTADO_CIVIL,estadoCivil,"estadoCivilId");
        opcion(CatalogoRepository.Tipo.PAIS,domicilio.paisId(),"domicilio.paisId");
        var asentamiento=asentamiento(domicilio.asentamientoId());
        if (!asentamiento.codigoPostal().equals(domicilio.codigoPostal())) throw new RegistroClienteException("domicilio.asentamientoId","El asentamiento no corresponde al código postal",400);
        return asentamiento;
    }
    public CatalogoRepository.Asentamiento asentamiento(Integer id) {
        return catalogos.porId(id).orElseThrow(() -> new RegistroClienteException("domicilio.asentamientoId","Selecciona un asentamiento del catálogo postal",400));
    }
    private void opcion(CatalogoRepository.Tipo tipo,Integer id,String campo) {
        if (!catalogos.habilitado(tipo,id)) throw new RegistroClienteException(campo,"Selecciona una opción habilitada del catálogo",400);
    }
}
