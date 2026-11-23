package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.usuario.*;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.auth.*;
import com.proyecto.servicios.repositorys.usuario.UsuarioDesafioOtpRepository;
import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import com.proyecto.servicios.repositorys.usuario.UsuarioSesionActivaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginService {
    private static final int MAX_INTENTOS_PASSWORD = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final UsuarioDesafioOtpRepository desafios;
    private final UsuarioSesionActivaRepository sesionesActivas;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final OtpCorreoService otpCorreo;
    private final Clock clock;
    private final long otpTtlSeconds;
    private final int maxIntentosOtp;
    private final int maxReenviosOtp;
    private final String hashInexistente;

    @PersistenceContext(unitName = "sfDatasource")
    private EntityManager entityManager;

    public LoginService(
            UsuarioRepository usuarios,
            UsuarioDesafioOtpRepository desafios,
            UsuarioSesionActivaRepository sesionesActivas,
            PasswordEncoder passwords,
            JwtService tokens,
            OtpCorreoService otpCorreo,
            Clock authClock,
            @Value("${security.otp.ttl-seconds:180}") long otpTtlSeconds,
            @Value("${security.otp.max-intentos:3}") int maxIntentosOtp,
            @Value("${security.otp.max-reenvios:3}") int maxReenviosOtp) {
        if (otpTtlSeconds < 30 || otpTtlSeconds > 900) {
            throw new IllegalArgumentException("OTP TTL debe estar entre 30 y 900 segundos");
        }
        if (maxIntentosOtp < 1 || maxReenviosOtp < 0) {
            throw new IllegalArgumentException("Límites OTP inválidos");
        }
        this.usuarios = usuarios;
        this.desafios = desafios;
        this.sesionesActivas = sesionesActivas;
        this.passwords = passwords;
        this.tokens = tokens;
        this.otpCorreo = otpCorreo;
        this.clock = authClock;
        this.otpTtlSeconds = otpTtlSeconds;
        this.maxIntentosOtp = maxIntentosOtp;
        this.maxReenviosOtp = maxReenviosOtp;
        this.hashInexistente = passwords.encode(UUID.randomUUID().toString());
    }

    /**
     * Valida el primer factor (correo + contraseña), bloquea tras 3 intentos fallidos
     * y genera un desafío OTP efímero enviado por correo sin emitir JWT todavía.
     */
    @Transactional(value = "sfTransactionManager", noRollbackFor = {CredencialesInvalidasException.class, OtpException.class})
    public Optional<DesafioOtpResponse> iniciarDesafio(LoginRequest request) {
        var encontrado = usuarios.buscarParaLogin(request.correo());
        if (encontrado.isEmpty()) {
            passwords.matches(request.password(), hashInexistente);
            return Optional.empty();
        }
        var usuario = encontrado.get();
        if (!usuario.isActivo() || !"ACTIVO".equals(usuario.getCliente().getEstatus())) {
            passwords.matches(request.password(), hashInexistente);
            return Optional.empty();
        }
        Instant ahora = clock.instant();
        LocalDateTime ahoraUtc = utc(ahora);
        if (!passwords.matches(request.password(), usuario.getPasswordHash())) {
            int intentos = Math.min(MAX_INTENTOS_PASSWORD, usuario.getIntentosFallidos() + 1);
            usuario.setIntentosFallidos(intentos);
            if (intentos == MAX_INTENTOS_PASSWORD) {
                usuario.setActivo(false);
                usuario.setVersionToken(usuario.getVersionToken() + 1);
                revocarSesionActivaExistente(usuario.getId(), usuario.getVersionToken(), ahoraUtc);
            }
            usuarios.saveAndFlush(usuario);
            return Optional.empty();
        }
        usuario.setIntentosFallidos(0);
        usuarios.saveAndFlush(usuario);

        var activos = desafios.findAllByUsuarioIdAndEstadoIn(
            usuario.getId(), List.of(EstadoDesafioOtp.PENDIENTE, EstadoDesafioOtp.CONFLICTO_PENDIENTE));
        for (var previo : activos) {
            previo.setEstado(EstadoDesafioOtp.CANCELADO);
            previo.setActualizadoEn(ahoraUtc);
        }
        if (!activos.isEmpty()) {
            desafios.saveAll(activos);
        }

        String codigoOtp = generarCodigoOtp();
        Instant expira = ahora.plusSeconds(otpTtlSeconds);
        LocalDateTime expiraUtc = utc(expira);

        UsuarioDesafioOtp desafio = new UsuarioDesafioOtp();
        desafio.setId(UUID.randomUUID());
        desafio.setUsuarioId(usuario.getId());
        desafio.setOtpHash(passwords.encode(codigoOtp));
        desafio.setPlataforma(request.plataforma());
        desafio.setEstado(EstadoDesafioOtp.PENDIENTE);
        desafio.setIntentosFallidos(0);
        desafio.setReenvios(0);
        desafio.setCreadoEn(ahoraUtc);
        desafio.setExpiraEn(expiraUtc);
        desafio.setActualizadoEn(ahoraUtc);
        desafios.saveAndFlush(desafio);

        otpCorreo.enviarOtp(usuario.getCorreo(), codigoOtp, otpTtlSeconds, request.plataforma().name());

        return Optional.of(new DesafioOtpResponse(
            desafio.getId(),
            enmascararCorreo(usuario.getCorreo()),
            desafio.getPlataforma(),
            true,
            otpTtlSeconds,
            expira,
            maxIntentosOtp,
            maxReenviosOtp
        ));
    }

    @Transactional(value = "sfTransactionManager", noRollbackFor = {OtpException.class, CredencialesInvalidasException.class})
    public DesafioOtpResponse reenviarOtp(ReenviarOtpRequest request) {
        var preliminar = desafios.findById(request.desafioId())
            .orElseThrow(() -> new OtpException(401, "OTP_INVALIDO", "El desafío OTP no existe o ya no está vigente"));
        var usuario = bloquearUsuarioActivo(preliminar.getUsuarioId());
        var desafio = bloquearDesafio(request.desafioId());

        Instant ahora = clock.instant();
        LocalDateTime ahoraUtc = utc(ahora);
        if (desafio.getEstado() == EstadoDesafioOtp.EXPIRADO || !desafio.getExpiraEn().isAfter(ahoraUtc)) {
            if (desafio.getEstado() == EstadoDesafioOtp.PENDIENTE) {
                desafio.setEstado(EstadoDesafioOtp.EXPIRADO);
                desafio.setActualizadoEn(ahoraUtc);
                desafios.saveAndFlush(desafio);
            }
            throw new OtpException(401, "OTP_EXPIRADO", "El código OTP expiró; inicia sesión nuevamente");
        }
        if (desafio.getEstado() != EstadoDesafioOtp.PENDIENTE) {
            throw new OtpException(401, "OTP_INVALIDO", "El desafío OTP ya no acepta reenvíos");
        }
        if (desafio.getReenvios() >= maxReenviosOtp) {
            throw new OtpException(429, "LIMITE_REENVIOS_OTP", "Se alcanzó el límite de reenvíos de código OTP; inicia sesión nuevamente");
        }

        String codigoOtp = generarCodigoOtp();
        Instant expira = ahora.plusSeconds(otpTtlSeconds);
        desafio.setOtpHash(passwords.encode(codigoOtp));
        desafio.setReenvios(desafio.getReenvios() + 1);
        desafio.setIntentosFallidos(0);
        desafio.setExpiraEn(utc(expira));
        desafio.setActualizadoEn(ahoraUtc);
        desafios.saveAndFlush(desafio);

        otpCorreo.enviarOtp(usuario.getCorreo(), codigoOtp, otpTtlSeconds, desafio.getPlataforma().name());

        return new DesafioOtpResponse(
            desafio.getId(),
            enmascararCorreo(usuario.getCorreo()),
            desafio.getPlataforma(),
            true,
            otpTtlSeconds,
            expira,
            maxIntentosOtp,
            maxReenviosOtp - desafio.getReenvios()
        );
    }

    @Transactional(value = "sfTransactionManager", noRollbackFor = {
        OtpException.class, ConflictoSesionActivaException.class, CredencialesInvalidasException.class})
    public LoginResponse verificarOtp(VerificarOtpRequest request) {
        var preliminar = desafios.findById(request.desafioId())
            .orElseThrow(() -> new OtpException(401, "OTP_INVALIDO", "El desafío OTP no es válido o ya fue utilizado"));
        var usuario = bloquearUsuarioActivo(preliminar.getUsuarioId());
        var desafio = bloquearDesafio(request.desafioId());

        if (request.plataforma() != null && request.plataforma() != desafio.getPlataforma()) {
            throw new RegistroClienteException("plataforma", "La plataforma enviada no coincide con la del desafío OTP", 400);
        }

        Instant ahora = clock.instant();
        LocalDateTime ahoraUtc = utc(ahora);
        if (desafio.getEstado() == EstadoDesafioOtp.BLOQUEADO) {
            throw new OtpException(401, "OTP_BLOQUEADO", "El código OTP fue bloqueado tras agotar los intentos permitidos");
        }
        if (desafio.getEstado() == EstadoDesafioOtp.CONSUMIDO || desafio.getEstado() == EstadoDesafioOtp.CANCELADO) {
            throw new OtpException(401, "OTP_INVALIDO", "El código OTP ya fue utilizado o cancelado");
        }
        if (desafio.getEstado() == EstadoDesafioOtp.EXPIRADO || !desafio.getExpiraEn().isAfter(ahoraUtc)) {
            desafio.setEstado(EstadoDesafioOtp.EXPIRADO);
            desafio.setActualizadoEn(ahoraUtc);
            desafios.saveAndFlush(desafio);
            throw new OtpException(401, "OTP_EXPIRADO", "El código OTP expiró; solicita uno nuevo o vuelve a iniciar sesión");
        }
        if (desafio.getEstado() != EstadoDesafioOtp.PENDIENTE) {
            throw new OtpException(401, "OTP_INVALIDO", "El desafío OTP no se encuentra pendiente de verificación");
        }

        if (!passwords.matches(request.codigoOtp(), desafio.getOtpHash())) {
            int fallos = desafio.getIntentosFallidos() + 1;
            desafio.setIntentosFallidos(fallos);
            desafio.setActualizadoEn(ahoraUtc);
            if (fallos >= maxIntentosOtp) {
                desafio.setEstado(EstadoDesafioOtp.BLOQUEADO);
                desafios.saveAndFlush(desafio);
                throw new OtpException(401, "OTP_BLOQUEADO", "Se superó el límite de intentos para el código OTP");
            }
            desafios.saveAndFlush(desafio);
            int restantes = maxIntentosOtp - fallos;
            throw new OtpException(401, "OTP_INVALIDO",
                "Código OTP incorrecto; intentos restantes: " + restantes);
        }

        var sesionExistente = sesionesActivas.buscarPorUsuarioIdParaModificar(usuario.getId());
        if (sesionExistente.isPresent()) {
            var sesion = sesionExistente.get();
            if (sesion.isActiva()
                    && sesion.getVersionToken() == usuario.getVersionToken()
                    && sesion.getExpiraEn().isAfter(ahoraUtc)) {
                String tokenConfirmacion = generarTokenConfirmacion();
                Instant expiraConfirmacion = ahora.plusSeconds(otpTtlSeconds);
                desafio.setEstado(EstadoDesafioOtp.CONFLICTO_PENDIENTE);
                desafio.setTokenConfirmacionHash(passwords.encode(tokenConfirmacion));
                desafio.setExpiraEn(utc(expiraConfirmacion));
                desafio.setActualizadoEn(ahoraUtc);
                desafios.saveAndFlush(desafio);
                throw new ConflictoSesionActivaException(new ConflictoSesionResponse(
                    true,
                    desafio.getId(),
                    tokenConfirmacion,
                    sesion.getPlataforma(),
                    desafio.getPlataforma(),
                    sesion.getCreadaEn().toInstant(ZoneOffset.UTC),
                    expiraConfirmacion
                ));
            }
        }

        desafio.setEstado(EstadoDesafioOtp.CONSUMIDO);
        desafio.setActualizadoEn(ahoraUtc);
        desafios.saveAndFlush(desafio);
        return abrirSesionUnica(usuario, desafio.getPlataforma(), ahora);
    }

    @Transactional(value = "sfTransactionManager", noRollbackFor = {OtpException.class, CredencialesInvalidasException.class})
    public LoginResponse reemplazarSesion(ReemplazarSesionRequest request) {
        var preliminar = desafios.findById(request.desafioId())
            .orElseThrow(() -> new OtpException(401, "CONFIRMACION_SESION_INVALIDA",
                "La confirmación de reemplazo de sesión no es válida o ya expiró"));
        var usuario = bloquearUsuarioActivo(preliminar.getUsuarioId());
        var desafio = bloquearDesafio(request.desafioId());

        Instant ahora = clock.instant();
        LocalDateTime ahoraUtc = utc(ahora);
        if (desafio.getEstado() != EstadoDesafioOtp.CONFLICTO_PENDIENTE || desafio.getTokenConfirmacionHash() == null) {
            throw new OtpException(401, "CONFIRMACION_SESION_INVALIDA",
                "No hay un conflicto de sesión pendiente para confirmar");
        }
        if (!desafio.getExpiraEn().isAfter(ahoraUtc)) {
            desafio.setEstado(EstadoDesafioOtp.EXPIRADO);
            desafio.setTokenConfirmacionHash(null);
            desafio.setActualizadoEn(ahoraUtc);
            desafios.saveAndFlush(desafio);
            throw new OtpException(401, "CONFIRMACION_SESION_INVALIDA",
                "La confirmación expiró; inicia sesión nuevamente");
        }
        if (!passwords.matches(request.tokenConfirmacion(), desafio.getTokenConfirmacionHash())) {
            desafio.setEstado(EstadoDesafioOtp.BLOQUEADO);
            desafio.setTokenConfirmacionHash(null);
            desafio.setActualizadoEn(ahoraUtc);
            desafios.saveAndFlush(desafio);
            throw new OtpException(401, "CONFIRMACION_SESION_INVALIDA",
                "Token de confirmación inválido; la sesión anterior se mantuvo activa");
        }

        desafio.setEstado(EstadoDesafioOtp.CONSUMIDO);
        desafio.setTokenConfirmacionHash(null);
        desafio.setActualizadoEn(ahoraUtc);
        desafios.saveAndFlush(desafio);
        return abrirSesionUnica(usuario, desafio.getPlataforma(), ahora);
    }

    @Transactional("sfTransactionManager")
    public void cerrarSesion(long usuarioId) {
        var usuario = usuarios.buscarParaModificar(usuarioId).orElseThrow(CredencialesInvalidasException::new);
        entityManager.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        LocalDateTime ahoraUtc = utc(clock.instant());
        revocarSesionActivaExistente(usuario.getId(), usuario.getVersionToken(), ahoraUtc);
    }

    private LoginResponse abrirSesionUnica(Usuario usuario, PlataformaCliente plataforma, Instant ahora) {
        LocalDateTime ahoraUtc = utc(ahora);
        LocalDateTime expiraUtc = utc(ahora.plusSeconds(tokens.getTtlSeconds()));
        UUID sesionId = UUID.randomUUID();

        var sesion = sesionesActivas.buscarPorUsuarioIdParaModificar(usuario.getId()).orElseGet(() -> {
            UsuarioSesionActiva nueva = new UsuarioSesionActiva();
            nueva.setUsuarioId(usuario.getId());
            return nueva;
        });
        sesion.setSesionId(sesionId);
        sesion.setPlataforma(plataforma);
        sesion.setVersionToken(usuario.getVersionToken());
        sesion.setActiva(true);
        sesion.setCreadaEn(ahoraUtc);
        sesion.setExpiraEn(expiraUtc);
        sesion.setActualizadaEn(ahoraUtc);
        sesionesActivas.saveAndFlush(sesion);
        return tokens.emitir(usuario, sesionId, plataforma);
    }

    private void revocarSesionActivaExistente(Long usuarioId, long versionToken, LocalDateTime ahoraUtc) {
        sesionesActivas.buscarPorUsuarioIdParaModificar(usuarioId).ifPresent(sesion -> {
            sesion.setActiva(false);
            sesion.setVersionToken(versionToken);
            sesion.setActualizadaEn(ahoraUtc);
            sesionesActivas.saveAndFlush(sesion);
        });
    }

    private Usuario bloquearUsuarioActivo(Long usuarioId) {
        var usuario = usuarios.buscarParaModificar(usuarioId).orElseThrow(CredencialesInvalidasException::new);
        entityManager.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        if (!usuario.isActivo() || !"ACTIVO".equals(usuario.getCliente().getEstatus())) {
            throw new CredencialesInvalidasException();
        }
        return usuario;
    }

    private UsuarioDesafioOtp bloquearDesafio(UUID desafioId) {
        var desafio = desafios.buscarPorIdParaModificar(desafioId)
            .orElseThrow(() -> new OtpException(401, "OTP_INVALIDO", "El desafío OTP no es válido"));
        entityManager.refresh(desafio, LockModeType.PESSIMISTIC_WRITE);
        return desafio;
    }

    private static String generarCodigoOtp() {
        int valor = RANDOM.nextInt(1_000_000);
        return String.format("%06d", valor);
    }

    private static String generarTokenConfirmacion() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static String enmascararCorreo(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 1) {
            return "***" + (arroba >= 0 ? correo.substring(arroba) : "");
        }
        return correo.charAt(0) + "***" + correo.charAt(arroba - 1) + correo.substring(arroba);
    }
}
