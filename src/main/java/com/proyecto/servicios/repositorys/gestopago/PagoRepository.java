package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Transacciones cortas. Orden de bloqueos: cuenta, identidad, producto, operación/outbox. Sin HTTP. */
@Repository
@Profile("(live | prod) & !simulacion")
public class PagoRepository {
    private final JdbcTemplate sql;
    private final TransactionTemplate tx;
    public PagoRepository(@Qualifier("sfDatasource") DataSource ds,
            @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        sql=new JdbcTemplate(ds);tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Trabajo(UUID id,UUID owner,boolean confirmar,String payloadCifrado,BigDecimal monto) {
        @Override public String toString(){return "Trabajo[id="+id+", confirmar="+confirmar+"]";}
    }
    private record Operacion(Pago pago,long usuario,int producto,int servicio,LocalDateTime version,String cifrado,boolean enviado) {}
    private Operacion leer(UUID id) {
        return sql.query("SELECT * FROM pago_operaciones WHERE id=?",(r,n)->new Operacion(
                new Pago(id,r.getLong("cuenta_id"),r.getBigDecimal("monto"),Estado.valueOf(r.getString("estado")),r.getBoolean("revision_manual")),
                r.getLong("usuario_id"),r.getInt("id_producto"),r.getInt("id_servicio"),
                r.getTimestamp("catalogo_version").toLocalDateTime(),r.getString("payload_cifrado"),r.getTimestamp("enviado_en")!=null),id)
                .stream().findFirst().orElseThrow(()->new PagoException(404,"RECURSO_NO_ENCONTRADO"));
    }
    private long cuenta(long usuario,long cuenta,boolean activa) {
        bloquearCuenta(cuenta);
        var rows=sql.query("""
            SELECT c.cliente_id,c.estatus FROM cuentas c JOIN usuarios u ON u.cliente_id=c.cliente_id
            JOIN clientes cl ON cl.id=c.cliente_id WHERE c.id=? AND u.id=? AND u.activo
            AND cl.estatus='ACTIVO' FOR SHARE OF u,cl
            """,(r,n)->new Object[]{r.getLong(1),r.getString(2)},cuenta,usuario);
        if(rows.isEmpty())throw new PagoException(404,"RECURSO_NO_ENCONTRADO");
        if(activa && !"ACTIVA".equals(rows.get(0)[1]))throw new PagoException(422,"CUENTA_INACTIVA");
        return (long)rows.get(0)[0];
    }
    private void bloquearCuenta(long cuenta) {
        if(sql.query("SELECT id FROM cuentas WHERE id=? FOR UPDATE",(r,n)->r.getLong(1),cuenta).isEmpty())
            throw new PagoException(404,"RECURSO_NO_ENCONTRADO");
    }
    private Saldo saldoBloqueado(long cuenta) {
        return sql.queryForObject("SELECT saldo,saldo_reservado FROM cuentas WHERE id=?",(r,n)->
                new Saldo(cuenta,r.getBigDecimal(1),r.getBigDecimal(2),r.getBigDecimal(1).subtract(r.getBigDecimal(2))),cuenta);
    }
    public Saldo saldo(long usuario,long cuenta){return tx.execute(s->{cuenta(usuario,cuenta,false);return saldoBloqueado(cuenta);});}
    public void prevalidar(long usuario,long cuenta,BigDecimal monto){tx.executeWithoutResult(s->{
        cuenta(usuario,cuenta,true);
        if(saldoBloqueado(cuenta).disponible().compareTo(monto)<0)throw new PagoException(422,"SALDO_INSUFICIENTE");
    });}
    public Pago consultar(long usuario,UUID id){return tx.execute(s->{var p=leer(id);cuenta(usuario,p.pago.cuentaId(),false);return leer(id).pago;});}
    public Pago porClave(long usuario,long cuenta,String key,String huella) {
        return tx.execute(s->{long cliente=cuenta(usuario,cuenta,false);return previo(cliente,key,huella);});
    }
    private Pago previo(long cliente,String key,String huella) {
        return sql.query("SELECT id,huella FROM pago_operaciones WHERE cliente_id=? AND idempotencia=?",(r,n)->{
            if(!huella.equals(r.getString(2)))throw new PagoException(409,"IDEMPOTENCIA_CONFLICTO");
            return leer(r.getObject(1,UUID.class)).pago;
        },cliente,key).stream().findFirst().orElse(null);
    }
    public Pago reservar(long usuario,long cuenta,String key,String huella,BigDecimal monto,UUID id,String upc,
            int servicio,int producto,LocalDateTime version,String cifrado,boolean bloqueo24h) {
        return tx.execute(s->{
            // Serializa la regla documentada servicio+monto incluso entre cuentas e instancias.
            if(bloqueo24h)sql.queryForObject("SELECT pg_advisory_xact_lock(7853,hashtext(?))",Object.class,servicio+":"+monto.setScale(2));
            long cliente=cuenta(usuario,cuenta,true);
            Pago anterior=previo(cliente,key,huella);if(anterior!=null)return anterior;
            if(!catalogoValido(producto,servicio,version))throw new PagoException(422,"CATALOGO_CAMBIO");
            if(bloqueo24h && sql.queryForObject("""
                SELECT count(*) FROM pago_operaciones WHERE id_servicio=? AND monto=?
                AND creado_en>clock_timestamp()-interval '24 hours'
                """,Long.class,servicio,monto)>0)throw new PagoException(409,"POSIBLE_DUPLICADO_SERVICIO_MONTO");
            if(saldoBloqueado(cuenta).disponible().compareTo(monto)<0)throw new PagoException(422,"SALDO_INSUFICIENTE");
            sql.update("UPDATE cuentas SET saldo_reservado=saldo_reservado+? WHERE id=?",monto,cuenta);
            sql.update("""
                INSERT INTO pago_operaciones(id,cliente_id,usuario_id,cuenta_id,idempotencia,huella,monto,estado,upc,
                id_servicio,id_producto,catalogo_version,payload_cifrado) VALUES(?,?,?,?,?,?,?,'RESERVED',?,?,?,?,?)
                """,id,cliente,usuario,cuenta,key,huella,monto,upc,servicio,producto,Timestamp.valueOf(version),cifrado);
            sql.update("INSERT INTO pago_outbox(pago_id) VALUES(?)",id);
            movimiento(leer(id).pago,"RESERVA");evento(id,"RESERVED",null,null);
            return leer(id).pago;
        });
    }
    private boolean catalogoValido(int producto,int servicio,LocalDateTime version) {
        return !sql.query("""
            SELECT id FROM gestopago_productos WHERE id_producto=? AND id_servicio=? AND activo
            AND fecha_actualizacion=? FOR SHARE
            """,(r,n)->r.getInt(1),producto,servicio,Timestamp.valueOf(version)).isEmpty();
    }
    public List<UUID> pendientes(boolean confirmar){return sql.query("""
        SELECT pago_id FROM pago_outbox WHERE tarea=? AND owner IS NULL
        AND siguiente_en<=clock_timestamp() ORDER BY siguiente_en LIMIT 20
        """,(r,n)->r.getObject(1,UUID.class),confirmar?"CONFIRM":"SEND");}
    public Trabajo reclamar(UUID id) {
        return tx.execute(s->{
            var p=leer(id);bloquearCuenta(p.pago.cuentaId());p=leer(id);
            UUID owner=UUID.randomUUID();
            int count=sql.update("""
                UPDATE pago_outbox SET owner=?,lease_hasta=clock_timestamp()+interval '180 seconds'
                WHERE pago_id=? AND owner IS NULL AND tarea<>'DONE' AND siguiente_en<=clock_timestamp()
                """,owner,id);
            if(count!=1)return null;
            boolean confirm=p.pago.estado()==Estado.UNKNOWN;
            if(!confirm && p.pago.estado()!=Estado.RESERVED){cerrar(id);return null;}
            if(confirm && !ventana(id)){escalar(id);return null;}
            if(!confirm)cambiar(id,Estado.PROCESSING);
            return new Trabajo(id,owner,confirm,p.cifrado,p.pago.monto());
        });
    }
    private boolean ventana(UUID id){return Boolean.TRUE.equals(sql.queryForObject("""
        SELECT enviado_en<=clock_timestamp()-interval '62 seconds' AND enviado_en>clock_timestamp()-interval '24 hours'
        FROM pago_operaciones WHERE id=?
        """,Boolean.class,id));}
    /** Commit inmediatamente antes del único POST; cualquier caída posterior se considera incierta. */
    public boolean comenzarHttp(Trabajo t) {
        return tx.execute(s->{
            var p=leer(t.id());bloquearCuenta(p.pago.cuentaId());p=leer(t.id());
            if(sql.queryForObject("SELECT count(*) FROM pago_outbox WHERE pago_id=? AND owner=? AND lease_hasta>clock_timestamp()",Integer.class,t.id(),t.owner())!=1)return false;
            if(t.confirmar()) {
                if(p.pago.estado()!=Estado.UNKNOWN || !ventana(t.id())){escalar(t.id());return false;}
                sql.update("UPDATE pago_outbox SET intentos_confirmacion=intentos_confirmacion+1 WHERE pago_id=?",t.id());
                evento(t.id(),"CONFIRM_REQUEST",null,null);return true;
            }
            if(p.pago.estado()!=Estado.PROCESSING || p.enviado)return false;
            try {cuenta(p.usuario,p.pago.cuentaId(),true);}
            catch(PagoException ex){resolver(p.pago,Estado.FAILED);return false;}
            if(!catalogoValido(p.producto,p.servicio,p.version)){resolver(p.pago,Estado.FAILED);return false;}
            sql.update("UPDATE pago_operaciones SET enviado_en=clock_timestamp() WHERE id=? AND enviado_en IS NULL",t.id());
            evento(t.id(),"SEND_REQUEST",null,null);return true;
        });
    }
    public void antesDeEnviarFallo(Trabajo t) {
        tx.executeWithoutResult(s->{
            var p=leer(t.id());bloquearCuenta(p.pago.cuentaId());p=leer(t.id());
            if(!esOwner(t))return;
            if(!t.confirmar() && !p.enviado && p.pago.estado()==Estado.PROCESSING)resolver(p.pago,Estado.FAILED);
            else reprogramar(t.id());
        });
    }
    private boolean esOwner(Trabajo t){return sql.queryForObject("SELECT count(*) FROM pago_outbox WHERE pago_id=? AND owner=?",Integer.class,t.id(),t.owner())==1;}
    public void finalizar(Trabajo t,Estado estado,String codigo,String idTx,String evidenciaCifrada) {
        if(!Set.of(Estado.UNKNOWN,Estado.CAPTURED,Estado.FAILED).contains(estado))throw new IllegalArgumentException();
        tx.executeWithoutResult(s->{
            var p=leer(t.id());bloquearCuenta(p.pago.cuentaId());p=leer(t.id());
            evento(t.id(),t.confirmar()?"CONFIRM_RESPONSE":"SEND_RESPONSE",codigo,evidenciaCifrada);
            if(p.pago.estado()==Estado.CAPTURED || p.pago.estado()==Estado.FAILED){
                if(estado!=Estado.UNKNOWN && estado!=p.pago.estado()){
                    sql.update("UPDATE pago_operaciones SET revision_manual=true WHERE id=?",t.id());
                    evento(t.id(),"DISCREPANCIA",codigo,null);
                }
                return;
            }
            if(estado==Estado.UNKNOWN && !esOwner(t))return;
            if(estado==Estado.FAILED && !t.confirmar())throw new IllegalArgumentException("Sin evidencia de confirmación.");
            if(estado==Estado.FAILED && Boolean.TRUE.equals(sql.queryForObject("SELECT envio_recuperado FROM pago_operaciones WHERE id=?",Boolean.class,t.id()))) {
                // No existe fencing externo documentado. Un emisor pausado podría reanudarse tras vencer su lease.
                // Un 70 no permite liberar automáticamente en ese caso; un 06 todavía permite capturar.
                escalar(t.id());return;
            }
            sql.update("UPDATE pago_operaciones SET codigo_proveedor=?,id_tx=? WHERE id=?",codigo,idTx,t.id());
            if(estado==Estado.UNKNOWN){cambiar(t.id(),estado);reprogramar(t.id());}
            else resolver(p.pago,estado);
        });
    }
    private void resolver(Pago p,Estado estado) {
        if(estado==Estado.CAPTURED){
            sql.update("UPDATE cuentas SET saldo=saldo-?,saldo_reservado=saldo_reservado-? WHERE id=?",p.monto(),p.monto(),p.cuentaId());
            movimiento(p,"CAPTURA");
        }else if(estado==Estado.FAILED){
            sql.update("UPDATE cuentas SET saldo_reservado=saldo_reservado-? WHERE id=?",p.monto(),p.cuentaId());
            movimiento(p,"LIBERACION");
        }
        cambiar(p.id(),estado);cerrar(p.id());
    }
    private void cambiar(UUID id,Estado estado){
        sql.update("UPDATE pago_operaciones SET estado=?,actualizado_en=clock_timestamp() WHERE id=?",estado.name(),id);
        evento(id,estado.name(),null,null);
    }
    private void cerrar(UUID id){sql.update("UPDATE pago_outbox SET tarea='DONE',owner=NULL,lease_hasta=NULL WHERE pago_id=?",id);}
    private void escalar(UUID id){sql.update("UPDATE pago_operaciones SET revision_manual=true WHERE id=?",id);evento(id,"REVISION_MANUAL",null,null);cerrar(id);}
    private void reprogramar(UUID id){
        sql.update("""
            UPDATE pago_outbox SET tarea='CONFIRM',owner=NULL,lease_hasta=NULL,
            siguiente_en=GREATEST(clock_timestamp()+interval '30 seconds',
                (SELECT enviado_en+interval '62 seconds' FROM pago_operaciones WHERE id=?)) WHERE pago_id=?
            """,id,id);
    }
    public void recuperar() {
        var ids=sql.query("SELECT pago_id FROM pago_outbox WHERE owner IS NOT NULL AND lease_hasta<clock_timestamp() LIMIT 100",(r,n)->r.getObject(1,UUID.class));
        for(UUID id:ids)tx.executeWithoutResult(s->{
            var p=leer(id);bloquearCuenta(p.pago.cuentaId());p=leer(id);
            if(sql.queryForObject("SELECT count(*) FROM pago_outbox WHERE pago_id=? AND owner IS NOT NULL AND lease_hasta<clock_timestamp()",Integer.class,id)!=1)return;
            if(p.pago.estado()==Estado.PROCESSING && !p.enviado)resolver(p.pago,Estado.FAILED);
            else if(p.pago.estado()==Estado.PROCESSING || p.pago.estado()==Estado.UNKNOWN){
                if(p.pago.estado()==Estado.PROCESSING)sql.update("UPDATE pago_operaciones SET envio_recuperado=true WHERE id=?",id);
                cambiar(id,Estado.UNKNOWN);reprogramar(id);
            }
            else cerrar(id);
        });
        // Fuera de 24h: jamás consultar, reenviar ni liberar por antigüedad.
        var vencidos=sql.query("SELECT id FROM pago_operaciones WHERE estado='UNKNOWN' AND NOT revision_manual AND enviado_en<=clock_timestamp()-interval '24 hours'",(r,n)->r.getObject(1,UUID.class));
        for(UUID id:vencidos)tx.executeWithoutResult(s->{var p=leer(id);bloquearCuenta(p.pago.cuentaId());if(leer(id).pago.estado()==Estado.UNKNOWN)escalar(id);});
    }
    private void movimiento(Pago p,String tipo){sql.update("INSERT INTO pago_movimientos(pago_id,cuenta_id,tipo,monto) VALUES(?,?,?,?)",p.id(),p.cuentaId(),tipo,p.monto());}
    private void evento(UUID id,String tipo,String codigo,String evidencia){sql.update("INSERT INTO pago_eventos(pago_id,tipo,codigo,evidencia_cifrada) VALUES(?,?,?,?)",id,tipo,codigo,evidencia);}
}
