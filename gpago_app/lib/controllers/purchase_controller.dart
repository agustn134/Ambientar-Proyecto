import 'dart:math';
import 'package:flutter/foundation.dart';
import '../models/auth_models.dart';
import '../models/catalog_models.dart';
import '../models/purchase_models.dart';
import '../services/gpago_api_client.dart';

enum PurchaseFlowStage { capture, review, result }

enum PurchaseOutcomeKind {
  captured,
  rejected,
  pending,
  uncertainCommunication,
}

String generateUuidV4Key() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-'
      '${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-'
      '${hex.substring(16, 20)}-'
      '${hex.substring(20, 32)}';
}

class PurchaseController extends ChangeNotifier {
  PurchaseController({
    required GpagoApiClient apiClient,
    required this.producto,
    required List<CuentaPropiaDto> cuentas,
    required this.readAccessToken,
    required this.onUnauthorized,
    this.onBalanceUpdated,
    String Function()? idempotencyKeyGenerator,
    this.maxBoundedPolls = 10,
  }) : _api = apiClient,
       _generateKey = idempotencyKeyGenerator ?? generateUuidV4Key,
       _cuentas = cuentas.where((c) => c.cuentaId > 0).toList() {
    if (_cuentas.isNotEmpty) {
      _selectedCuenta = _cuentas.firstWhere(
        (c) => c.estatus.toUpperCase() == 'ACTIVA',
        orElse: () => _cuentas.first,
      );
    }
    _destinoModo = _inferInitialDestinoModo(producto);
  }

  final GpagoApiClient _api;
  final ProductoCatalogoDto producto;
  final List<CuentaPropiaDto> _cuentas;
  final String Function() readAccessToken;
  final Future<void> Function(GpagoApiException) onUnauthorized;
  final void Function(int cuentaId, double saldoContabilizado)? onBalanceUpdated;
  final String Function() _generateKey;
  final int maxBoundedPolls;

  PurchaseFlowStage _stage = PurchaseFlowStage.capture;
  PurchaseOutcomeKind? _outcomeKind;
  CuentaPropiaDto? _selectedCuenta;
  SaldoCuentaFinancieraDto? _saldoFinanciero;
  bool _loadingSaldo = false;
  late DestinoPagoModo _destinoModo;
  bool _isBusy = false;
  bool _isSubmittingPayment = false;
  ValidacionLocalResponseDto? _validacion;
  SolicitudPagoDto? _solicitudConfirmada;
  String? _idempotencyKey;
  OperacionPagoDto? _operacion;
  GpagoApiException? _error;
  String? _localValidationError;
  int _pollAttempts = 0;

  PurchaseFlowStage get stage => _stage;
  PurchaseOutcomeKind? get outcomeKind => _outcomeKind;
  List<CuentaPropiaDto> get cuentas => _cuentas;
  CuentaPropiaDto? get selectedCuenta => _selectedCuenta;
  SaldoCuentaFinancieraDto? get saldoFinanciero => _saldoFinanciero;
  bool get isLoadingSaldo => _loadingSaldo;
  DestinoPagoModo get destinoModo => _destinoModo;
  bool get isBusy => _isBusy;
  bool get isSubmittingPayment => _isSubmittingPayment;
  ValidacionLocalResponseDto? get validacion => _validacion;
  SolicitudPagoDto? get solicitudConfirmada => _solicitudConfirmada;
  String? get idempotencyKey => _idempotencyKey;
  OperacionPagoDto? get operacion => _operacion;
  GpagoApiException? get error => _error;
  String? get localValidationError => _localValidationError;
  int get pollAttempts => _pollAttempts;
  bool get canPollMore => _pollAttempts < maxBoundedPolls;

  bool get tieneCuentaSeleccionable =>
      _selectedCuenta != null && _selectedCuenta!.cuentaId > 0;

  bool get esMontoFijoCatalogo =>
      producto.precio != null && producto.precio! > 0;

