package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.mapper.GestoPagoProductoMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.GestoPagoCatalogoService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GestoPagoProductServiceImpl implements GestoPagoProductService {

    private final GestoPagoProductClient gestoPagoProductClient;
    private final GestoPagoTokenService gestoPagoTokenService;
    private final GestoPagoProductoRepository productoRepository;
    private final GestoPagoProductoMapper productoMapper;
    private final GestoPagoCatalogoService catalogoService;
    private final com.proyecto.servicios.repositorys.gestopago.GestoPagoCatalogoQuota cuotaCatalogo;

    @Value("${gestopago.service.id-distribuidor:${gestopago.auth.id-distribuidor}}")
    private Integer idDistribuidor;

    @Value("${gestopago.service.codigo-dispositivo:${gestopago.auth.codigo-dispositivo}}")
    private String codigoDispositivo;




    @Override
    public GestoPagoProductResponse consultarCatalogoGestoPago() {
        // 1. Obtener Bearer Token desde la configuración de la aplicación (o fallback activo en BD)
        String authHeader = obtenerBearerToken();

        // 2. Invocar al endpoint GET /sistema/service/getProductList.do
        log.info("Consultando endpoint GET /sistema/service/getProductList.do con Bearer Token...");
        return consultarProveedor(authHeader);
    }

    @Override
    public GestoPagoProductResponse sincronizarCatalogoProductos() {
        log.info("Iniciando sincronizacion forzada del catalogo GestoPago para distribuidor={}", idDistribuidor);

        GestoPagoProductResponse response = consultarCatalogoGestoPago();

        if (response == null || response.getProductos() == null || response.getProductos().isEmpty()) {
            log.warn("La respuesta de GestoPago no contiene productos.");
            throw new GestoPagoException(502, "La API de GestoPago no retornó productos.");
        }

        log.info("Se recibieron {} productos desde GestoPago. Mapeando y guardando en base de datos...",
                response.getProductos().size());

        // MapStruct: List<ProductoDto> -> List<GestoPagoProducto> en tiempo de ejecucion
        List<GestoPagoProducto> entidadesParaGuardar = productoMapper.toEntityList(response.getProductos());

        catalogoService.guardarCatalogo(entidadesParaGuardar);
        log.info("Sincronizacion finalizada. Se persistieron {} productos en gestopago_productos.",
                entidadesParaGuardar.size());

        return response;
    }

    @Override
    @Cacheable(value = "productosCache")
    public List<GestoPagoProducto> obtenerProductosGuardados() {
        return productoRepository.findByActivoTrue();
    }

    /**
     * Flujo principal con jerarquia de cache: Redis -> PostgreSQL -> API GestoPago.
     *
     * 1. @Cacheable intercepta primero: si la lista ya esta en Redis, la devuelve de inmediato.
     * 2. Cache miss: verifica PostgreSQL. Si hay datos, los retorna y Spring los guarda en Redis.
     * 3. PostgreSQL vacio: llama a la API, JAXB parsea el XML (via Feign), MapStruct mapea
     *    List<ProductoDto> -> List<GestoPagoProducto>, guarda en PostgreSQL.
     *    Spring guarda el resultado en Redis automaticamente al retornar (@Cacheable).
     * Si la API responde != 200, GestoPagoErrorDecoder convierte el error en GestoPagoException
     * que GlobalExceptionHandler responde en el body del ResponseEntity.
     */
    @Override
    @Cacheable(value = "productosCache", unless = "#result == null || #result.isEmpty()")
    public List<GestoPagoProducto> obtenerOSincronizarProductos() {
        log.info("Cache miss en Redis. Verificando PostgreSQL...");

        List<GestoPagoProducto> productosEnBD = productoRepository.findByActivoTrue();
        if (!productosEnBD.isEmpty()) {
            log.info("{} producto(s) encontrados en PostgreSQL. @Cacheable guardara en Redis al retornar.",
                    productosEnBD.size());
            return productosEnBD;
        }

        log.info("PostgreSQL vacio. Llamando a la API de GestoPago para generar el catalogo...");
        String authHeader = obtenerBearerToken();

        // Feign llama a la API. GestoPagoErrorDecoder maneja status != 200 automaticamente.
        // JAXB parsea el XML de respuesta (@XmlRootElement en GestoPagoProductResponse).
        GestoPagoProductResponse response = consultarProveedor(authHeader);

        if (response == null || response.getProductos() == null || response.getProductos().isEmpty()) {
            log.warn("La API de GestoPago no retornó productos.");
            throw new GestoPagoException(502, "La API de GestoPago no retornó productos.");
        }

        // MapStruct: List<ProductoDto> -> List<GestoPagoProducto> en tiempo de ejecucion (sin new manual)
        List<GestoPagoProducto> entidades = productoMapper.toEntityList(response.getProductos());

        // El servicio compartido confirma PostgreSQL e invalida la caché antes de volver a consultar.
        // @Cacheable guardará las entidades persistidas, con sus IDs y fechas, al retornar.
        catalogoService.guardarCatalogo(entidades);
        log.info("Se persistieron {} productos en PostgreSQL. @Cacheable los guardara en Redis al retornar.",
                entidades.size());

        return productoRepository.findByActivoTrue();
    }

    /**
     * Obtiene el Bearer desde el administrador central, que valida vigencia y caché cifrada.
     */
    private String obtenerBearerToken() {
        GestoPagoToken tokenEntity = obtenerOAsegurarToken();
        return "Bearer " + tokenEntity.getToken();
    }

    /**
     * Asegura la obtención de un token válido. Si es la primera vez o la BD está vacía,
     * ejecuta renovarToken() antes de consultar.
     */
    private GestoPagoToken obtenerOAsegurarToken() {
        return gestoPagoTokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .orElseGet(() -> {
                    log.info("No se encontró token activo en BD. Solicitando uno nuevo por primera vez...");
                    gestoPagoTokenService.renovarToken();
                    return gestoPagoTokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                            .orElseThrow(() -> new IllegalStateException(
                                     "No se pudo generar ni obtener un token activo de GestoPago para distribuidor " + idDistribuidor));
                });
    }
    private GestoPagoProductResponse consultarProveedor(String authHeader) {
        cuotaCatalogo.reservarIntento(); // Confirmado en transacción propia; un timeout también consume intento.
        try { return gestoPagoProductClient.getProductList(authHeader); }
        catch(com.proyecto.servicios.exception.GestoPagoExpiredTokenException error) {
            gestoPagoTokenService.invalidarToken(idDistribuidor,codigoDispositivo,authHeader.substring(7));
            throw error; // Renovación en la próxima petición; sin reenvío automático.
        }
    }
}
