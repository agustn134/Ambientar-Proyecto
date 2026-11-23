class ProductoCatalogoDto {
  const ProductoCatalogoDto({
    required this.id,
    required this.idProducto,
    required this.idServicio,
    required this.servicio,
    required this.producto,
    required this.idCatTipoServicio,
    required this.tipoFront,
    required this.hasDigitoVerificador,
    required this.precio,
    required this.showAyuda,
    required this.tipoReferencia,
    required this.legend,
    required this.fechaCreacion,
    required this.fechaActualizacion,
    required this.activo,
  });

  final int id;
  final int idProducto;
  final int? idServicio;
  final String servicio;
  final String producto;
  final int? idCatTipoServicio;
  final String? tipoFront;
  final bool hasDigitoVerificador;
  final double? precio;
  final bool showAyuda;
  final String? tipoReferencia;
  final String? legend;
  final DateTime? fechaCreacion;
  final DateTime? fechaActualizacion;
  final bool activo;

  bool get estaDisponible => activo && idProducto > 0;

  String get nombreProducto =>
      producto.trim().isNotEmpty ? producto.trim() : 'Producto #$idProducto';

  String get nombreServicio =>
      servicio.trim().isNotEmpty ? servicio.trim() : 'Servicio sin nombre';

  String get etiquetaCategoria => idCatTipoServicio != null
      ? 'Categoría #$idCatTipoServicio'
      : 'Sin categoría';

  String? get textoAyuda =>
      (legend != null && legend!.trim().isNotEmpty) ? legend!.trim() : null;

  factory ProductoCatalogoDto.fromJson(Map<String, dynamic> json) {
    return ProductoCatalogoDto(
      id: (json['id'] as num?)?.toInt() ?? 0,
      idProducto: (json['idProducto'] as num?)?.toInt() ?? 0,
      idServicio: (json['idServicio'] as num?)?.toInt(),
      servicio: json['servicio']?.toString() ?? '',
      producto: json['producto']?.toString() ?? '',
      idCatTipoServicio: (json['idCatTipoServicio'] as num?)?.toInt(),
      tipoFront: json['tipoFront']?.toString(),
      hasDigitoVerificador: json['hasDigitoVerificador'] == true,
      precio: (json['precio'] as num?)?.toDouble(),
      showAyuda: json['showAyuda'] == true,
      tipoReferencia: json['tipoReferencia']?.toString(),
      legend: json['legend']?.toString(),
      fechaCreacion: json['fechaCreacion'] != null
          ? DateTime.tryParse(json['fechaCreacion'].toString())
          : null,
      fechaActualizacion: json['fechaActualizacion'] != null
          ? DateTime.tryParse(json['fechaActualizacion'].toString())
          : null,
      activo: json['activo'] == true,
    );
  }
}

class CategoriaProductosGroup {
  const CategoriaProductosGroup({
    required this.idCatTipoServicio,
    required this.etiqueta,
    required this.productos,
  });

  final int? idCatTipoServicio;
  final String etiqueta;
  final List<ProductoCatalogoDto> productos;
}
