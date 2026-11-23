import 'dart:async';
import 'dart:convert';
import 'dart:io';
import '../models/auth_models.dart';
import '../models/catalog_models.dart';
import '../models/history_models.dart';
import '../models/purchase_models.dart';

export '../models/auth_models.dart' show GpagoLogRedactor;

class RawHttpResponse {
  const RawHttpResponse({
    required this.statusCode,
    required this.body,
    this.headers = const {},
  });

  final int statusCode;
  final String body;
  final Map<String, String> headers;
}

class _ApiEnvelopeResult {
  const _ApiEnvelopeResult({
    required this.statusCode,
    required this.datos,
    required this.headers,
    this.timestamp,
    this.mensaje,
  });

  final int statusCode;
  final dynamic datos;
  final Map<String, String> headers;
  final DateTime? timestamp;
  final String? mensaje;
}

abstract interface class GpagoHttpTransport {
  Future<RawHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
  });
}

class IoGpagoHttpTransport implements GpagoHttpTransport {
  IoGpagoHttpTransport({this.timeout = const Duration(seconds: 15)});

  final Duration timeout;

  @override
  Future<RawHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
  }) async {
    final client = HttpClient()..connectionTimeout = timeout;
    try {
      final request = await client.openUrl(method, uri).timeout(timeout);
      headers.forEach(request.headers.set);
      if (body != null) {
        final bytes = utf8.encode(body);
        request.headers.contentLength = bytes.length;
        request.add(bytes);
      }
      final response = await request.close().timeout(timeout);
      final rawBody = await utf8.decoder.bind(response).join().timeout(timeout);
      final responseHeaders = <String, String>{};
      response.headers.forEach((name, values) {
        if (values.isNotEmpty) {
          responseHeaders[name.toLowerCase()] = values.join(', ');
        }
      });
      return RawHttpResponse(
        statusCode: response.statusCode,
        body: rawBody,
        headers: responseHeaders,
      );
    } on SocketException {
      throw const GpagoApiException(
        statusCode: 0,
        codigo: 'ERROR_COMUNICACION',
        mensaje:
            'No se pudo conectar con el servidor GPago. Verifica tu conexión e intenta de nuevo.',
        isNetworkError: true,
      );
    } on TimeoutException {
      throw const GpagoApiException(
        statusCode: 0,
        codigo: 'ERROR_COMUNICACION',
        mensaje:
            'La solicitud tardó demasiado tiempo. Verifica tu conexión antes de reintentar.',
        isNetworkError: true,
      );
    } on HandshakeException {
      throw const GpagoApiException(
        statusCode: 0,
        codigo: 'ERROR_CERTIFICADO',
        mensaje:
            'No se pudo validar la conexión segura HTTPS con el servidor.',
        isNetworkError: true,
      );
    } finally {
      client.close(force: true);
    }
  }
}

class GpagoApiClient {
  GpagoApiClient({
    required String baseUrl,
    GpagoHttpTransport? transport,
    this.plataforma = PlataformaCliente.android,
    bool allowLoopbackHttp = true,
  }) : baseUri = validateSecureBaseUri(
         baseUrl,
         allowLoopbackHttp: allowLoopbackHttp,
       ),
       _transport = transport ?? IoGpagoHttpTransport();

  static const Set<String> _loopbackHosts = {
    'localhost',
    '127.0.0.1',
    '10.0.2.2',
    '::1',
  };

  /// Exige HTTPS en cualquier destino remoto; solo autoriza HTTP en loopback local durante desarrollo.
  static Uri validateSecureBaseUri(
    String rawBaseUrl, {
    bool allowLoopbackHttp = true,
  }) {
    final normalized = rawBaseUrl.trim().replaceAll(RegExp(r'/+$'), '');
    final parsed = Uri.tryParse(normalized);
    if (parsed == null || parsed.host.isEmpty || parsed.userInfo.isNotEmpty) {
      throw ArgumentError.value(
        rawBaseUrl,
        'baseUrl',
        'La URL base debe incluir un host válido y sin credenciales incrustadas.',
      );
    }
    final scheme = parsed.scheme.toLowerCase();
    final isLoopback = _loopbackHosts.contains(parsed.host.toLowerCase());
    if (scheme == 'https') {
      return parsed;
    }
    if (scheme == 'http' && allowLoopbackHttp && isLoopback) {
      return parsed;
    }
    throw ArgumentError.value(
      rawBaseUrl,
      'baseUrl',
      'Por seguridad solo se permite transporte HTTPS fuera de loopback local.',
    );
  }

  final Uri baseUri;
  final GpagoHttpTransport _transport;
  final PlataformaCliente plataforma;

