package com.proyecto.servicios.service;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.ProductoDto;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import com.proyecto.servicios.service.Impl.GestoPagoProductServiceImpl;
import feign.FeignException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class GestoPagoProductServiceTest {

    @Mock
    private GestoPagoProductClient gestoPagoProductClient;

    @Mock
    private GestoPagoTokenService gestoPagoTokenService;

    @Mock
    private GestoPagoProductoRepository productoRepository;

    @InjectMocks
    private GestoPagoProductServiceImpl productService;

    private static final String TEST_TOKEN = "qmzAAEYFmqQbIT/Ktxme8FDV343iYQPwVJM4T39cEinWq1yjc1pp9oxSo4Rjmo5Fk27YzkCDaCr5RM4xtGZNCBZk1MH1tMNbykJ/WzfVjxSEXc9FERnZJPsVuFcAgfsfHmEYEfsLSmLZgovuGmnpDA==";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productService, "idDistribuidor", 83);
        ReflectionTestUtils.setField(productService, "codigoDispositivo", "GPS83-TPV-17");
        ReflectionTestUtils.setField(productService, "tokenConfigurado", TEST_TOKEN);
    }

    @Test
    @DisplayName("Debe consultar getProductList con Bearer token obtenido de la configuración")
    void debeConsultarCatalogoConBearerTokenConfigurado() {
        GestoPagoProductResponse mockResponse = new GestoPagoProductResponse();
        when(gestoPagoProductClient.getProductList(anyString())).thenReturn(mockResponse);

        GestoPagoProductResponse result = productService.consultarCatalogoGestoPago();

        assertNotNull(result);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        verify(gestoPagoProductClient, times(1)).getProductList(authCaptor.capture());

        String capturedAuth = authCaptor.getValue();
        assertTrue(capturedAuth.startsWith("Bearer "));
        assertEquals("Bearer " + TEST_TOKEN, capturedAuth);

        verify(gestoPagoTokenService, never()).obtenerTokenActivo(anyInt(), anyString());
    }

    @Test
    @DisplayName("Debe deserializar correctamente el XML de respuesta de getProductList")
    void debeDeserializarXmlRespuestaCorrectamente() throws Exception {
        String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<RESPONSE>\n" +
                "    <MENSAJE>\n" +
                "        <CODIGO>01</CODIGO>\n" +
                "        <TEXTO>Operacion realizada con exito</TEXTO>\n" +
                "    </MENSAJE>\n" +
                "    <PRODUCTOS>\n" +
                "        <producto servicio='AGUAKAN (Cancun)' producto='Agua Cancun' idServicio='56' idProducto='185' idCatTipoServicio='15' tipoFront='2' hasDigitoVerificador='false' precio='10.0' showAyuda='false' tipoReferencia='c'>\n" +
                "            <legend><![CDATA[Atencion a clientes AGUAKAN]]></legend>\n" +
                "        </producto>\n" +
                "    </PRODUCTOS>\n" +
                "</RESPONSE>";

        JAXBContext context = JAXBContext.newInstance(GestoPagoProductResponse.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        GestoPagoProductResponse response = (GestoPagoProductResponse) unmarshaller.unmarshal(new StringReader(xml));

        assertNotNull(response);
        assertNotNull(response.getMensaje());
        assertEquals("01", response.getMensaje().getCodigo());
        assertEquals("Operacion realizada con exito", response.getMensaje().getTexto());

        assertNotNull(response.getProductos());
        assertEquals(1, response.getProductos().size());

        ProductoDto prod = response.getProductos().get(0);
        assertEquals("AGUAKAN (Cancun)", prod.getServicio());
        assertEquals("Agua Cancun", prod.getProducto());
        assertEquals(56, prod.getIdServicio());
        assertEquals(185, prod.getIdProducto());
    }

    @Test
    @DisplayName("Debe sincronizar productos correctamente y guardarlos en BD")
    void debeSincronizarCatalogoProductosExito() {
        GestoPagoProductResponse mockResponse = new GestoPagoProductResponse();
        ProductoDto prod1 = new ProductoDto();
        prod1.setIdProducto(101);
        prod1.setProducto("Producto Test 1");
        mockResponse.setProductos(List.of(prod1));

        when(gestoPagoProductClient.getProductList(anyString())).thenReturn(mockResponse);
        when(productoRepository.findByIdProducto(101)).thenReturn(Optional.empty());

        GestoPagoProductResponse response = productService.sincronizarCatalogoProductos();

        assertNotNull(response);
        assertEquals(1, response.getProductos().size());
        
        // Verifica que se haya mandado a guardar
        verify(productoRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Sincronización debe retornar sin error si la respuesta es vacía o nula")
    void debeSincronizarCatalogoProductosRespuestaVacia() {
        GestoPagoProductResponse mockResponse = new GestoPagoProductResponse();
        // Productos nulos o lista vacia
        mockResponse.setProductos(new ArrayList<>());

        when(gestoPagoProductClient.getProductList(anyString())).thenReturn(mockResponse);

        GestoPagoProductResponse response = productService.sincronizarCatalogoProductos();

        assertNotNull(response);
        assertTrue(response.getProductos().isEmpty());
        // No se debe llamar a base de datos
        verify(productoRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Debe obtener token de BD si no hay token configurado")
    void debeObtenerTokenDesdeBDCuandoPropertiesEsVacio() {
        // Configuramos el token vacío
        ReflectionTestUtils.setField(productService, "tokenConfigurado", "");

        GestoPagoToken mockTokenEntity = new GestoPagoToken();
        mockTokenEntity.setToken("token-from-db-123");

        // Simulamos que el token existe en BD
        when(gestoPagoTokenService.obtenerTokenActivo(anyInt(), anyString()))
                .thenReturn(Optional.of(mockTokenEntity));
        when(gestoPagoProductClient.getProductList("Bearer token-from-db-123"))
                .thenReturn(new GestoPagoProductResponse());

        productService.consultarCatalogoGestoPago();

        verify(gestoPagoTokenService, times(1)).obtenerTokenActivo(anyInt(), anyString());
        verify(gestoPagoProductClient, times(1)).getProductList("Bearer token-from-db-123");
    }

    @Test
    @DisplayName("Debe arrojar excepción si Feign falla al comunicarse con GestoPago")
    void debePropagarExcepcionDeFeignAlConsultarCatalogo() {
        // Simulamos un error 500 Interno
        when(gestoPagoProductClient.getProductList(anyString()))
                .thenThrow(new GestoPagoException(500, "Error interno en el servidor de GestoPago (500)"));

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            productService.consultarCatalogoGestoPago();
        });

        assertEquals(500, exception.getStatus());
        assertTrue(exception.getMessage().contains("Error interno"));
    }

    @Test
    @DisplayName("Debe devolver productos locales almacenados")
    void debeDevolverProductosLocales() {
        GestoPagoProducto localProd = new GestoPagoProducto();
        localProd.setProducto("Producto Local");
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(localProd));

        List<GestoPagoProducto> productos = productService.obtenerProductosGuardados();

        assertFalse(productos.isEmpty());
        assertEquals("Producto Local", productos.get(0).getProducto());
        verify(productoRepository, times(1)).findByActivoTrue();
    }
}
