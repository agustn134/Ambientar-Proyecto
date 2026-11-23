package com.proyecto.servicios.repositorys.simulacion;

import com.proyecto.servicios.exception.SimulacionException;
import com.proyecto.servicios.model.gestopago.simulacion.SimulacionDtos.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** Bloqueos siempre en orden cuenta -> saldo -> pago. Transacciones cortas, sin HTTP. */
@Repository
@Profile("simulacion")
public class SimulacionRepository {
    private final JdbcTemplate sql;
    private final TransactionTemplate tx;
    public SimulacionRepository(@Qualifier("sfDatasource") DataSource ds,
            @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        sql = new JdbcTemplate(ds);
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Pendiente(UUID id, long cuentaId, BigDecimal monto, String cifrado) {
        @Override public String toString() { return "Pendiente[simulacion]"; }
    }
    private long cuenta(long usuario, long cuenta, boolean exigirActiva) {
        var clientes = sql.query("""
            SELECT u.cliente_id FROM usuarios u JOIN clientes c ON c.id=u.cliente_id
            WHERE u.id=? AND u.activo AND c.estatus='ACTIVO' FOR SHARE OF u,c
            """, (r,n)->r.getLong(1), usuario);
        if (clientes.isEmpty()) throw new SimulacionException(401,"NO_AUTENTICADO");
        var filas = sql.query("SELECT estatus FROM cuentas WHERE id=? AND cliente_id=? FOR UPDATE",
                (r,n)->r.getString(1),cuenta,clientes.get(0));
        if (filas.isEmpty()) throw new SimulacionException(404,"RECURSO_NO_ENCONTRADO");
        if (exigirActiva && !"ACTIVA".equals(filas.get(0))) throw new SimulacionException(422,"CUENTA_INACTIVA");
        return clientes.get(0);
    }
    private void crearSaldo(long cuenta) {
        sql.update("INSERT INTO simulacion_saldos(cuenta_id) VALUES (?) ON CONFLICT DO NOTHING",cuenta);
    }
    private Saldo saldoBloqueado(long cuenta) {
        return sql.queryForObject("SELECT * FROM simulacion_saldos WHERE cuenta_id=? FOR UPDATE",(r,n)->{
            var contable=r.getBigDecimal("contabilizado"); var reserva=r.getBigDecimal("reservado");
            return new Saldo(cuenta,contable,reserva,contable.subtract(reserva),true);
        },cuenta);
    }
    public Saldo saldo(long usuario,long cuenta) {
        return tx.execute(s->{cuenta(usuario,cuenta,false);crearSaldo(cuenta);return saldoBloqueado(cuenta);});
    }
    public Saldo fondear(long usuario,long cuenta,BigDecimal monto) {
        return tx.execute(s->{
            cuenta(usuario,cuenta,true);crearSaldo(cuenta);var saldo=saldoBloqueado(cuenta);
            if(saldo.contabilizado().add(monto).compareTo(new BigDecimal("9999999999999.99"))>0)
                throw new SimulacionException(422,"LIMITE_SALDO_SIMULADO");
            sql.update("UPDATE simulacion_saldos SET contabilizado=contabilizado+? WHERE cuenta_id=?",monto,cuenta);
            movimiento(cuenta,null,"FONDEO",monto);return saldoBloqueado(cuenta);
        });
    }
    public Pago reservar(long usuario,long cuenta,String key,String huella,BigDecimal monto,
                         UUID id,String upc,String cifrado) {
        return tx.execute(s->{
            long cliente=cuenta(usuario,cuenta,true);
            var previos=sql.query("SELECT * FROM simulacion_pagos WHERE cliente_id=? AND idempotencia=?",
                    (r,n)->{if(!huella.equals(r.getString("huella")))throw new SimulacionException(409,"IDEMPOTENCIA_CONFLICTO");return pago(r);},cliente,key);
            if(!previos.isEmpty())return previos.get(0);
            crearSaldo(cuenta);
            if(saldoBloqueado(cuenta).disponible().compareTo(monto)<0)throw new SimulacionException(422,"SALDO_SIMULADO_INSUFICIENTE");
            sql.update("UPDATE simulacion_saldos SET reservado=reservado+? WHERE cuenta_id=?",monto,cuenta);
            sql.update("""
                INSERT INTO simulacion_pagos(id,cliente_id,cuenta_id,idempotencia,huella,monto,estado,upc,payload_cifrado)
                VALUES (?,?,?,?,?,?,'RESERVED',?,?)
                """,id,cliente,cuenta,key,huella,monto,upc,cifrado);
            sql.update("INSERT INTO simulacion_outbox(pago_id) VALUES (?)",id);
            movimiento(cuenta,id,"RESERVA",monto);evento(id,Estado.RESERVED);
            return leer(id);
        });
    }
    public Pago porClave(long usuario,long cuenta,String key,String huella) {
        return tx.execute(s->{
            long cliente=cuenta(usuario,cuenta,true);
            return sql.query("SELECT * FROM simulacion_pagos WHERE cliente_id=? AND idempotencia=?",(r,n)->{
                if(!huella.equals(r.getString("huella")))throw new SimulacionException(409,"IDEMPOTENCIA_CONFLICTO");return pago(r);
            },cliente,key).stream().findFirst().orElse(null);
        });
    }
    private Pago pago(ResultSet r)throws SQLException {
        return new Pago(r.getObject("id",UUID.class),r.getLong("cuenta_id"),r.getBigDecimal("monto"),
                Estado.valueOf(r.getString("estado")),true,false);
    }
    private Pago leer(UUID id) {
        var rows=sql.query("SELECT * FROM simulacion_pagos WHERE id=?",(r,n)->pago(r),id);
        if(rows.isEmpty())throw new SimulacionException(404,"RECURSO_NO_ENCONTRADO");return rows.get(0);
    }
    public Pago consultar(long usuario,UUID id) {
        return tx.execute(s->{var p=leer(id);cuenta(usuario,p.cuentaId(),false);return leer(id);});
    }
    public Pendiente reclamar(long usuario,UUID id) {
        return tx.execute(s->{var p=leer(id);cuenta(usuario,p.cuentaId(),false);return reclamarBloqueado(id);});
    }
    public Pendiente reclamarInterno(UUID id) {
        return tx.execute(s->{var p=leer(id);bloquearCuenta(p.cuentaId());return reclamarBloqueado(id);});
    }
    private Pendiente reclamarBloqueado(UUID id) {
        // Una sola instancia puede reclamarlo. Un lease vencido JAMÁS vuelve a PENDING.
        if(sql.update("""
            UPDATE simulacion_outbox SET estado='CLAIMED',vence_en=clock_timestamp()+interval '60 seconds'
            WHERE pago_id=? AND estado='PENDING'
            """,id)!=1)return null;
        cambiar(id,Estado.PROCESSING);
        var pago=leer(id);
        int autorizadas=sql.queryForObject("""
            SELECT count(*) FROM cuentas c JOIN clientes cl ON cl.id=c.cliente_id
            JOIN usuarios u ON u.cliente_id=cl.id
            WHERE c.id=? AND c.estatus='ACTIVA' AND cl.estatus='ACTIVO' AND u.activo
            """,Integer.class,pago.cuentaId());
        if(autorizadas!=1){
            saldoBloqueado(pago.cuentaId());
            resolverBloqueado(pago,Estado.FAILED);
            return null;
        }
        return sql.queryForObject("SELECT * FROM simulacion_pagos WHERE id=?",(r,n)->
                new Pendiente(id,r.getLong("cuenta_id"),r.getBigDecimal("monto"),r.getString("payload_cifrado")),id);
    }
    private void bloquearCuenta(long id) {
        sql.queryForObject("SELECT id FROM cuentas WHERE id=? FOR UPDATE",Long.class,id);
    }
    public List<UUID> pendientes() {
        return sql.query("SELECT pago_id FROM simulacion_outbox WHERE estado='PENDING' ORDER BY pago_id LIMIT 20",(r,n)->r.getObject(1,UUID.class));
    }
    public void recuperarInciertos() {
        var ids=sql.query("SELECT pago_id FROM simulacion_outbox WHERE estado='CLAIMED' AND vence_en<clock_timestamp() LIMIT 100",(r,n)->r.getObject(1,UUID.class));
        for(var id:ids)tx.executeWithoutResult(s->{
            var p=leer(id);bloquearCuenta(p.cuentaId());
            if(sql.update("UPDATE simulacion_outbox SET estado='DONE' WHERE pago_id=? AND estado='CLAIMED' AND vence_en<clock_timestamp()",id)==1
                    && leer(id).estado()==Estado.PROCESSING)cambiar(id,Estado.UNKNOWN);
        });
    }
    public void finalizar(UUID id,Estado resultado) {
        if(!Set.of(Estado.CAPTURED,Estado.FAILED,Estado.UNKNOWN).contains(resultado))throw new IllegalArgumentException();
        tx.executeWithoutResult(s->{
            var inicial=leer(id);bloquearCuenta(inicial.cuentaId());saldoBloqueado(inicial.cuentaId());
            var p=leer(id);
            if(p.estado()!=Estado.PROCESSING && p.estado()!=Estado.UNKNOWN)return;
            resolverBloqueado(p,resultado);
        });
    }
    private void resolverBloqueado(Pago p,Estado resultado) {
            UUID id=p.id();
            if(resultado==Estado.CAPTURED){
                sql.update("UPDATE simulacion_saldos SET contabilizado=contabilizado-?,reservado=reservado-? WHERE cuenta_id=?",p.monto(),p.monto(),p.cuentaId());
                movimiento(p.cuentaId(),id,"CAPTURA",p.monto());
            }else if(resultado==Estado.FAILED){
                sql.update("UPDATE simulacion_saldos SET reservado=reservado-? WHERE cuenta_id=?",p.monto(),p.cuentaId());
                movimiento(p.cuentaId(),id,"LIBERACION",p.monto());
            }
            if(p.estado()!=resultado)cambiar(id,resultado);
            sql.update("UPDATE simulacion_outbox SET estado='DONE',vence_en=NULL WHERE pago_id=?",id);
    }
    private void cambiar(UUID id,Estado estado) {
        sql.update("UPDATE simulacion_pagos SET estado=?,actualizado_en=clock_timestamp() WHERE id=?",estado.name(),id);
        evento(id,estado);
    }
    private void evento(UUID id,Estado estado){sql.update("INSERT INTO simulacion_eventos(pago_id,estado) VALUES (?,?)",id,estado.name());}
    private void movimiento(long cuenta,UUID id,String tipo,BigDecimal monto){
        sql.update("INSERT INTO simulacion_movimientos(cuenta_id,pago_id,tipo,monto) VALUES (?,?,?,?)",cuenta,id,tipo,monto);
    }
    /** El resultado del proveedor ficticio sobrevive al fallo del consumidor. */
    public void registrarProveedor(UUID id,boolean aplicado,BigDecimal monto) {
        tx.executeWithoutResult(s->sql.update("INSERT INTO simulacion_proveedor(pago_id,aplicado,monto) VALUES (?,?,?) ON CONFLICT DO NOTHING",id,aplicado,monto));
    }
    public record Evidencia(boolean aplicado,long idTx,BigDecimal monto) {}
    public Optional<Evidencia> evidencia(UUID id) {
        return sql.query("SELECT * FROM simulacion_proveedor WHERE pago_id=?",(r,n)->new Evidencia(r.getBoolean("aplicado"),r.getLong("id_tx"),r.getBigDecimal("monto")),id).stream().findFirst();
    }
}
