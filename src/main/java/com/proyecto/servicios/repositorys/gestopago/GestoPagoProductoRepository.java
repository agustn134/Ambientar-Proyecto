package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GestoPagoProductoRepository extends JpaRepository<GestoPagoProducto, Integer> {

    Optional<GestoPagoProducto> findByIdProducto(Integer idProducto);

    List<GestoPagoProducto> findByIdServicio(Integer idServicio);

    List<GestoPagoProducto> findByActivoTrue();

    boolean existsByIdProducto(Integer idProducto);
}
