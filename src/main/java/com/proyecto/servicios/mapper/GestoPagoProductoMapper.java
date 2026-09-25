package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.model.gestopago.ProductoDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct para la conversion entre ProductoDto y la entidad GestoPagoProducto.
 * Al usar componentModel = "spring", Spring lo registra como bean en el contexto al arrancar,
 * por lo que no es necesario instanciarlo manualmente.
 */
@Mapper(componentModel = "spring")
public interface GestoPagoProductoMapper {

    /**
     * Convierte un ProductoDto (respuesta XML de la API) a la entidad GestoPagoProducto.
     * Los campos gestionados por JPA/auditoria se ignoran para que los @PrePersist los manejen.
     * MapStruct convierte Double -> BigDecimal automaticamente via BigDecimal.valueOf().
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    GestoPagoProducto toEntity(ProductoDto dto);

    /**
     * Convierte una lista de ProductoDto a una lista de entidades GestoPagoProducto.
     */
    List<GestoPagoProducto> toEntityList(List<ProductoDto> dtos);

    /**
     * Convierte una entidad GestoPagoProducto a ProductoDto.
     */
    ProductoDto toDto(GestoPagoProducto entity);

    /**
     * Convierte una lista de entidades GestoPagoProducto a una lista de ProductoDto.
     */
    List<ProductoDto> toDtoList(List<GestoPagoProducto> entities);
}
