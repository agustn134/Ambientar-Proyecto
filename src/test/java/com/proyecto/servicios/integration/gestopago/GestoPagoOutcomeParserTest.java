package com.proyecto.servicios.integration.gestopago;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static com.proyecto.servicios.integration.gestopago.GestoPagoOutcomeParser.State.*;

class GestoPagoOutcomeParserTest {
    private final GestoPagoOutcomeParser parser=new GestoPagoOutcomeParser();
    private String xml(String code,String amount){return "<RESPONSE><ID_TX>123</ID_TX><NUM_AUTORIZACION>456</NUM_AUTORIZACION><MONTO>"+amount+"</MONTO><MENSAJE><CODIGO>"+code+"</CODIGO></MENSAJE></RESPONSE>";}
    @Test void exigeCodigoMontoYRespuestaCompleta() {
        assertEquals(CONFIRMED,parse(200,xml("01","4E+1")).state());
        assertEquals(UNKNOWN,parse(200,xml("01","41")).state());
        assertEquals(UNKNOWN,parse(200,xml("82","40")).state());
        assertEquals(UNKNOWN,parse(500,xml("01","40")).state());
        assertEquals(UNKNOWN,parse(200,"<RESPONSE>").state());
        assertEquals(UNKNOWN,parse(200,xml("01","40").replace("</RESPONSE>","<CODIGO>01</CODIGO></RESPONSE>")).state());
    }
    @Test void distingueExpiracionDePermisosSinReenviar() {
        assertTrue(parse(403,"{\"token\":\"EXPIRED\"}").tokenExpired());
        assertFalse(parse(403,"{\"message\":\"Forbidden\"}").tokenExpired());
    }
    @Test void codigo01NoConfirmaAutorizacionesNumericasNulasONegativas() {
        for(String authorization : new String[]{"0","000","-1","-2","-000"}) {
            String body=xml("01","40").replace("<NUM_AUTORIZACION>456</NUM_AUTORIZACION>",
                    "<NUM_AUTORIZACION>"+authorization+"</NUM_AUTORIZACION>");
            assertEquals(UNKNOWN,parse(200,body).state(),"Una autorización inválida no acredita una captura");
        }
        // El contrato de venta define String(1-60), no exige que toda autorización sea numérica.
        assertEquals(CONFIRMED,parse(200,xml("01","40").replace(
                "<NUM_AUTORIZACION>456</NUM_AUTORIZACION>","<NUM_AUTORIZACION>ABC-123</NUM_AUTORIZACION>")).state());
    }
    @Test void carrierNoDisponibleEnOtroFormatoNoAcreditaExitoNiRechazoDefinitivo() {
        String body="<AUTORIZACION><NUM_AUTORIZACION>-1</NUM_AUTORIZACION>"
                +"<CODIGO_MENSAJE>03</CODIGO_MENSAJE><MENSAJE>Carrier no responde</MENSAJE></AUTORIZACION>";
        for(int status : new int[]{200,422,503})assertEquals(UNKNOWN,parse(status,body).state());
        assertEquals(UNKNOWN,parse(200,xml("03","40")).state());
    }
    @Test void rechazaXxeYDocumentosGrandes() {
        assertEquals(UNKNOWN,parse(200,"<!DOCTYPE RESPONSE [<!ENTITY x SYSTEM 'file:///archivo-privado'>]><RESPONSE>&x;</RESPONSE>").state());
        assertEquals(UNKNOWN,parse(200,"x".repeat(65537)).state());
    }
    private GestoPagoOutcomeParser.Result parse(int status,String body){return parser.parse(new GestoPagoTransport.Reply(status,body),new BigDecimal("40"));}
}
