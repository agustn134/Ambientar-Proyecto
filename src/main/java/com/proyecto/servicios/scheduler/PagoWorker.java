package com.proyecto.servicios.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.integration.gestopago.*;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.*;
import com.proyecto.servicios.repositorys.gestopago.PagoRepository;
import com.proyecto.servicios.service.PagoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Profile("(live | prod) & !simulacion")
public class PagoWorker {
    private final PagoRepository repo;
    private final PagoService pagos;
    private final GestoPagoPaymentClient client;
    private final GestoPagoOutcomeParser sendParser;
    private final GestoPagoConfirmationParser confirmParser;
    private final TokenCacheCipher storage;
    private final ObjectMapper mapper;
    private final boolean enabled;
    public PagoWorker(PagoRepository repo,PagoService pagos,GestoPagoPaymentClient client,GestoPagoOutcomeParser sendParser,
            GestoPagoConfirmationParser confirmParser,ObjectMapper mapper,
            @Value("${gestopago.payment.storage-key-base64:}") String key,
            @Value("${gestopago.payment.worker-enabled:true}") boolean enabled) {
        this.repo=repo;this.pagos=pagos;this.client=client;this.sendParser=sendParser;this.confirmParser=confirmParser;
        this.mapper=mapper;this.storage=new TokenCacheCipher(key);this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${gestopago.payment.worker-delay-ms:5000}",initialDelay=10000)
    public void pendientes() {
        if(!enabled)return;
        repo.recuperar();
        for(UUID id:repo.pendientes(false)) {
            try { ejecutar(id); }
            catch(RuntimeException ex) { /* La tarea persistida se recupera por lease; no se imprimen datos externos. */ }
        }
    }
    public void ejecutar(UUID id) {
        var tarea=repo.reclamar(id);if(tarea==null)return;
        GestoPagoPaymentClient.Prepared prepared;
        try {
            String json=storage.decrypt(tarea.payloadCifrado());
            // Conciliar una venta iniciada no depende de que su producto siga activo hoy.
            if(!tarea.confirmar())pagos.validarPolitica(mapper.readTree(json).path("idProducto").asInt());
            prepared=client.preparar(json);
        } catch(Exception ex) { repo.antesDeEnviarFallo(tarea);return; }
        if(!repo.comenzarHttp(tarea))return;
        Estado resultado=Estado.UNKNOWN;String codigo=null,idTx=null,evidencia=null;
        try {
            var response=client.ejecutar(prepared,tarea.confirmar());
            evidencia=storage.encrypt(response.status()+"\n"+response.body());
            if(tarea.confirmar()) {
                var r=confirmParser.parse(response);codigo=r.code();idTx=r.transactionId();
                resultado=switch(r.decision()) {case APPLIED->Estado.CAPTURED;case NOT_APPLIED->Estado.FAILED;default->Estado.UNKNOWN;};
            } else {
                var r=sendParser.parse(response,tarea.monto());codigo=r.code();idTx=r.transactionId();
                if(r.state()==GestoPagoOutcomeParser.State.CONFIRMED)resultado=Estado.CAPTURED;
            }
        } catch(RuntimeException ex) { /* Timeout/desconexión: UNKNOWN conserva la reserva. */ }
        if(codigo!=null && !codigo.matches("[0-9]{2}"))codigo=null;
        repo.finalizar(tarea,resultado,codigo,idTx,evidencia);
    }
}
