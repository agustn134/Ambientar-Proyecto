package com.proyecto.servicios.integration.gestopago;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.StringReader;
import java.math.BigDecimal;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;

@Component
public class GestoPagoOutcomeParser {
    public enum State { CONFIRMED, UNKNOWN }
    // No hay códigos de rechazo definitivo habilitados sin tabla contractual aprobada.
    public record Result(State state, String code, String transactionId, String authorization, boolean tokenExpired) {
        public static Result unknown() { return new Result(State.UNKNOWN,null,null,null,false); }
        @Override public String toString() { return "Result[state=" + state + "]"; }
    }
    public Result parse(GestoPagoTransport.Reply reply, BigDecimal expectedAmount) {
        try {
            if (reply.body().length() > 65536) return Result.unknown();
            if (reply.status()==403) {
                var json = new ObjectMapper().readTree(reply.body());
                return new Result(State.UNKNOWN,null,null,null,"EXPIRED".equals(json.path("token").asText()));
            }
            if (reply.status()!=200) return Result.unknown();
            var factory=DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder=factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
                @Override public void error(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
            });
            Document xml=builder.parse(new InputSource(new StringReader(reply.body())));
            if (!xml.getDocumentElement().getTagName().equals("RESPONSE")) return Result.unknown();
            String code=one(xml,"CODIGO");
            var codeNode=xml.getElementsByTagName("CODIGO").item(0);
            if(!codeNode.getParentNode().getNodeName().equals("MENSAJE")
                    || codeNode.getParentNode().getParentNode()!=xml.getDocumentElement()) return Result.unknown();
            if (!"01".equals(code)) return new Result(State.UNKNOWN,code,null,null,false);
            String id=one(xml,"ID_TX"), authorization=one(xml,"NUM_AUTORIZACION");
            for(String field : java.util.List.of("ID_TX","NUM_AUTORIZACION","MONTO"))
                if(xml.getElementsByTagName(field).item(0).getParentNode()!=xml.getDocumentElement()) return Result.unknown();
            if (!id.matches("[0-9]+") || new java.math.BigInteger(id).signum()<=0
                    || authorization.isBlank() || authorization.length()>60
                    || (authorization.matches("-?[0-9]+") && new java.math.BigInteger(authorization).signum()<=0)
                    || new BigDecimal(one(xml,"MONTO")).compareTo(expectedAmount)!=0) return Result.unknown();
            return new Result(State.CONFIRMED,code,id,authorization,false);
        } catch (Exception error) { return Result.unknown(); }
    }
    private static String one(Document xml, String field) {
        var nodes=xml.getElementsByTagName(field);
        if(nodes.getLength()!=1) throw new IllegalArgumentException();
        return nodes.item(0).getTextContent().trim();
    }
}
