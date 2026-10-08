package com.proyecto.servicios.config;

import feign.Request;
import feign.Response;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class LoggingSecurityTest {
    private static final String SECRETO = "password=NoPublicar123! token=NoPublicarToken";

    private ProceedingJoinPoint invocation() {
        ProceedingJoinPoint invocation = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(invocation.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("RegistroClienteService");
        when(signature.getName()).thenReturn("registrar");
        return invocation;
    }

    @Test
    void registraInicioYFinSinInspeccionarArgumentos(CapturedOutput output) throws Throwable {
        ProceedingJoinPoint invocation = invocation();
        when(invocation.proceed()).thenReturn(SECRETO);
        assertEquals(SECRETO, new LoggingAspect().logAround(invocation));
        assertTrue(output.getAll().contains("Iniciando ejecución"));
        assertTrue(output.getAll().contains("Finalizando ejecución"));
        assertTrue(output.getAll().contains("resultado=EXITO"));
        assertFalse(output.getAll().contains(SECRETO));
        verify(invocation, never()).getArgs();
    }

    @Test
    void registraFinTambienAnteErrorSinFiltrarMensaje(CapturedOutput output) throws Throwable {
        ProceedingJoinPoint invocation = invocation();
        IllegalArgumentException error = new IllegalArgumentException(SECRETO);
        when(invocation.proceed()).thenThrow(error);
        assertSame(error, assertThrows(IllegalArgumentException.class, () -> new LoggingAspect().logAround(invocation)));
        assertTrue(output.getAll().contains("tipo=IllegalArgumentException"));
        assertTrue(output.getAll().contains("resultado=ERROR"));
        assertFalse(output.getAll().contains(SECRETO));
        verify(invocation, never()).getArgs();
    }

    @Test
    void errorExternoNoRegistraBodyNiQuery(CapturedOutput output) {
        Request request = Request.create(Request.HttpMethod.GET,
                "https://proveedor.example/catalogo?password=NoPublicar123!", Map.of(),
                null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(404).reason("Not found")
                .request(request).headers(Map.of()).body(SECRETO, StandardCharsets.UTF_8).build();
        Exception error = new GestoPagoErrorDecoder().decode("ProductClient#catalogo", response);
        assertTrue(output.getAll().contains("status=404"));
        assertFalse(output.getAll().contains("NoPublicar"));
        assertFalse(error.getMessage().contains("NoPublicar"));
    }

    @Test
    void manejadorGlobalNoRegistraStackTraceConDatosSensibles(CapturedOutput output) {
        var response = new GlobalExceptionHandler().handleGenericException(
                new IllegalStateException(SECRETO), new MockHttpServletRequest());
        assertEquals(500, response.getStatusCode().value());
        assertTrue(output.getAll().contains("tipo=IllegalStateException"));
        assertFalse(output.getAll().contains(SECRETO));
    }
}
