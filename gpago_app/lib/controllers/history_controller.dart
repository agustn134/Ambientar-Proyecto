import 'package:flutter/foundation.dart';
import '../models/auth_models.dart';
import '../models/history_models.dart';
import '../services/gpago_api_client.dart';

class HistoryController extends ChangeNotifier {
  HistoryController({required GpagoApiClient apiClient, this.pageSize = 10})
    : _api = apiClient;

  final GpagoApiClient _api;
  final int pageSize;

  bool _isLoading = false;
  bool _hasLoadedOnce = false;
  bool _isLoadingDetail = false;
  int _currentPage = 0;
  int _totalElementos = 0;
  int _totalPaginas = 0;
  List<ComprobanteHistorialDto> _items = const [];
  ComprobanteHistorialDto? _selectedDetail;
  GpagoApiException? _error;
  GpagoApiException? _detailError;

  bool get isLoading => _isLoading;
  bool get hasLoadedOnce => _hasLoadedOnce;
  bool get isLoadingDetail => _isLoadingDetail;
  int get currentPage => _currentPage;
  int get totalElementos => _totalElementos;
  int get totalPaginas => _totalPaginas;
  List<ComprobanteHistorialDto> get items => _items;
  ComprobanteHistorialDto? get selectedDetail => _selectedDetail;
  GpagoApiException? get error => _error;
  GpagoApiException? get detailError => _detailError;

  bool get hasPreviousPage => _currentPage > 0;
  bool get hasNextPage =>
      _totalPaginas > 0 && (_currentPage + 1) < _totalPaginas;

  Future<void> cargarPagina({
    required String accessToken,
    required int page,
    required Future<void> Function(GpagoApiException) onUnauthorized,
  }) async {
    if (_isLoading || page < 0) return;
    _isLoading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _api.consultarHistorialPagos(
        accessToken: accessToken,
        page: page,
        size: pageSize,
      );
      _items = result.contenido.where((c) => c.esCapturada).toList();
      _currentPage = result.page;
      _totalElementos = result.totalElementos;
      _totalPaginas = result.totalPaginas;
      _hasLoadedOnce = true;
    } on GpagoApiException catch (ex) {
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        limpiar();
        await onUnauthorized(ex);
        return;
      }
      _error = ex;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> cargarDetalle({
    required String accessToken,
    required String pagoId,
    required Future<void> Function(GpagoApiException) onUnauthorized,
  }) async {
    if (_isLoadingDetail) return;
    _isLoadingDetail = true;
    _detailError = null;
    _selectedDetail = null;
    notifyListeners();
    try {
      final detalle = await _api.consultarDetalleHistorialPago(
        accessToken: accessToken,
        pagoId: pagoId,
      );
      _selectedDetail = detalle;
    } on GpagoApiException catch (ex) {
      if (ex.esNoAutenticado || ex.statusCode == 403) {
        limpiar();
        await onUnauthorized(ex);
        return;
      }
      _detailError = ex;
    } finally {
      _isLoadingDetail = false;
      notifyListeners();
    }
  }

  void limpiar() {
    _isLoading = false;
    _hasLoadedOnce = false;
    _isLoadingDetail = false;
    _currentPage = 0;
    _totalElementos = 0;
    _totalPaginas = 0;
    _items = const [];
    _selectedDetail = null;
    _error = null;
    _detailError = null;
    notifyListeners();
  }
}