  static DestinoPagoModo _inferInitialDestinoModo(ProductoCatalogoDto item) {
    final front = item.tipoFront?.trim();
    final refType = item.tipoReferencia?.trim() ?? '';
    if (front == '4') {
      return DestinoPagoModo.referencia;
    }
    if (front == '2') {
      return refType.isNotEmpty
          ? DestinoPagoModo.referencia
          : DestinoPagoModo.sinDestino;
    }
    return DestinoPagoModo.telefono;
  }

  Future<void> cargarSaldoFinanciero({int? cuentaId}) async {
    final targetId = cuentaId ?? _selectedCuenta?.cuentaId;
    if (targetId == null || targetId <= 0 || _loadingSaldo) return;
    final token = readAccessToken();
    if (token.isEmpty) return;

    _loadingSaldo = true;
    notifyListeners();
    try {
      final saldo = await _api.consultarSaldoFinanciero(
        accessToken: token,
        cuentaId: targetId,
      );
      if (_selectedCuenta?.cuentaId == targetId) {
        _saldoFinanciero = saldo;
        onBalanceUpdated?.call(saldo.cuentaId, saldo.contabilizado);
      }
    } on GpagoApiException catch (ex) {
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        await onUnauthorized(ex);
      }
      // Si el perfil del servidor no expone /cuentas/{id}/saldo (404), conserva el saldo de /cuentas/me.
    } finally {
      _loadingSaldo = false;
      notifyListeners();
    }
  }

  Future<void> seleccionarCuenta(CuentaPropiaDto cuenta) async {
    if (_isBusy || _isSubmittingPayment || _stage != PurchaseFlowStage.capture) {
      return;
    }
    _selectedCuenta = cuenta;
    _saldoFinanciero = null;
    _error = null;
    notifyListeners();
    await cargarSaldoFinanciero(cuentaId: cuenta.cuentaId);
  }

  void seleccionarDestinoModo(DestinoPagoModo modo) {
    if (_isBusy || _isSubmittingPayment || _stage != PurchaseFlowStage.capture) {
      return;
    }
    _destinoModo = modo;
    _localValidationError = null;
    _error = null;
    notifyListeners();
  }

  Future<void> prevalidarYRevisar({
    required String montoRaw,
    required String destinoRaw,
  }) async {
    if (_isBusy || _isSubmittingPayment) return;

    _localValidationError = null;
    _error = null;

    final cuenta = _selectedCuenta;
    if (cuenta == null || cuenta.cuentaId <= 0) {
      _localValidationError =
          'No hay una cuenta financiera propia con identificador válido.';
      notifyListeners();
      return;
    }
    if (cuenta.estatus.toUpperCase() != 'ACTIVA') {
      _localValidationError =
          'La cuenta seleccionada no se encuentra ACTIVA (${cuenta.estatus}).';
      notifyListeners();
      return;
    }
    final idServicio = producto.idServicio;
    if (!producto.estaDisponible || idServicio == null || idServicio <= 0) {
      _localValidationError =
          'Este producto no cuenta con servicio autorizado para compra.';
      notifyListeners();
      return;
    }

    final montoNormalizado = montoRaw.trim().replaceAll(',', '');
    final monto = esMontoFijoCatalogo
        ? producto.precio!
        : double.tryParse(montoNormalizado);
    if (monto == null || monto < 0.01) {
      _localValidationError = 'Ingresa un importe válido mayor o igual a \$0.01 MXN.';
      notifyListeners();
      return;
    }
    if (!esMontoFijoCatalogo &&
        !RegExp(r'^\d{1,12}(\.\d{1,2})?$').hasMatch(montoNormalizado)) {
      _localValidationError =
          'El importe admite hasta 12 enteros y 2 decimales.';
      notifyListeners();
      return;
    }

    String? telefono;
    String? referencia;
    final cleanDestino = destinoRaw.trim();
    switch (_destinoModo) {
      case DestinoPagoModo.telefono:
        if (!RegExp(r'^\d{10}$').hasMatch(cleanDestino)) {
          _localValidationError =
              'Ingresa un número telefónico de 10 dígitos numéricos.';
          notifyListeners();
          return;
        }
        telefono = cleanDestino;
      case DestinoPagoModo.referencia:
        if (cleanDestino.isEmpty ||
            cleanDestino.length > 100 ||
            !RegExp(r'^[A-Za-z0-9]+$').hasMatch(cleanDestino)) {
          _localValidationError =
              'Ingresa una referencia alfanumérica válida (sin espacios, máximo 100 caracteres).';
          notifyListeners();
          return;
        }
        referencia = cleanDestino;
      case DestinoPagoModo.sinDestino:
        break;
    }

    final token = readAccessToken();
    if (token.isEmpty) return;

    final pagoRequest = CrearPagoRequestDto(
      idServicio: idServicio,
      idProducto: producto.idProducto,
      montoPago: double.parse(monto.toStringAsFixed(2)),
      telefono: telefono,
      referencia: referencia,
    );

    _isBusy = true;
    notifyListeners();
    try {
      final validacion = await _api.validarPagoLocal(
        accessToken: token,
        request: pagoRequest,
      );
      if (!validacion.contratoLocalValido || validacion.pagoEnviado) {
        _localValidationError =
            'El servidor no confirmó la prevalidación local del contrato.';
        return;
      }

      _validacion = validacion;
      _solicitudConfirmada = SolicitudPagoDto(
        cuentaId: cuenta.cuentaId,
        pago: pagoRequest,
      );
      _idempotencyKey = _generateKey();
      _operacion = null;
      _outcomeKind = null;
      _pollAttempts = 0;
      _stage = PurchaseFlowStage.review;
    } on GpagoApiException catch (ex) {
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        await onUnauthorized(ex);
        return;
      }
      _error = ex;
    } finally {
      _isBusy = false;
      notifyListeners();
    }

    await cargarSaldoFinanciero(cuentaId: cuenta.cuentaId);
  }

  void volverACaptura({bool limpiarResultado = false}) {
    if (_isBusy || _isSubmittingPayment) return;
    _stage = PurchaseFlowStage.capture;
    _idempotencyKey = null;
    _solicitudConfirmada = null;
    _validacion = null;
    _error = null;
    _localValidationError = null;
    if (limpiarResultado) {
      _operacion = null;
      _outcomeKind = null;
      _pollAttempts = 0;
    }
    notifyListeners();
  }

  /// Ejecuta el envío único idempotente. Bloquea doble toque antes de la llamada HTTP.
  Future<void> confirmarYEnviarPago() async {
    if (_isBusy || _isSubmittingPayment) return;
    final solicitud = _solicitudConfirmada;
    final key = _idempotencyKey;
    final token = readAccessToken();
    if (solicitud == null || key == null || token.isEmpty) return;

    _isBusy = true;
    _isSubmittingPayment = true;
    _error = null;
    notifyListeners();

    try {
      final op = await _api.crearPagoIdempotente(
        accessToken: token,
        idempotencyKey: key,
        solicitud: solicitud,
      );
      _aplicarOperacionRecibida(op);
      _stage = PurchaseFlowStage.result;
    } on GpagoApiException catch (ex) {
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        await onUnauthorized(ex);
        return;
      }
      _error = ex;
      _stage = PurchaseFlowStage.result;
      if (_esIncertidumbreComunicacion(ex)) {
        // Conserva intactos _idempotencyKey, _solicitudConfirmada y _operacion.
        _outcomeKind = PurchaseOutcomeKind.uncertainCommunication;
      } else {
        _outcomeKind = PurchaseOutcomeKind.rejected;
      }
    } finally {
      _isBusy = false;
      _isSubmittingPayment = false;
      notifyListeners();
    }

    if (_outcomeKind == PurchaseOutcomeKind.captured ||
        _outcomeKind == PurchaseOutcomeKind.rejected) {
      await cargarSaldoFinanciero(cuentaId: solicitud.cuentaId);
    }
  }

  /// Consulta acotada del estado o recuperación de operación incierta sin cambiar Idempotency-Key.
  Future<void> consultarEstadoORecuperar() async {
    if (_isBusy || _isSubmittingPayment) return;
    final token = readAccessToken();
    if (token.isEmpty) return;

    final existingOp = _operacion;
    final solicitud = _solicitudConfirmada;
    final key = _idempotencyKey;

    if (existingOp != null && !canPollMore) {
      _error = const GpagoApiException(
        statusCode: 0,
        codigo: 'LIMITE_CONSULTAS_LOCAL',
        mensaje:
            'Se alcanzó el límite acotado de consultas en esta pantalla. Tu operación mantiene su UUID y protección en el servidor.',
      );
      notifyListeners();
      return;
    }

    if (existingOp == null && (solicitud == null || key == null)) {
      return;
    }

    _isBusy = true;
    _error = null;
    if (existingOp != null) {
      _pollAttempts += 1;
    }
    notifyListeners();

    try {
      final op = existingOp != null
          ? await _api.consultarPago(accessToken: token, pagoId: existingOp.id)
          : await _api.crearPagoIdempotente(
              accessToken: token,
              idempotencyKey: key!,
              solicitud: solicitud!,
            );
      _aplicarOperacionRecibida(op);
      _stage = PurchaseFlowStage.result;
    } on GpagoApiException catch (ex) {
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        await onUnauthorized(ex);
        return;
      }
      _error = ex;
      if (_esIncertidumbreComunicacion(ex)) {
        _outcomeKind = PurchaseOutcomeKind.uncertainCommunication;
      } else if (ex.statusCode == 409 && existingOp == null) {
        _outcomeKind = PurchaseOutcomeKind.rejected;
      }
    } finally {
      _isBusy = false;
      notifyListeners();
    }

    if (_operacion != null &&
        (_operacion!.estado.esCapturada || _operacion!.estado.esRechazada)) {
      await cargarSaldoFinanciero(cuentaId: _operacion!.cuentaId);
    }
  }

  Future<void> conciliarOperacionUnknown() async {
    final op = _operacion;
    if (_isBusy ||
        _isSubmittingPayment ||
        op == null ||
        !op.estado.esUnknown ||
        op.revisionManual) {
      return;
    }
    final token = readAccessToken();
    if (token.isEmpty) return;

    _isBusy = true;
    _error = null;
    notifyListeners();
    try {
      final actualizada = await _api.conciliarPago(
        accessToken: token,
        pagoId: op.id,
      );
      _aplicarOperacionRecibida(actualizada);
    } on GpagoApiException catch (ex) {
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        await onUnauthorized(ex);
        return;
      }
      _error = ex;
      if (_esIncertidumbreComunicacion(ex)) {
        _outcomeKind = PurchaseOutcomeKind.uncertainCommunication;
      }
    } finally {
      _isBusy = false;
      notifyListeners();
    }

    if (_operacion != null &&
        (_operacion!.estado.esCapturada || _operacion!.estado.esRechazada)) {
      await cargarSaldoFinanciero(cuentaId: _operacion!.cuentaId);
    }
  }

  void _aplicarOperacionRecibida(OperacionPagoDto op) {
    _operacion = op;
    _outcomeKind = switch (op.estado) {
      EstadoOperacionPago.captured => PurchaseOutcomeKind.captured,
      EstadoOperacionPago.failed => PurchaseOutcomeKind.rejected,
      EstadoOperacionPago.reserved ||
      EstadoOperacionPago.processing ||
      EstadoOperacionPago.unknown => PurchaseOutcomeKind.pending,
    };
  }

  static bool _esIncertidumbreComunicacion(GpagoApiException ex) {
    if (ex.isNetworkError) return true;
    if (ex.statusCode >= 500 && ex.codigo != 'REFERENCIA_NO_CONCLUYENTE') {
      return true;
    }
    return false;
  }
}
