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
    @Test void rechazaXxeYDocumentosGrandes() {
        assertEquals(UNKNOWN,parse(200,"<!DOCTYPE RESPONSE [<!ENTITY x SYSTEM 'file:///archivo-privado'>]><RESPONSE>&x;</RESPONSE>").state());
        assertEquals(UNKNOWN,parse(200,"x".repeat(65537)).state());
    }
    private GestoPagoOutcomeParser.Result parse(int status,String body){return parser.parse(new GestoPagoTransport.Reply(status,body),new BigDecimal("40"));}
}
