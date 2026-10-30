package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.simulacion.PagoSimuladoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("simulacion")
public class PagoSimuladoWorker {
    private final PagoSimuladoService pagos;
    private final boolean enabled;
    public PagoSimuladoWorker(PagoSimuladoService pagos,@Value("${simulacion.worker.enabled:false}") boolean enabled){this.pagos=pagos;this.enabled=enabled;}
    @Scheduled(fixedDelayString="${simulacion.worker.delay-ms:10000}")
    public void ejecutar(){
        pagos.recuperarInciertos();
        if(enabled)pagos.procesarPendientes();
    }
}
