package com.proyecto.servicios.model.gestopago;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Data;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class ProductoDto {

    @XmlAttribute(name = "servicio")
    private String servicio;

    @XmlAttribute(name = "producto")
    private String producto;

    @XmlAttribute(name = "idServicio")
    private Integer idServicio;

    @XmlAttribute(name = "idProducto")
    private Integer idProducto;

    @XmlAttribute(name = "idCatTipoServicio")
    private Integer idCatTipoServicio;

    @XmlAttribute(name = "tipoFront")
    private String tipoFront;

    @XmlAttribute(name = "hasDigitoVerificador")
    private Boolean hasDigitoVerificador;

    @XmlAttribute(name = "precio")
    private Double precio;

    @XmlAttribute(name = "showAyuda")
    private Boolean showAyuda;

    @XmlAttribute(name = "tipoReferencia")
    private String tipoReferencia;

    @XmlElement(name = "legend")
    private String legend;
}
