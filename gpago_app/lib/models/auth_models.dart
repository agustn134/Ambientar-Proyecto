enum PlataformaCliente {
  android('ANDROID', 'Android'),
  web('WEB', 'Web');

  const PlataformaCliente(this.apiValue, this.etiqueta);

  final String apiValue;
  final String etiqueta;

  static PlataformaCliente fromApi(String? value) {
    return switch (value?.toUpperCase()) {
      'ANDROID' => PlataformaCliente.android,
      _ => PlataformaCliente.web,
    };
  }
}

class FieldValidationError {
  const FieldValidationError({required this.campo, required this.mensaje});

  final String campo;
  final String mensaje;

  factory FieldValidationError.fromJson(Map<String, dynamic> json) {
    return FieldValidationError(
      campo: json['campo']?.toString() ?? '',
      mensaje: json['mensaje']?.toString() ?? '',
    );
  }
}

class DesafioOtpDto {
  const DesafioOtpDto({
    required this.desafioId,
    required this.correoEnmascarado,
    required this.plataforma,
    required this.requiereOtp,
    required this.expiresIn,
    required this.expiresAt,
    required this.intentosRestantes,
    required this.reenviosRestantes,
  });

  final String desafioId;
  final String correoEnmascarado;
  final PlataformaCliente plataforma;
  final bool requiereOtp;
  final int expiresIn;
  final DateTime expiresAt;
  final int intentosRestantes;
  final int reenviosRestantes;

  factory DesafioOtpDto.fromJson(Map<String, dynamic> json) {
    return DesafioOtpDto(
      desafioId: json['desafioId']?.toString() ?? '',
      correoEnmascarado: json['correoEnmascarado']?.toString() ?? '',
      plataforma: PlataformaCliente.fromApi(json['plataforma']?.toString()),
      requiereOtp: json['requiereOtp'] == true,
      expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 0,
      expiresAt: DateTime.parse(json['expiresAt'].toString()),
      intentosRestantes: (json['intentosRestantes'] as num?)?.toInt() ?? 0,
      reenviosRestantes: (json['reenviosRestantes'] as num?)?.toInt() ?? 0,
    );
  }
}

class ConflictoSesionDto {
  const ConflictoSesionDto({
    required this.conflictoSesion,
    required this.desafioId,
    required this.tokenConfirmacion,
    required this.plataformaActiva,
    required this.plataformaNueva,
    required this.sesionActivaDesde,
    required this.expiraConfirmacionEn,
  });

  final bool conflictoSesion;
  final String desafioId;
  final String tokenConfirmacion;
  final PlataformaCliente plataformaActiva;
  final PlataformaCliente plataformaNueva;
  final DateTime sesionActivaDesde;
  final DateTime expiraConfirmacionEn;

  factory ConflictoSesionDto.fromJson(Map<String, dynamic> json) {
    return ConflictoSesionDto(
      conflictoSesion: json['conflictoSesion'] == true,
      desafioId: json['desafioId']?.toString() ?? '',
      tokenConfirmacion: json['tokenConfirmacion']?.toString() ?? '',
      plataformaActiva: PlataformaCliente.fromApi(
        json['plataformaActiva']?.toString(),
      ),
      plataformaNueva: PlataformaCliente.fromApi(
        json['plataformaNueva']?.toString(),
      ),
      sesionActivaDesde: DateTime.parse(json['sesionActivaDesde'].toString()),
      expiraConfirmacionEn: DateTime.parse(
        json['expiraConfirmacionEn'].toString(),
      ),
    );
  }
}

class LoginTokensDto {
  const LoginTokensDto({
    required this.accessToken,
    required this.tokenType,
    required this.expiresIn,
    required this.expiresAt,
    required this.sesionId,
    required this.plataforma,
  });

  final String accessToken;
  final String tokenType;
  final int expiresIn;
  final DateTime expiresAt;
  final String sesionId;
  final PlataformaCliente plataforma;

  bool estaVigente(DateTime ahora) => expiresAt.isAfter(ahora);

