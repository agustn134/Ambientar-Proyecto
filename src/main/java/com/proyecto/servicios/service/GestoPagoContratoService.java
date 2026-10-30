package com.proyecto.servicios.service;

import com.proyecto.servicios.config.GestoPagoContratoProperties;
import com.proyecto.servicios.config.GestoPagoContratoProperties.Destino;
import com.proyecto.servicios.exception.ContratoPagoException;
import com.proyecto.servicios.model.gestopago.pago.*;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GestoPagoContratoService {
    private final GestoPagoProductoRepository productos;
    private final GestoPagoContratoProperties configuracion;
    private final Validator validator;

    public PagoContratoValidado validar(CrearPagoRequest solicitud) {
        exigir(solicitud != null && validator.validate(solicitud).isEmpty(), "DATOS_INVALIDOS");
        var producto = productos.findByIdProducto(solicitud.idProducto())
                .orElseThrow(() -> new ContratoPagoException("PRODUCTO_NO_DISPONIBLE"));
        exigir(Boolean.TRUE.equals(producto.getActivo()), "PRODUCTO_NO_DISPONIBLE");
        exigir(Objects.equals(producto.getIdServicio(), solicitud.idServicio()), "SERVICIO_INCONSISTENTE");
        var regla = configuracion.getProductos().get(solicitud.idProducto());
        exigir(regla != null && regla.isHabilitado(), "PRODUCTO_NO_HABILITADO");
        exigir(Objects.equals(regla.getIdServicio(), producto.getIdServicio())
                && regla.getTipoFront() != null && regla.getIdCatTipoServicio() != null
                && Objects.equals(regla.getTipoFront(), producto.getTipoFront())
                && Objects.equals(regla.getIdCatTipoServicio(), producto.getIdCatTipoServicio()), "CATALOGO_CAMBIO");
        exigir(regla.getMontoMinimo() != null && regla.getMontoMaximo() != null
                && regla.getMontoMinimo().signum() > 0
                && regla.getMontoMinimo().compareTo(regla.getMontoMaximo()) <= 0,
                "REGLA_INCOMPLETA");
        if (regla.getMontoFijo() != null) {
            exigir(producto.getPrecio() != null && producto.getPrecio().compareTo(regla.getMontoFijo()) == 0,
                    "CATALOGO_CAMBIO");
        }
        exigir(solicitud.montoPago().compareTo(regla.getMontoMinimo()) >= 0
                && solicitud.montoPago().compareTo(regla.getMontoMaximo()) <= 0
                && (regla.getMontoFijo() == null || solicitud.montoPago().compareTo(regla.getMontoFijo()) == 0),
                "MONTO_NO_PERMITIDO");
        if (regla.isRequiereReferencia()) {
            exigir(regla.getReferenciaMinima() != null && regla.getReferenciaMaxima() != null
                    && regla.getReferenciaMinima() > 0 && regla.getReferenciaMaxima() <= 100
                    && regla.getReferenciaMinima() <= regla.getReferenciaMaxima(), "REGLA_INCOMPLETA");
            String referencia = solicitud.referencia();
            exigir(referencia != null && !referencia.isBlank()
                    && referencia.length() >= regla.getReferenciaMinima()
                    && referencia.length() <= regla.getReferenciaMaxima()
                    && referencia.matches("[A-Za-z0-9]+"), "REFERENCIA_INVALIDA");
        } else {
            exigir(solicitud.referencia() == null, "REFERENCIA_NO_PERMITIDA");
        }
        boolean especial = producto.getIdCatTipoServicio() == 10 || producto.getIdCatTipoServicio() == 11;
        var destino = regla.getDestino();
        exigir(destino != null, "REGLA_INCOMPLETA");
        exigir("1".equals(producto.getTipoFront()) || "2".equals(producto.getTipoFront())
                || ("4".equals(producto.getTipoFront()) && regla.isIntegracionAprobada()
                    && regla.isRequiereReferencia() && destino==Destino.REFERENCIA), "DESTINO_NO_SOPORTADO");
        // La excepción solo se activa por una política explícita aprobada para este producto.
        exigir(especial ? destino == Destino.FIJO_ESPECIAL
                : ("1".equals(producto.getTipoFront()) && (destino == Destino.TELEFONO || destino == Destino.REFERENCIA))
                || ("2".equals(producto.getTipoFront()) && destino == Destino.FIJO)
                || ("4".equals(producto.getTipoFront()) && regla.isIntegracionAprobada()
                    && regla.isRequiereReferencia() && destino==Destino.REFERENCIA), "DESTINO_NO_SOPORTADO");
        String telefono;
        if (destino == Destino.TELEFONO) {
            exigir(solicitud.telefono() != null && solicitud.telefono().matches("[0-9]{10}"), "TELEFONO_INVALIDO");
            telefono = solicitud.telefono();
        } else {
            exigir(solicitud.telefono() == null, "TELEFONO_NO_PERMITIDO");
            if (destino == Destino.REFERENCIA) {
                exigir(regla.isRequiereReferencia(), "REGLA_INCOMPLETA");
                exigir(solicitud.referencia().length() <= 20, "DESTINO_NO_SOPORTADO");
                telefono = solicitud.referencia();
            } else {
                telefono = destino == Destino.FIJO_ESPECIAL ? "#1111111111" : "1111111111";
            }
        }
        return new PagoContratoValidado(solicitud.idServicio(), solicitud.idProducto(), solicitud.montoPago(),
                telefono, solicitud.referencia(), regla.isRequiereReferencia());
    }

    private static void exigir(boolean condicion, String codigo) {
        if (!condicion) throw new ContratoPagoException(codigo);
    }
}