  Future<DesafioOtpDto> iniciarLogin({
    required String correo,
    required String password,
  }) async {
    final datos = await _requestJson(
      method: 'POST',
      path: '/auth/login',
      body: {
        'correo': correo.trim(),
        'password': password,
        'plataforma': plataforma.apiValue,
      },
    );
    return DesafioOtpDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<LoginTokensDto> verificarOtp({
    required String desafioId,
    required String codigoOtp,
  }) async {
    final datos = await _requestJson(
      method: 'POST',
      path: '/auth/otp/verificar',
      body: {
        'desafioId': desafioId,
        'codigoOtp': codigoOtp.trim(),
        'plataforma': plataforma.apiValue,
      },
    );
    return LoginTokensDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<DesafioOtpDto> reenviarOtp({required String desafioId}) async {
    final datos = await _requestJson(
      method: 'POST',
      path: '/auth/otp/reenviar',
      body: {'desafioId': desafioId},
    );
    return DesafioOtpDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<LoginTokensDto> reemplazarSesion({
    required String desafioId,
    required String tokenConfirmacion,
    required bool confirmarCierre,
  }) async {
    final datos = await _requestJson(
      method: 'POST',
      path: '/auth/sesion/reemplazar',
      body: {
        'desafioId': desafioId,
        'tokenConfirmacion': tokenConfirmacion,
        'confirmarCierre': confirmarCierre,
      },
    );
    return LoginTokensDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<void> logout({required String accessToken}) async {
    await _requestJson(
      method: 'POST',
      path: '/auth/logout',
      accessToken: accessToken,
    );
  }

  Future<PerfilAuthDto> consultarPerfilAuth({
    required String accessToken,
  }) async {
    final datos = await _requestJson(
      method: 'GET',
      path: '/auth/me',
      accessToken: accessToken,
    );
    return PerfilAuthDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<ClienteResumenDto> consultarClientePropio({
    required String accessToken,
  }) async {
    final datos = await _requestJson(
      method: 'GET',
      path: '/clientes/me',
      accessToken: accessToken,
    );
    return ClienteResumenDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<List<CuentaPropiaDto>> consultarCuentasPropias({
    required String accessToken,
  }) async {
    final datos = await _requestJson(
      method: 'GET',
      path: '/cuentas/me',
      accessToken: accessToken,
    );
    final list = datos as List<dynamic>;
    return list
        .map((item) => CuentaPropiaDto.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// Consume únicamente el catálogo local sincronizado en servidor; nunca invoca rutas de cuota externa.
  Future<List<ProductoCatalogoDto>> consultarCatalogoProductos({
    required String accessToken,
  }) async {
    final datos = await _requestJson(
      method: 'GET',
      path: '/api/gestopago/productos',
      accessToken: accessToken,
    );
    final list = (datos as List<dynamic>?) ?? const [];
    return list
        .whereType<Map<String, dynamic>>()
        .map(ProductoCatalogoDto.fromJson)
        .toList();
  }

  /// Prevalidación local de contrato en servidor sin cobro ni envío al proveedor.
  Future<ValidacionLocalResponseDto> validarPagoLocal({
    required String accessToken,
    required CrearPagoRequestDto request,
  }) async {
    final datos = await _requestJson(
      method: 'POST',
      path: '/api/gestopago/pagos/validar',
      accessToken: accessToken,
      body: request.toJson(),
    );
    return ValidacionLocalResponseDto.fromJson(datos as Map<String, dynamic>);
  }

  /// Crea o recupera operación financiera real con `Idempotency-Key` estable.
  Future<OperacionPagoDto> crearPagoIdempotente({
    required String accessToken,
    required String idempotencyKey,
    required SolicitudPagoDto solicitud,
  }) async {
    final envelope = await _requestEnvelope(
      method: 'POST',
      path: '/api/gestopago/pagos',
      accessToken: accessToken,
      extraHeaders: {'Idempotency-Key': idempotencyKey},
      body: solicitud.toJson(),
    );
    return OperacionPagoDto.fromJson(
      envelope.datos as Map<String, dynamic>,
      serverTimestamp: envelope.timestamp,
      serverMessage: envelope.mensaje,
    );
  }

  Future<OperacionPagoDto> consultarPago({
    required String accessToken,
    required String pagoId,
  }) async {
    final envelope = await _requestEnvelope(
      method: 'GET',
      path: '/api/gestopago/pagos/$pagoId',
      accessToken: accessToken,
    );
    return OperacionPagoDto.fromJson(
      envelope.datos as Map<String, dynamic>,
      serverTimestamp: envelope.timestamp,
      serverMessage: envelope.mensaje,
    );
  }

  Future<OperacionPagoDto> conciliarPago({
    required String accessToken,
    required String pagoId,
  }) async {
    final envelope = await _requestEnvelope(
      method: 'POST',
      path: '/api/gestopago/pagos/$pagoId/conciliar',
      accessToken: accessToken,
    );
    return OperacionPagoDto.fromJson(
      envelope.datos as Map<String, dynamic>,
      serverTimestamp: envelope.timestamp,
      serverMessage: envelope.mensaje,
    );
  }

  Future<SaldoCuentaFinancieraDto> consultarSaldoFinanciero({
    required String accessToken,
    required int cuentaId,
  }) async {
    final datos = await _requestJson(
      method: 'GET',
      path: '/api/gestopago/cuentas/$cuentaId/saldo',
      accessToken: accessToken,
    );
    return SaldoCuentaFinancieraDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<PaginaHistorialDto> consultarHistorialPagos({
    required String accessToken,
    int page = 0,
    int size = 10,
  }) async {
    final datos = await _requestJson(
      method: 'GET',
      path: '/api/gestopago/pagos/historial?page=$page&size=$size',
      accessToken: accessToken,
    );
    return PaginaHistorialDto.fromJson(datos as Map<String, dynamic>);
  }

  Future<ComprobanteHistorialDto> consultarDetalleHistorialPago({
    required String accessToken,
    required String pagoId,
  }) async {
    final datos = await _requestJson(
      method: 'GET',
      path: '/api/gestopago/pagos/historial/$pagoId',
      accessToken: accessToken,
    );
    final dto = ComprobanteHistorialDto.fromJson(datos as Map<String, dynamic>);
    if (!dto.esCapturada) {
      throw const GpagoApiException(
        statusCode: 404,
        codigo: 'RECURSO_NO_ENCONTRADO',
        mensaje: 'El comprobante solicitado no corresponde a una operación capturada.',
      );
    }
    return dto;
  }

  Future<dynamic> _requestJson({
    required String method,
    required String path,
    Map<String, dynamic>? body,
    String? accessToken,
    Map<String, String> extraHeaders = const {},
  }) async {
    final result = await _requestEnvelope(
      method: method,
      path: path,
      body: body,
      accessToken: accessToken,
      extraHeaders: extraHeaders,
    );
    return result.datos;
  }

  Future<_ApiEnvelopeResult> _requestEnvelope({
    required String method,
    required String path,
    Map<String, dynamic>? body,
    String? accessToken,
    Map<String, String> extraHeaders = const {},
  }) async {
    final uri = Uri.parse('$baseUri$path');
    final headers = <String, String>{
      'Accept': 'application/json',
      'X-Client-Platform': plataforma.apiValue,
      'X-Requested-With': 'GPago-Client',
      'Cache-Control': 'no-store',
      ...extraHeaders,
    };
    if (body != null) {
      headers['Content-Type'] = 'application/json; charset=utf-8';
    }
    if (accessToken != null && accessToken.isNotEmpty) {
      if (RegExp(r'[\r\n]').hasMatch(accessToken)) {
        throw const GpagoApiException(
          statusCode: 401,
          codigo: 'NO_AUTENTICADO',
          mensaje: 'Token de sesión con formato inválido.',
        );
      }
      headers['Authorization'] = 'Bearer $accessToken';
    }

    final response = await _transport.send(
      method: method,
      uri: uri,
      headers: headers,
      body: body != null ? jsonEncode(body) : null,
    );

    if (response.statusCode == 204) {
      return _ApiEnvelopeResult(
        statusCode: 204,
        datos: null,
        headers: response.headers,
      );
    }

    final Map<String, dynamic> parsed;
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const FormatException('Estructura JSON inesperada');
      }
      parsed = decoded;
    } on FormatException {
      throw GpagoApiException(
        statusCode: response.statusCode,
        codigo: 'RESPUESTA_INVALIDA',
        mensaje:
            'El servidor devolvió una respuesta ilegible (HTTP ${response.statusCode}).',
        isNetworkError: response.statusCode == 0 || response.statusCode >= 500,
      );
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      final rawTimestamp = parsed['timestamp']?.toString();
      final parsedTimestamp = rawTimestamp != null
          ? DateTime.tryParse(rawTimestamp)
          : null;
      return _ApiEnvelopeResult(
        statusCode: response.statusCode,
        datos: parsed.containsKey('datos') ? parsed['datos'] : parsed,
        headers: response.headers,
        timestamp: parsedTimestamp,
        mensaje: parsed['mensaje']?.toString(),
      );
    }

    final codigo = parsed['codigo']?.toString() ?? 'ERROR_HTTP';
    final mensaje =
        parsed['mensaje']?.toString() ??
        'No fue posible completar la solicitud (HTTP ${response.statusCode}).';
    final rawErrores = parsed['errores'];
    final errores = rawErrores is List
        ? rawErrores
            .whereType<Map<String, dynamic>>()
            .map(FieldValidationError.fromJson)
            .toList()
        : const <FieldValidationError>[];

    ConflictoSesionDto? conflicto;
    if (response.statusCode == 409 &&
        codigo == 'CONFLICTO_SESION_ACTIVA' &&
        parsed['datos'] is Map<String, dynamic>) {
      conflicto = ConflictoSesionDto.fromJson(
        parsed['datos'] as Map<String, dynamic>,
      );
    }

    throw GpagoApiException(
      statusCode: response.statusCode,
      codigo: codigo,
      mensaje: mensaje,
      errores: errores,
      conflictoSesion: conflicto,
    );
  }
}
