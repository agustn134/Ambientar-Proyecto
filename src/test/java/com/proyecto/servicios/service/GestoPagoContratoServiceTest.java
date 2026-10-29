package com.proyecto.servicios.service;

import com.proyecto.servicios.config.GestoPagoContratoProperties;
import com.proyecto.servicios.config.GestoPagoContratoProperties.*;
import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.exception.ContratoPagoException;
import com.proyecto.servicios.model.gestopago.pago.CrearPagoRequest;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestoPagoContratoServiceTest {
    private final GestoPagoProductoRepository repository = mock(GestoPagoProductoRepository.class);
    private final GestoPagoContratoProperties properties = new GestoPagoContratoProperties();
    private final jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final GestoPagoContratoService service = new GestoPagoContratoService(repository, properties, factory.getValidator());
    private GestoPagoProducto producto;
    private ReglaProducto regla;

    @BeforeEach void preparar() {
        producto = GestoPagoProducto.builder().idProducto(100).idServicio(20).tipoFront("1")
                .idCatTipoServicio(1).activo(true).build();
        when(repository.findByIdProducto(100)).thenReturn(Optional.of(producto));
        regla = new ReglaProducto();
        regla.setHabilitado(true); regla.setIdServicio(20); regla.setTipoFront("1");
        regla.setIdCatTipoServicio(1); regla.setDestino(Destino.TELEFONO);
        regla.setMontoMinimo(new BigDecimal("10")); regla.setMontoMaximo(new BigDecimal("100"));
        properties.getProductos().put(100, regla);
    }
    @AfterEach void cerrar() { factory.close(); }
    private CrearPagoRequest request(String monto, String telefono, String referencia) {
        return new CrearPagoRequest(20, 100, new BigDecimal(monto), telefono, referencia);
    }
    private void rechaza(String codigo, CrearPagoRequest request) {
        assertEquals(codigo, assertThrows(ContratoPagoException.class, () -> service.validar(request)).getCodigo());
    }
    @Test void validaTelefonoYMontoSinAlterarlos() {
        var resultado = service.validar(request("10.00", "5512345678", null));
        assertEquals("5512345678", resultado.telefono());
        assertFalse(resultado.requiereVerificacionReferencia());
        assertFalse(resultado.toString().contains("5512345678"));
    }
    @Test void bloqueaProductosSinPoliticaYCambiosDeCatalogo() {
        properties.getProductos().clear();
        rechaza("PRODUCTO_NO_HABILITADO", request("10", "5512345678", null));
        properties.getProductos().put(100, regla);
        producto.setTipoFront("40");
        rechaza("CATALOGO_CAMBIO", request("10", "5512345678", null));
    }
    @Test void rechazaMontoManipuladoEscalaYTelefono() {
        rechaza("DATOS_INVALIDOS", request("10.001", "5512345678", null));
        rechaza("MONTO_NO_PERMITIDO", request("101", "5512345678", null));
        regla.setMontoFijo(new BigDecimal("50"));
        producto.setPrecio(new BigDecimal("50"));
        rechaza("MONTO_NO_PERMITIDO", request("49", "5512345678", null));
        rechaza("TELEFONO_INVALIDO", request("50", "123", null));
    }
    @Test void referenciaNoSeConsideraValidadaPorElProveedor() {
        regla.setDestino(Destino.REFERENCIA); regla.setRequiereReferencia(true);
        regla.setReferenciaMinima(3); regla.setReferenciaMaxima(20);
        var resultado = service.validar(request("10", null, "ABC123"));
        assertEquals("ABC123", resultado.telefono());
        assertTrue(resultado.requiereVerificacionReferencia());
        rechaza("REFERENCIA_INVALIDA", request("10", null, "AB"));
        rechaza("REFERENCIA_INVALIDA", request("10", null, "ABC\n123"));
    }
    @Test void fijoYEspecialRequierenPoliticaExplicita() {
        producto.setTipoFront("2"); regla.setTipoFront("2"); regla.setDestino(Destino.FIJO);
        assertEquals("1111111111", service.validar(request("10", null, null)).telefono());
        producto.setIdCatTipoServicio(10); regla.setIdCatTipoServicio(10);
        rechaza("DESTINO_NO_SOPORTADO", request("10", null, null));
        regla.setDestino(Destino.FIJO_ESPECIAL);
        assertEquals("#1111111111", service.validar(request("10", null, null)).telefono());
        rechaza("TELEFONO_NO_PERMITIDO", request("10", "5512345678", null));
    }
    @Test void rechazaInactivoServicioDistintoYParametrosIncompletos() {
        rechaza("DATOS_INVALIDOS", null);
        producto.setActivo(false);
        rechaza("PRODUCTO_NO_DISPONIBLE", request("10", "5512345678", null));
        producto.setActivo(true); producto.setIdServicio(21);
        rechaza("SERVICIO_INCONSISTENTE", request("10", "5512345678", null));
    }
}
