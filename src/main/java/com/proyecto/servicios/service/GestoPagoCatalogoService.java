package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoCatalogoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GestoPagoCatalogoService {
    private final GestoPagoCatalogoRepository catalogoRepository;
    private final CacheManager cacheManager;

    @Transactional("sfTransactionManager")
    public void guardarCatalogo(List<GestoPagoProducto> productos) {
        validarIdentificadores(productos);
        // Orden estable para que dos lotes concurrentes adquieran las filas en el mismo orden.
        List<GestoPagoProducto> ordenados = productos.stream()
                .sorted(Comparator.comparing(GestoPagoProducto::getIdProducto)).toList();
        catalogoRepository.guardarCatalogo(ordenados);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                var cache = cacheManager.getCache("productosCache");
                if (cache != null) {
                    cache.clear();
                }
            }
        });
    }

    private void validarIdentificadores(List<GestoPagoProducto> productos) {
        if (productos == null || productos.isEmpty()) {
            throw new GestoPagoException(502, "La API de GestoPago no retornó productos.");
        }
        Set<Integer> identificadores = new HashSet<>();
        for (GestoPagoProducto producto : productos) {
            if (producto == null || producto.getIdProducto() == null || producto.getIdProducto() <= 0
                    || !identificadores.add(producto.getIdProducto())) {
                throw new GestoPagoException(502, "El catálogo de GestoPago contiene identificadores inválidos o repetidos.");
            }
        }
    }
}
