package com.proyecto.servicios.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Sin productos configurados, ningún producto está autorizado para construir pagos. */
@Component
@ConfigurationProperties(prefix = "gestopago.contrato")
@Getter @Setter
public class GestoPagoContratoProperties {
    private Map<Integer, ReglaProducto> productos = new HashMap<>();

    public enum Destino { TELEFONO, REFERENCIA, FIJO, FIJO_ESPECIAL }
    @Getter @Setter
    public static class ReglaProducto {
        private boolean habilitado;
        // Habilitar localmente un producto no autoriza su envío financiero.
        private boolean integracionAprobada;
        private boolean servicioActivo;
        private boolean bloqueoServicioMonto24h;
        private Integer idServicio;
        private String tipoFront;
        private Integer idCatTipoServicio;
        private Destino destino;
        private BigDecimal montoMinimo;
        private BigDecimal montoMaximo;
        private BigDecimal montoFijo;
        private boolean requiereReferencia;
        private Integer referenciaMinima;
        private Integer referenciaMaxima;
    }
}
