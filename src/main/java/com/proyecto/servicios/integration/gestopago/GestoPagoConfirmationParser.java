package com.proyecto.servicios.integration.gestopago;

import org.springframework.stereotype.Component;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.w3c.dom.*;
import java.io.StringReader;

/** confirmTx NO usa el contrato XML de sendTx: 06 aplicado, 70 no aplicado. */
@Component
public class GestoPagoConfirmationParser {
    public enum Decision { APPLIED, NOT_APPLIED, UNKNOWN }
    public record Result(Decision decision,String code,String transactionId) {}
    public Result parse(GestoPagoTransport.Reply reply) {
        var unknown=new Result(Decision.UNKNOWN,null,null);
        try {
            if(reply.status()!=200 || reply.body().length()>65536) return unknown;
            var f=DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            f.setXIncludeAware(false);f.setExpandEntityReferences(false);
            var builder=f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){
                @Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
                @Override public void error(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
            });
            var xml=builder.parse(new InputSource(new StringReader(reply.body())));
            var root=xml.getDocumentElement();
            if(!"RESPONSE".equals(root.getTagName()))return unknown;
            var mensaje=single(xml,"MENSAJE",root);
            String code=single(xml,"CODIGO",mensaje).getTextContent().trim();
            String id=single(xml,"ID_TX",mensaje).getTextContent().trim();
            String auth=single(xml,"NUM_AUTORIZACION",root).getTextContent().trim();
            if("06".equals(code) && id.matches("[0-9]{1,60}") && new java.math.BigInteger(id).signum()>0
                    && auth.matches("[0-9]{1,60}") && new java.math.BigInteger(auth).signum()>0)
                return new Result(Decision.APPLIED,code,id);
            if("70".equals(code) && "-1".equals(id) && "0".equals(auth))
                return new Result(Decision.NOT_APPLIED,code,null);
            return new Result(Decision.UNKNOWN,code,null);
        } catch(Exception ex) { return unknown; }
    }
    private static Node single(Document doc,String name,Node parent) {
        var list=doc.getElementsByTagName(name);
        if(list.getLength()!=1 || list.item(0).getParentNode()!=parent)throw new IllegalArgumentException();
        return list.item(0);
    }
}
