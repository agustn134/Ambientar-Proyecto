class ComprobanteHistorialDto {
  const ComprobanteHistorialDto({
    required this.id,
    required this.cuentaId,
    required this.idServicio,
    required this.servicio,
    required this.idProducto,
    required this.producto,
    required this.monto,
    required this.estado,
    required this.referenciaInternaUpc,
    required this.folioProveedorIdTx,
    required this.creadoEn,
    required this.capturadoEn,
  });

  final String id;
  final int cuentaId;
  final int idServicio;
  final String? servicio;
  final int idProducto;
  final String? producto;
  final double monto;
  final String estado;
  final String referenciaInternaUpc;
  final String? folioProveedorIdTx;
  final DateTime? creadoEn;
  final DateTime? capturadoEn;

  bool get esCapturada => estado.toUpperCase() == 'CAPTURED';

  String get nombreProductoVisible =>
      (producto != null && producto!.trim().isNotEmpty)
      ? producto!.trim()
      : 'Producto #$idProducto';

  String get nombreServicioVisible =>
      (servicio != null && servicio!.trim().isNotEmpty)
      ? servicio!.trim()
      : 'Servicio #$idServicio';

  /// Folio externo reportado por GestoPago (`id_tx`); nunca se fabrica si viene nulo.
  String? get folioExternoDisponible =>
      (folioProveedorIdTx != null && folioProveedorIdTx!.trim().isNotEmpty)
      ? folioProveedorIdTx!.trim()
      : null;

  DateTime? get fechaVisible => capturadoEn ?? creadoEn;

  factory ComprobanteHistorialDto.fromJson(Map<String, dynamic> json) {
    return ComprobanteHistorialDto(
      id: json['id']?.toString() ?? '',
      cuentaId: (json['cuentaId'] as num?)?.toInt() ?? 0,
      idServicio: (json['idServicio'] as num?)?.toInt() ?? 0,
      servicio: json['servicio']?.toString(),
      idProducto: (json['idProducto'] as num?)?.toInt() ?? 0,
      producto: json['producto']?.toString(),
      monto: (json['monto'] as num?)?.toDouble() ?? 0.0,
      estado: json['estado']?.toString() ?? '',
      referenciaInternaUpc: json['referenciaInternaUpc']?.toString() ?? '',
      folioProveedorIdTx: json['folioProveedorIdTx']?.toString(),
      creadoEn: json['creadoEn'] != null
          ? DateTime.tryParse(json['creadoEn'].toString())
          : null,
      capturadoEn: json['capturadoEn'] != null
          ? DateTime.tryParse(json['capturadoEn'].toString())
          : null,
    );
  }
}

class PaginaHistorialDto {
  const PaginaHistorialDto({
    required this.contenido,
    required this.page,
    required this.size,
    required this.totalElementos,
    required this.totalPaginas,
  });

  final List<ComprobanteHistorialDto> contenido;
  final int page;
  final int size;
  final int totalElementos;
  final int totalPaginas;

  factory PaginaHistorialDto.fromJson(Map<String, dynamic> json) {
    final rawList = (json['contenido'] as List<dynamic>?) ?? const [];
    final capturadas = rawList
        .whereType<Map<String, dynamic>>()
        .map(ComprobanteHistorialDto.fromJson)
        .where((item) => item.esCapturada)
        .toList();
    return PaginaHistorialDto(
      contenido: capturadas,
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? 10,
      totalElementos: (json['totalElementos'] as num?)?.toInt() ?? 0,
      totalPaginas: (json['totalPaginas'] as num?)?.toInt() ?? 0,
    );
  }
}
