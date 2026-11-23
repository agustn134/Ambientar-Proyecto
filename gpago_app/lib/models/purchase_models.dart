enum DestinoPagoModo {
  telefono('TELEFONO', 'Número de teléfono (10 dígitos)'),
  referencia('REFERENCIA', 'Referencia de servicio'),
  sinDestino('FIJO', 'Sin teléfono ni referencia adicional');

  const DestinoPagoModo(this.apiHint, this.etiqueta);

  final String apiHint;
  final String etiqueta;
}

class CrearPagoRequestDto {
  const CrearPagoRequestDto({
    required this.idServicio,
    required this.idProducto,
    required this.montoPago,
    this.telefono,
    this.referencia,
  });

  final int idServicio;
  final int idProducto;
  final double montoPago;
  final String? telefono;
  final String? referencia;

  String get destinoResumen {
    final tel = telefono?.trim();
    if (tel != null && tel.isNotEmpty) return tel;
    final ref = referencia?.trim();
    if (ref != null && ref.isNotEmpty) return ref;
    return 'Asignado por contrato en servidor';
  }

  Map<String, dynamic> toJson() {
    final cleanPhone = telefono?.trim();
    final cleanRef = referencia?.trim();
    return {
      'idServicio': idServicio,
      'idProducto': idProducto,
      'montoPago': double.parse(montoPago.toStringAsFixed(2)),
      if (cleanPhone != null && cleanPhone.isNotEmpty) 'telefono': cleanPhone,
      if (cleanRef != null && cleanRef.isNotEmpty) 'referencia': cleanRef,
    };
  }
}

class SolicitudPagoDto {
  const SolicitudPagoDto({
    required this.cuentaId,
    required this.pago,
  });

  final int cuentaId;
  final CrearPagoRequestDto pago;

  Map<String, dynamic> toJson() => {
    'cuentaId': cuentaId,
    'pago': pago.toJson(),
  };
}

class ValidacionLocalResponseDto {
  const ValidacionLocalResponseDto({
    required this.contratoLocalValido,
    required this.idServicio,
    required this.idProducto,
    required this.montoPago,
    required this.requiereVerificacionReferencia,
    required this.pagoEnviado,
  });

  final bool contratoLocalValido;
  final int idServicio;
  final int idProducto;
  final double montoPago;
  final bool requiereVerificacionReferencia;
  final bool pagoEnviado;

  factory ValidacionLocalResponseDto.fromJson(Map<String, dynamic> json) {
    return ValidacionLocalResponseDto(
      contratoLocalValido: json['contratoLocalValido'] == true,
      idServicio: (json['idServicio'] as num).toInt(),
      idProducto: (json['idProducto'] as num).toInt(),
      montoPago: (json['montoPago'] as num).toDouble(),
      requiereVerificacionReferencia:
          json['requiereVerificacionReferencia'] == true,
      pagoEnviado: json['pagoEnviado'] == true,
    );
  }
}

enum EstadoOperacionPago {
  reserved('RESERVED', 'Fondos reservados'),
  processing('PROCESSING', 'En proceso'),
  unknown('UNKNOWN', 'Resultado en verificación'),
  captured('CAPTURED', 'Capturada'),
  failed('FAILED', 'Rechazada');

  const EstadoOperacionPago(this.apiValue, this.etiqueta);

  final String apiValue;
  final String etiqueta;

  bool get esCapturada => this == EstadoOperacionPago.captured;
  bool get esRechazada => this == EstadoOperacionPago.failed;
  bool get esPendiente =>
      this == EstadoOperacionPago.reserved ||
      this == EstadoOperacionPago.processing ||
      this == EstadoOperacionPago.unknown;
  bool get esUnknown => this == EstadoOperacionPago.unknown;

  static EstadoOperacionPago fromApi(String? raw) {
    return switch (raw?.toUpperCase()) {
      'RESERVED' => EstadoOperacionPago.reserved,
      'PROCESSING' => EstadoOperacionPago.processing,
      'CAPTURED' => EstadoOperacionPago.captured,
      'FAILED' => EstadoOperacionPago.failed,
      _ => EstadoOperacionPago.unknown,
    };
  }
}

/// Representa el DTO real `PagoDtos.Pago` devuelto por `POST/GET/conciliar` de pagos,
/// más la marca de tiempo del sobre HTTP del servidor cuando está presente.
class OperacionPagoDto {
  const OperacionPagoDto({
    required this.id,
    required this.cuentaId,
    required this.monto,
    required this.estado,
    required this.revisionManual,
    this.serverTimestamp,
    this.serverMessage,
  });

  final String id;
  final int cuentaId;
  final double monto;
  final EstadoOperacionPago estado;
  final bool revisionManual;
  final DateTime? serverTimestamp;
  final String? serverMessage;

  factory OperacionPagoDto.fromJson(
    Map<String, dynamic> json, {
    DateTime? serverTimestamp,
    String? serverMessage,
  }) {
    return OperacionPagoDto(
      id: json['id']?.toString() ?? '',
      cuentaId: (json['cuentaId'] as num).toInt(),
      monto: (json['monto'] as num).toDouble(),
      estado: EstadoOperacionPago.fromApi(json['estado']?.toString()),
      revisionManual: json['revisionManual'] == true,
      serverTimestamp: serverTimestamp,
      serverMessage: serverMessage,
    );
  }
}

class SaldoCuentaFinancieraDto {
  const SaldoCuentaFinancieraDto({
    required this.cuentaId,
    required this.contabilizado,
    required this.reservado,
    required this.disponible,
  });

  final int cuentaId;
  final double contabilizado;
  final double reservado;
  final double disponible;

  factory SaldoCuentaFinancieraDto.fromJson(Map<String, dynamic> json) {
    return SaldoCuentaFinancieraDto(
      cuentaId: (json['cuentaId'] as num).toInt(),
      contabilizado: (json['contabilizado'] as num).toDouble(),
      reservado: (json['reservado'] as num).toDouble(),
      disponible: (json['disponible'] as num).toDouble(),
    );
  }
}