  factory LoginTokensDto.fromJson(Map<String, dynamic> json) {
    return LoginTokensDto(
      accessToken: json['accessToken']?.toString() ?? '',
      tokenType: json['tokenType']?.toString() ?? 'Bearer',
      expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 0,
      expiresAt: DateTime.parse(json['expiresAt'].toString()),
      sesionId: json['sesionId']?.toString() ?? '',
      plataforma: PlataformaCliente.fromApi(json['plataforma']?.toString()),
    );
  }

  Map<String, dynamic> toJson() => {
    'accessToken': accessToken,
    'tokenType': tokenType,
    'expiresIn': expiresIn,
    'expiresAt': expiresAt.toUtc().toIso8601String(),
    'sesionId': sesionId,
    'plataforma': plataforma.apiValue,
  };
}

class PerfilAuthDto {
  const PerfilAuthDto({
    required this.usuarioId,
    required this.clienteId,
    required this.correo,
    required this.activo,
    required this.rol,
  });

  final int usuarioId;
  final int clienteId;
  final String correo;
  final bool activo;
  final String rol;

  factory PerfilAuthDto.fromJson(Map<String, dynamic> json) {
    return PerfilAuthDto(
      usuarioId: (json['usuarioId'] as num).toInt(),
      clienteId: (json['clienteId'] as num).toInt(),
      correo: json['correo']?.toString() ?? '',
      activo: json['activo'] == true,
      rol: json['rol']?.toString() ?? 'CLIENTE',
    );
  }
}

class CuentaPropiaDto {
  const CuentaPropiaDto({
    required this.cuentaId,
    required this.numeroCuenta,
    required this.clienteId,
    required this.saldo,
    required this.estatus,
  });

  final int cuentaId;
  final String numeroCuenta;
  final int clienteId;
  final double saldo;
  final String estatus;

  factory CuentaPropiaDto.fromJson(Map<String, dynamic> json) {
    return CuentaPropiaDto(
      cuentaId: (json['cuentaId'] as num).toInt(),
      numeroCuenta: json['numeroCuenta']?.toString() ?? '',
      clienteId: (json['clienteId'] as num).toInt(),
      saldo: (json['saldo'] as num).toDouble(),
      estatus: json['estatus']?.toString() ?? '',
    );
  }
}

class ClienteResumenDto {
  const ClienteResumenDto({
    required this.clienteId,
    required this.nombreCompleto,
    required this.correo,
    required this.estatus,
  });

  final int clienteId;
  final String nombreCompleto;
  final String correo;
  final String estatus;

  factory ClienteResumenDto.fromJson(Map<String, dynamic> json) {
    final c = (json['cliente'] as Map<String, dynamic>?) ?? json;
    final partes = [
      c['nombre']?.toString(),
      c['segundoNombre']?.toString(),
      c['apellidoPaterno']?.toString(),
      c['apellidoMaterno']?.toString(),
    ].where((p) => p != null && p.trim().isNotEmpty).cast<String>().toList();
    return ClienteResumenDto(
      clienteId: (c['clienteId'] as num).toInt(),
      nombreCompleto: partes.join(' '),
      correo: c['correo']?.toString() ?? '',
      estatus: c['estatus']?.toString() ?? '',
    );
  }
}

class GpagoApiException implements Exception {
  const GpagoApiException({
    required this.statusCode,
    required this.codigo,
    required this.mensaje,
    this.errores = const [],
    this.conflictoSesion,
    this.isNetworkError = false,
  });

  final int statusCode;
  final String codigo;
  final String mensaje;
  final List<FieldValidationError> errores;
  final ConflictoSesionDto? conflictoSesion;
  final bool isNetworkError;

  bool get esNoAutenticado => statusCode == 401 && !isNetworkError;
  bool get esConflictoSesion =>
      statusCode == 409 && codigo == 'CONFLICTO_SESION_ACTIVA';
  bool get esOtpExpirado => codigo == 'OTP_EXPIRADO';
  bool get esOtpBloqueado => codigo == 'OTP_BLOQUEADO';

  @override
  String toString() => 'GpagoApiException($statusCode, $codigo)';
}
