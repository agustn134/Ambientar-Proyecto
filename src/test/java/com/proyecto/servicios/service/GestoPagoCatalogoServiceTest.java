package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoCatalogoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestoPagoCatalogoServiceTest {
    private final GestoPagoCatalogoRepository repository = mock(GestoPagoCatalogoRepository.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final GestoPagoCatalogoService service = new GestoPagoCatalogoService(repository, cacheManager);

    @BeforeEach void iniciar() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void terminar() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void invalidaCacheSoloDespuesDelCommitYOrdenaElLote() {
        var primero = producto(1);
        var segundo = producto(2);
        when(cacheManager.getCache("productosCache")).thenReturn(cache);
        service.guardarCatalogo(List.of(segundo, primero));
        verify(repository).guardarCatalogo(List.of(primero, segundo));
        verifyNoInteractions(cacheManager, cache);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        verify(cache).clear();
    }

    @Test void rechazaCatalogosInvalidosAntesDeGuardar() {
        for (var lote : List.of(List.of(producto(1), producto(1)), List.of(producto(0)),
                List.of(producto(-1)), List.of(new GestoPagoProducto()),
                Arrays.asList(producto(1), null), List.<GestoPagoProducto>of())) {
            assertEquals(502, assertThrows(GestoPagoException.class, () -> service.guardarCatalogo(lote)).getStatus());
        }
        assertThrows(GestoPagoException.class, () -> service.guardarCatalogo(null));
        verifyNoInteractions(repository, cacheManager);
    }

    @Test void falloDePersistenciaNoInvalidaCache() {
        doThrow(new IllegalStateException("fallo sintético")).when(repository).guardarCatalogo(anyList());
        assertThrows(IllegalStateException.class, () -> service.guardarCatalogo(List.of(producto(1))));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verifyNoInteractions(cacheManager);
    }

    static GestoPagoProducto producto(int id) {
        return GestoPagoProducto.builder().idProducto(id).producto("Producto de prueba").build();
    }
}
