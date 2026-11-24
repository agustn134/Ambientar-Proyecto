package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.OtpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OtpCorreoService {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String remitente;
    private final boolean buzonLocalPruebas;
    private final Map<String, String> ultimoCodigoPorCorreo = new ConcurrentHashMap<>();

    public OtpCorreoService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${gpago.mail.from:${spring.mail.username:no-reply@gpago.local}}") String remitente,
            @Value("${auth.otp.buzon-local-pruebas:false}") boolean buzonLocalPruebas) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.remitente = remitente;
        this.buzonLocalPruebas = buzonLocalPruebas;
    }

    public void enviarOtp(String correo, String codigoOtp, long ttlSeconds, String plataforma) {
        String normalizado = correo.toLowerCase(Locale.ROOT);
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!mailHost.isEmpty() && sender != null) {
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                if (remitente != null && !remitente.isBlank()) {
                    mensaje.setFrom(remitente);
                }
                mensaje.setTo(normalizado);
                mensaje.setSubject("GPago · Código de verificación de inicio de sesión");
                long minutos = Math.max(1L, ttlSeconds / 60L);
                mensaje.setText("Tu código de verificación de un solo uso es: " + codigoOtp
                    + "\n\nVálido durante " + minutos + " minutos para iniciar sesión en " + plataforma
                    + ".\nSi no reconoces este intento, no compartas este código y cambia tu contraseña.");
                sender.send(mensaje);
                log.info("Código OTP enviado por correo para inicio en plataforma={}", plataforma);
                if (buzonLocalPruebas) {
                    ultimoCodigoPorCorreo.put(normalizado, codigoOtp);
                }
                return;
            } catch (MailException ex) {
                log.error("Fallo al enviar OTP por correo; tipo={}", ex.getClass().getSimpleName());
                throw new OtpException(503, "SERVICIO_CORREO_NO_DISPONIBLE",
                    "No fue posible entregar el código de verificación por correo; intenta más tarde");
            }
        }
        if (buzonLocalPruebas) {
            ultimoCodigoPorCorreo.put(normalizado, codigoOtp);
            log.info("Código OTP registrado en buzón local de pruebas para plataforma={}", plataforma);
            return;
        }
        log.warn("Envío de OTP rechazado: servidor SMTP no configurado");
        throw new OtpException(503, "SERVICIO_CORREO_NO_DISPONIBLE",
            "El servicio de correo para envío de OTP no está configurado");
    }

    public String obtenerUltimoCodigoParaPruebas(String correo) {
        if (!buzonLocalPruebas || correo == null) {
            return null;
        }
        return ultimoCodigoPorCorreo.get(correo.trim().toLowerCase(Locale.ROOT));
    }

    public void limpiarBuzonPruebas() {
        ultimoCodigoPorCorreo.clear();
    }
}
