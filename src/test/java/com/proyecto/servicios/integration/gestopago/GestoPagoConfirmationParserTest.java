package com.proyecto.servicios.integration.gestopago;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.proyecto.servicios.integration.gestopago.GestoPagoConfirmationParser.Decision.*;

class GestoPagoConfirmationParserTest {
    final GestoPagoConfirmationParser parser=new GestoPagoConfirmationParser();
    String xml(String code,String id,String auth){return "<RESPONSE><NUM_AUTORIZACION>"+auth+"</NUM_AUTORIZACION><MENSAJE><CODIGO>"+code+"</CODIGO><ID_TX>"+id+"</ID_TX></MENSAJE></RESPONSE>";}
    @Test void aplicadoUsa06ConIdAnidado(){assertEquals(APPLIED,parser.parse(new GestoPagoTransport.Reply(200,xml("06","291437188","100024988"))).decision());}
    @Test void solo70ConIdMenosUnoYAutorizacionCeroLibera(){
        assertEquals(NOT_APPLIED,parser.parse(new GestoPagoTransport.Reply(200,xml("70","-1","0"))).decision());
        assertEquals(UNKNOWN,parser.parse(new GestoPagoTransport.Reply(200,xml("70","123","0"))).decision());
    }
    @Test void codigo01NoSeConfundeConContratoSend(){assertEquals(UNKNOWN,parser.parse(new GestoPagoTransport.Reply(200,xml("01","123","100"))).decision());}
    @Test void rechazaEstructuraAmbiguaErroresHttpYEntidadesExternas(){
        for(String body:new String[]{"<RESPONSE/>",xml("06","1","0"),xml("06","1","10").replace("</MENSAJE>","<CODIGO>70</CODIGO></MENSAJE>"),
                "<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///etc/passwd'>]><RESPONSE>&ext;</RESPONSE>","x".repeat(65537)})
            assertEquals(UNKNOWN,parser.parse(new GestoPagoTransport.Reply(200,body)).decision());
        assertEquals(UNKNOWN,parser.parse(new GestoPagoTransport.Reply(500,xml("70","-1","0"))).decision());
    }
}
