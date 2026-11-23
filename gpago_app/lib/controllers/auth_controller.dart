import 'package:flutter/foundation.dart';
import '../models/auth_models.dart';
import '../services/gpago_api_client.dart';
import '../services/session_storage.dart';
import '../theme/gpago_theme.dart';

enum AuthStage {
  restoring,
  login,
  otpVerification,
  sessionConflict,
  authenticated,
}

class AuthFeedback {
  const AuthFeedback({
    required this.tone,
    required this.title,
    required this.description,
  });

  final GpagoBannerTone tone;
  final String title;
  final String description;
}

class AuthController extends ChangeNotifier {
  AuthController({
    required GpagoApiClient apiClient,
    required this.storage,
    DateTime Function()? clock,
  }) : _api = apiClient,
       _clock = clock ?? DateTime.now;

  final GpagoApiClient _api;
  final SessionStorage storage;
  final DateTime Function() _clock;

  AuthStage _stage = AuthStage.restoring;
  bool _busy = false;
  DesafioOtpDto? _desafio;
  ConflictoSesionDto? _conflicto;
  LoginTokensDto? _tokens;
  PerfilAuthDto? _perfil;
  ClienteResumenDto? _cliente;
  List<CuentaPropiaDto> _cuentas = const [];
  AuthFeedback? _feedback;

  AuthStage get stage => _stage;
  GpagoApiClient get apiClient => _api;
  bool get isBusy => _busy;
  DesafioOtpDto? get desafio => _desafio;
  ConflictoSesionDto? get conflicto => _conflicto;
  LoginTokensDto? get tokens => _tokens;
  PerfilAuthDto? get perfil => _perfil;
  ClienteResumenDto? get cliente => _cliente;
  List<CuentaPropiaDto> get cuentas => _cuentas;
  AuthFeedback? get feedback => _feedback;
  DateTime nowUtc() => _clock().toUtc();

