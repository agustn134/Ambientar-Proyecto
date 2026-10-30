package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.pago.PagoDtos.*;
import com.proyecto.servicios.repositorys.gestopago.PagoRepository;
import com.proyecto.servicios.scheduler.PagoWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Solo selecciona consultas CONFIRM. Nunca crea una nueva venta. */
@Service
@Profile("(live | prod) & !simulacion")
public class PagoReconciliator {
    private final PagoRepository repo;
    private final PagoWorker worker;
    private final boolean enabled;
    public PagoReconciliator(PagoRepository repo,PagoWorker worker,
            @Value("${gestopago.payment.worker-enabled:true}") boolean enabled){this.repo=repo;this.worker=worker;this.enabled=enabled;}
    @Scheduled(fixedDelayString="${gestopago.payment.confirm-delay-ms:10000}",initialDelay=15000)
    public void pendientes(){
        if(!enabled)return;
        for(UUID id:repo.pendientes(true)){
            try{worker.ejecutar(id);}
            catch(RuntimeException ex){/* Recuperación por lease, sin imprimir información financiera. */}
        }
    }
    public Pago conciliar(long usuario,UUID id){
        var pago=repo.consultar(usuario,id);
        if(pago.estado()==Estado.UNKNOWN)worker.ejecutar(id);
        return repo.consultar(usuario,id);
    }
}
