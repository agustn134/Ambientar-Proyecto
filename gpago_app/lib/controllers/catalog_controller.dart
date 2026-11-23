import 'package:flutter/foundation.dart';
import '../models/auth_models.dart';
import '../models/catalog_models.dart';
import '../services/gpago_api_client.dart';

class CatalogController extends ChangeNotifier {
  CatalogController({required GpagoApiClient apiClient}) : _api = apiClient;

  final GpagoApiClient _api;

  bool _isLoading = false;
  bool _hasLoadedOnce = false;
  GpagoApiException? _error;
  List<ProductoCatalogoDto> _productos = const [];
  bool _filterAllCategories = true;
  int? _selectedCategoryId;
  String _searchQuery = '';
  ProductoCatalogoDto? _selectedProduct;

  bool get isLoading => _isLoading;
  bool get hasLoadedOnce => _hasLoadedOnce;
  GpagoApiException? get error => _error;
  List<ProductoCatalogoDto> get productos => _productos;
  bool get filterAllCategories => _filterAllCategories;
  int? get selectedCategoryId => _selectedCategoryId;
  String get searchQuery => _searchQuery;
  ProductoCatalogoDto? get selectedProduct => _selectedProduct;

  List<CategoriaProductosGroup> get categorias {
    final grouped = <int?, List<ProductoCatalogoDto>>{};
    for (final producto in _productos) {
      grouped.putIfAbsent(producto.idCatTipoServicio, () => []).add(producto);
    }
    final keys = grouped.keys.toList()
      ..sort((a, b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
      });

    return keys
        .map(
          (id) => CategoriaProductosGroup(
            idCatTipoServicio: id,
            etiqueta: id != null ? 'Categoría #$id' : 'Sin categoría',
            productos: List.unmodifiable(grouped[id]!),
          ),
        )
        .toList();
  }

  List<ProductoCatalogoDto> get productosFiltrados {
    final q = _searchQuery.trim().toLowerCase();
    return _productos.where((p) {
      if (!_filterAllCategories && p.idCatTipoServicio != _selectedCategoryId) {
        return false;
      }
      if (q.isEmpty) return true;
      return p.nombreProducto.toLowerCase().contains(q) ||
          p.nombreServicio.toLowerCase().contains(q) ||
          p.idProducto.toString().contains(q);
    }).toList();
  }

  Future<void> cargarProductos({
    required String accessToken,
    Future<void> Function(GpagoApiException)? onUnauthorized,
  }) async {
    if (_isLoading) return;
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      final items = await _api.consultarCatalogoProductos(
        accessToken: accessToken,
      );
      _productos = List.unmodifiable(items);
      _hasLoadedOnce = true;
      if (!_filterAllCategories &&
          !_productos.any((p) => p.idCatTipoServicio == _selectedCategoryId)) {
        _filterAllCategories = true;
        _selectedCategoryId = null;
      }
    } on GpagoApiException catch (ex) {
      _hasLoadedOnce = true;
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        if (onUnauthorized != null) {
          await onUnauthorized(ex);
        }
        return;
      }
      _error = ex;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  void seleccionarTodasLasCategorias() {
    if (_filterAllCategories) return;
    _filterAllCategories = true;
    _selectedCategoryId = null;
    notifyListeners();
  }

  void seleccionarCategoria(int? categoryId) {
    _filterAllCategories = false;
    _selectedCategoryId = categoryId;
    notifyListeners();
  }

  void actualizarBusqueda(String query) {
    if (_searchQuery == query) return;
    _searchQuery = query;
    notifyListeners();
  }

  void limpiarFiltros() {
    _filterAllCategories = true;
    _selectedCategoryId = null;
    _searchQuery = '';
    notifyListeners();
  }

  void seleccionarProducto(ProductoCatalogoDto? producto) {
    _selectedProduct = producto;
    notifyListeners();
  }

  void limpiar() {
    _isLoading = false;
    _hasLoadedOnce = false;
    _error = null;
    _productos = const [];
    _filterAllCategories = true;
    _selectedCategoryId = null;
    _searchQuery = '';
    _selectedProduct = null;
    notifyListeners();
  }
}
