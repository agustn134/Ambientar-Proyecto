package com.proyecto.servicios.integration.gestopago;

import org.springframework.stereotype.Component;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.math.BigDecimal;
import static com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.*;

@Component
public class GestoPagoReferenceParser {
    public boolean tokenExpired(String body) {
        try { return body.length()<=65536 && "EXPIRED".equals(new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("token").asText()); }
        catch(Exception error){return false;}
    }
    public Detalle parse(GestoPagoTransport.Reply response) {
        var unknown=new Detalle(Resultado.INDETERMINADA,null);
        try {
            if(response.status()!=200 || response.body().length()>65536)return unknown;
            var factory=DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            var builder=factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){
                @Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
                @Override public void error(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
            });
            var xml=builder.parse(new InputSource(new StringReader(response.body())));
            if(!"RESPONSE".equals(xml.getDocumentElement().getTagName()))return unknown;
            var messages=xml.getElementsByTagName("MENSAJE");var codes=xml.getElementsByTagName("CODIGO");
            if(messages.getLength()!=1 || messages.item(0).getParentNode()!=xml.getDocumentElement()
                    || codes.getLength()!=1 || codes.item(0).getParentNode()!=messages.item(0))return unknown;
            String code=codes.item(0).getTextContent().trim();
            if("02".equals(code))return new Detalle(Resultado.INVALIDA,null);
            if(!"01".equals(code))return unknown;
            var balances=xml.getElementsByTagName("SALDO");
            if(balances.getLength()>1)return unknown;
            BigDecimal balance=null;
            if(balances.getLength()==1){
                if(balances.item(0).getParentNode()!=messages.item(0))return unknown;
                balance=new BigDecimal(balances.item(0).getTextContent().trim());
                if(balance.precision()>19 || balance.scale()>2 || balance.scale() < -12)return unknown;
            }
            return new Detalle(Resultado.VALIDA,balance);
        }catch(Exception error){return unknown;}
    }
}