  Future<void> restoreSession() async {
    _busy = true;
    _feedback = null;
    notifyListeners();
    try {
      final saved = await storage.read();
      if (saved == null || saved.accessToken.isEmpty) {
        _limpiarEstadoLocal();
        _stage = AuthStage.login;
        return;
      }
      if (!saved.estaVigente(_clock())) {
        await storage.clear();
        _limpiarEstadoLocal();
        _feedback = const AuthFeedback(
          tone: GpagoBannerTone.pending,
          title: 'Sesión expirada',
          description:
              'Tu sesión anterior venció por tiempo de seguridad. Inicia sesión nuevamente.',
        );
        _stage = AuthStage.login;
        return;
      }
      _tokens = saved;
      await _cargarPerfilYCuentas(saved.accessToken);
      _stage = AuthStage.authenticated;
    } on GpagoApiException catch (ex) {
      await _manejarErrorEnSesion(ex);
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> iniciarLogin({
    required String correo,
    required String password,
  }) async {
    if (_busy) return;
    _busy = true;
    _feedback = null;
    notifyListeners();
    try {
      final desafio = await _api.iniciarLogin(
        correo: correo,
        password: password,
      );
      _desafio = desafio;
      _conflicto = null;
      _stage = AuthStage.otpVerification;
    } on GpagoApiException catch (ex) {
      _feedback = _feedbackDesdeExcepcion(
        ex,
        fallbackTitle: 'No se pudo iniciar sesión',
      );
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> verificarOtp(String codigoOtp) async {
    final desafioActual = _desafio;
    if (_busy || desafioActual == null) return;
    _busy = true;
    _feedback = null;
    notifyListeners();
    try {
      final tokens = await _api.verificarOtp(
        desafioId: desafioActual.desafioId,
        codigoOtp: codigoOtp,
      );
      await _finalizarAutenticacion(tokens);
    } on GpagoApiException catch (ex) {
      if (ex.esConflictoSesion && ex.conflictoSesion != null) {
        _conflicto = ex.conflictoSesion;
        _stage = AuthStage.sessionConflict;
      } else {
        if (ex.esOtpBloqueado || ex.esOtpExpirado) {
          _desafio = DesafioOtpDto(
            desafioId: desafioActual.desafioId,
            correoEnmascarado: desafioActual.correoEnmascarado,
            plataforma: desafioActual.plataforma,
            requiereOtp: desafioActual.requiereOtp,
            expiresIn: desafioActual.expiresIn,
            expiresAt: ex.esOtpExpirado
                ? _clock().subtract(const Duration(seconds: 1))
                : desafioActual.expiresAt,
            intentosRestantes: ex.esOtpBloqueado
                ? 0
                : (desafioActual.intentosRestantes > 0
                      ? desafioActual.intentosRestantes - 1
                      : 0),
            reenviosRestantes: desafioActual.reenviosRestantes,
          );
        } else if (ex.codigo == 'OTP_INVALIDO' &&
            desafioActual.intentosRestantes > 0) {
          _desafio = DesafioOtpDto(
            desafioId: desafioActual.desafioId,
            correoEnmascarado: desafioActual.correoEnmascarado,
            plataforma: desafioActual.plataforma,
            requiereOtp: desafioActual.requiereOtp,
            expiresIn: desafioActual.expiresIn,
            expiresAt: desafioActual.expiresAt,
            intentosRestantes: desafioActual.intentosRestantes - 1,
            reenviosRestantes: desafioActual.reenviosRestantes,
          );
        }
        _feedback = _feedbackDesdeExcepcion(
          ex,
          fallbackTitle: 'No se pudo verificar el código',
        );
      }
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> reenviarOtp() async {
    final desafioActual = _desafio;
    if (_busy || desafioActual == null) return;
    _busy = true;
    _feedback = null;
    notifyListeners();
    try {
      final actualizado = await _api.reenviarOtp(
        desafioId: desafioActual.desafioId,
      );
      _desafio = actualizado;
      _feedback = const AuthFeedback(
        tone: GpagoBannerTone.success,
        title: 'Nuevo código enviado',
        description:
            'Enviamos un nuevo código de 6 dígitos a tu correo registrado. El código anterior quedó invalidado.',
      );
    } on GpagoApiException catch (ex) {
      _feedback = _feedbackDesdeExcepcion(
        ex,
        fallbackTitle: 'No fue posible reenviar el código',
      );
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> confirmarReemplazoSesion() async {
    final conflictoActual = _conflicto;
    if (_busy || conflictoActual == null) return;
    _busy = true;
    _feedback = null;
    notifyListeners();
    try {
      final tokens = await _api.reemplazarSesion(
        desafioId: conflictoActual.desafioId,
        tokenConfirmacion: conflictoActual.tokenConfirmacion,
        confirmarCierre: true,
      );
      await _finalizarAutenticacion(tokens);
    } on GpagoApiException catch (ex) {
      _feedback = _feedbackDesdeExcepcion(
        ex,
        fallbackTitle: 'No se pudo reemplazar la sesión anterior',
      );
      if (ex.codigo == 'CONFIRMACION_SESION_INVALIDA') {
        _conflicto = null;
        _desafio = null;
        _stage = AuthStage.login;
      }
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  void cancelarConflictoSesion() {
    _conflicto = null;
    _desafio = null;
    _feedback = const AuthFeedback(
      tone: GpagoBannerTone.pending,
      title: 'Inicio cancelado',
      description:
          'Se conservó la sesión activa anterior. Inicia sesión nuevamente cuando decidas reemplazarla.',
    );
    _stage = AuthStage.login;
    notifyListeners();
  }

  void volverALogin() {
    _conflicto = null;
    _desafio = null;
    _feedback = null;
    _stage = AuthStage.login;
    notifyListeners();
  }

  Future<void> refrescarSesionProtegida() async {
    final active = _tokens;
    if (_busy || active == null) return;
    _busy = true;
    _feedback = null;
    notifyListeners();
    try {
      await _cargarPerfilYCuentas(active.accessToken);
      _feedback = const AuthFeedback(
        tone: GpagoBannerTone.success,
        title: 'Sesión verificada',
        description:
            'Tu sesión única y el inventario de cuentas propias están vigentes.',
      );
    } on GpagoApiException catch (ex) {
      await _manejarErrorEnSesion(ex);
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> cerrarSesion() async {
    if (_busy) return;
    _busy = true;
    _feedback = null;
    notifyListeners();
    final token = _tokens?.accessToken;
    try {
      if (token != null && token.isNotEmpty) {
        await _api.logout(accessToken: token);
      }
    } on GpagoApiException {
      // Aunque falle la red al cerrar, se limpia el almacenamiento local por seguridad.
    } finally {
      await storage.clear();
      _limpiarEstadoLocal();
      _feedback = const AuthFeedback(
        tone: GpagoBannerTone.success,
        title: 'Sesión cerrada',
        description: 'Tu sesión se cerró de forma segura en este dispositivo.',
      );
      _stage = AuthStage.login;
      _busy = false;
      notifyListeners();
    }
  }

  Future<void> _finalizarAutenticacion(LoginTokensDto tokens) async {
    await storage.save(tokens);
    _tokens = tokens;
    _desafio = null;
    _conflicto = null;
    try {
      await _cargarPerfilYCuentas(tokens.accessToken);
      _stage = AuthStage.authenticated;
    } on GpagoApiException catch (ex) {
      await _manejarErrorEnSesion(ex);
    }
  }

  Future<void> _cargarPerfilYCuentas(String accessToken) async {
    final perfil = await _api.consultarPerfilAuth(accessToken: accessToken);
    final cliente = await _api.consultarClientePropio(accessToken: accessToken);
    final cuentas = await _api.consultarCuentasPropias(
      accessToken: accessToken,
    );
    _perfil = perfil;
    _cliente = cliente;
    _cuentas = cuentas;
  }

  Future<void> manejarSesionNoAutorizada(GpagoApiException ex) async {
    await _manejarErrorEnSesion(ex);
    notifyListeners();
  }

  void actualizarSaldoCuenta(int cuentaId, double nuevoSaldoContabilizado) {
    final updated = _cuentas
        .map(
          (c) => c.cuentaId == cuentaId
              ? CuentaPropiaDto(
                  cuentaId: c.cuentaId,
                  numeroCuenta: c.numeroCuenta,
                  clienteId: c.clienteId,
                  saldo: nuevoSaldoContabilizado,
                  estatus: c.estatus,
                )
              : c,
        )
        .toList();
    _cuentas = updated;
    notifyListeners();
  }

  Future<void> _manejarErrorEnSesion(GpagoApiException ex) async {
    if (ex.esNoAutenticado || ex.statusCode == 403) {
      await storage.clear();
      _limpiarEstadoLocal();
      _feedback = const AuthFeedback(
        tone: GpagoBannerTone.pending,
        title: 'Sesión revocada o expirada',
        description:
            'Tu sesión dejó de ser válida (pudo cerrarse desde otro dispositivo o expirar). Vuelve a iniciar sesión.',
      );
      _stage = AuthStage.login;
      return;
    }
    _feedback = _feedbackDesdeExcepcion(
      ex,
      fallbackTitle: 'No se pudo validar tu sesión',
    );
    if (_tokens == null) {
      _stage = AuthStage.login;
    }
  }

  void _limpiarEstadoLocal() {
    _tokens = null;
    _perfil = null;
    _cliente = null;
    _cuentas = const [];
    _desafio = null;
    _conflicto = null;
  }

  AuthFeedback _feedbackDesdeExcepcion(
    GpagoApiException ex, {
    required String fallbackTitle,
  }) {
    if (ex.isNetworkError) {
      return AuthFeedback(
        tone: GpagoBannerTone.network,
        title: 'Sin conexión con GPago',
        description: ex.mensaje,
      );
    }
    if (ex.esOtpExpirado) {
      return AuthFeedback(
        tone: GpagoBannerTone.pending,
        title: 'Código OTP expirado',
        description: ex.mensaje,
      );
    }
    if (ex.statusCode == 429) {
      return AuthFeedback(
        tone: GpagoBannerTone.pending,
        title: 'Límite de solicitudes alcanzado',
        description: ex.mensaje,
      );
    }
    final detail = ex.errores.isNotEmpty
        ? ex.errores.map((e) => e.mensaje).join(' ')
        : ex.mensaje;
    return AuthFeedback(
      tone: GpagoBannerTone.rejected,
      title: fallbackTitle,
      description: detail,
    );
  }
}
