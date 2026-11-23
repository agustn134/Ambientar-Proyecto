import 'package:flutter/material.dart';
import '../controllers/catalog_controller.dart';
import '../models/catalog_models.dart';
import '../theme/gpago_theme.dart';

/// Traducción nativa de la pantalla Stitch "Catálogo de servicios y productos" (`8a8b7714caa241eebb3202ffc4cbe3f8`).
class CatalogView extends StatefulWidget {
  const CatalogView({
    super.key,
    required this.controller,
    required this.onRefresh,
  });

  final CatalogController controller;
  final Future<void> Function() onRefresh;

  @override
  State<CatalogView> createState() => _CatalogViewState();
}

class _CatalogViewState extends State<CatalogView> {
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _clearFilters() {
    _searchController.clear();
    widget.controller.limpiarFiltros();
  }

  Future<void> _openDetail(
    BuildContext context,
    ProductoCatalogoDto producto,
  ) async {
    widget.controller.seleccionarProducto(producto);
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: GpagoTokens.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) => _ProductDetailSheet(producto: producto),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final ctrl = widget.controller;
        final categorias = ctrl.categorias;
        final filtrados = ctrl.productosFiltrados;

        return SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: GpagoTokens.maxContentWidth,
              ),
              child: RefreshIndicator(
                onRefresh: widget.onRefresh,
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      alignment: WrapAlignment.spaceBetween,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Catálogo de servicios',
                              style: GpagoTokens.titleStyle,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Consulta los productos disponibles desde el servidor GPago.',
                              style: GpagoTokens.helpStyle,
                            ),
                          ],
                        ),
                        OutlinedButton.icon(
                          key: const Key('catalog_refresh_button'),
                          onPressed: ctrl.isLoading ? null : widget.onRefresh,
                          icon: const Icon(Icons.refresh, size: 18),
                          label: const Text('Actualizar'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      key: const Key('catalog_search_field'),
                      controller: _searchController,
                      onChanged: ctrl.actualizarBusqueda,
                      decoration: InputDecoration(
                        labelText: 'Buscar servicio o producto',
                        hintText: 'Nombre del servicio, producto o ID',
                        prefixIcon: const Icon(Icons.search),
                        suffixIcon: ctrl.searchQuery.isNotEmpty
                            ? IconButton(
                                key: const Key('catalog_search_clear'),
                                tooltip: 'Limpiar búsqueda',
                                onPressed: _clearFilters,
                                icon: const Icon(Icons.close),
                              )
                            : null,
                      ),
                    ),
                    if (categorias.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      SingleChildScrollView(
                        scrollDirection: Axis.horizontal,
                        child: Row(
                          children: [
                            Padding(
                              padding: const EdgeInsets.only(right: 8),
                              child: ChoiceChip(
                                key: const Key('catalog_chip_all'),
                                label: Text('Todas (${ctrl.productos.length})'),
                                selected: ctrl.filterAllCategories,
                                onSelected: (_) =>
                                    ctrl.seleccionarTodasLasCategorias(),
                              ),
                            ),
                            ...categorias.map(
                              (cat) => Padding(
                                padding: const EdgeInsets.only(right: 8),
                                child: ChoiceChip(
                                  key: Key(
                                    'catalog_chip_${cat.idCatTipoServicio ?? 'none'}',
                                  ),
                                  label: Text(
                                    '${cat.etiqueta} (${cat.productos.length})',
                                  ),
                                  selected:
                                      !ctrl.filterAllCategories &&
                                      ctrl.selectedCategoryId ==
                                          cat.idCatTipoServicio,
                                  onSelected: (_) => ctrl.seleccionarCategoria(
                                    cat.idCatTipoServicio,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    if (ctrl.isLoading && !ctrl.hasLoadedOnce)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 48),
                        child: Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              CircularProgressIndicator(),
                              SizedBox(height: 16),
                              Text(
                                'Cargando catálogo de servicios...',
                                style: GpagoTokens.bodyStyle,
                              ),
                            ],
                          ),
                        ),
                      )
                    else if (ctrl.error != null)
                      GpagoStatusBanner(
                        tone: ctrl.error!.isNetworkError
                            ? GpagoBannerTone.network
                            : GpagoBannerTone.rejected,
                        title: ctrl.error!.isNetworkError
                            ? 'Sin conexión con GPago'
                            : 'Catálogo no disponible',
                        description: ctrl.error!.mensaje,
                        action: ElevatedButton.icon(
                          key: const Key('catalog_retry_button'),
                          onPressed: ctrl.isLoading ? null : widget.onRefresh,
                          icon: const Icon(Icons.refresh),
                          label: const Text('Reintentar'),
                        ),
                      )
                    else if (ctrl.productos.isEmpty)
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            children: [
                              const Icon(
                                Icons.inventory_2_outlined,
                                size: 48,
                                color: GpagoTokens.muted,
                              ),
                              const SizedBox(height: 12),
                              const Text(
                                'Catálogo sin productos disponibles',
                                style: GpagoTokens.subtitleStyle,
                                textAlign: TextAlign.center,
                              ),
                              const SizedBox(height: 8),
                              const Text(
                                'No hay servicios activos sincronizados en este momento.',
                                style: GpagoTokens.helpStyle,
                                textAlign: TextAlign.center,
                              ),
                              const SizedBox(height: 16),
                              ElevatedButton.icon(
                                key: const Key('catalog_empty_retry_button'),
                                onPressed: ctrl.isLoading
                                    ? null
                                    : widget.onRefresh,
                                icon: const Icon(Icons.refresh),
                                label: const Text('Consultar nuevamente'),
                              ),
                            ],
                          ),
                        ),
                      )
                    else if (filtrados.isEmpty)
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            children: [
                              const Icon(
                                Icons.search_off_outlined,
                                size: 44,
                                color: GpagoTokens.muted,
                              ),
                              const SizedBox(height: 12),
                              const Text(
                                'Sin resultados para tu filtro',
                                style: GpagoTokens.subtitleStyle,
                                textAlign: TextAlign.center,
                              ),
                              const SizedBox(height: 8),
                              const Text(
                                'Prueba otro término de búsqueda o selecciona todas las categorías.',
                                style: GpagoTokens.helpStyle,
                                textAlign: TextAlign.center,
                              ),
                              const SizedBox(height: 16),
                              OutlinedButton(
                                key: const Key('catalog_clear_filters_button'),
                                onPressed: _clearFilters,
                                child: const Text('Limpiar filtros'),
                              ),
                            ],
                          ),
                        ),
                      )
                    else
                      LayoutBuilder(
                        builder: (context, constraints) {
                          final width = constraints.maxWidth;
                          final columns = width >= 1024
                              ? 3
                              : (width >= 600 ? 2 : 1);
                          const spacing = 16.0;
                          final cardWidth =
                              (width - (spacing * (columns - 1))) / columns;

                          return Wrap(
                            spacing: spacing,
                            runSpacing: spacing,
                            children: filtrados
                                .map(
                                  (producto) => SizedBox(
                                    width: cardWidth,
                                    child: _ProductCard(
                                      producto: producto,
                                      onInspect: () =>
                                          _openDetail(context, producto),
                                    ),
                                  ),
                                )
                                .toList(),
                          );
                        },
                      ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _ProductCard extends StatelessWidget {
  const _ProductCard({required this.producto, required this.onInspect});

  final ProductoCatalogoDto producto;
  final VoidCallback onInspect;

  @override
  Widget build(BuildContext context) {
    final disponible = producto.estaDisponible;

    return Card(
      key: Key('product_card_${producto.idProducto}'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: GpagoTokens.canvas,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.receipt_long_outlined,
                    color: GpagoTokens.primary,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        producto.nombreProducto,
                        style: GpagoTokens.bodyStyle.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        producto.nombreServicio,
                        style: GpagoTokens.helpStyle,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: GpagoTokens.canvas,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    producto.etiquetaCategoria,
                    style: GpagoTokens.helpStyle.copyWith(fontSize: 12),
                  ),
                ),
                _AvailabilityBadge(disponible: disponible),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              _formatPrecio(producto.precio),
              style: GpagoTokens.subtitleStyle.copyWith(
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 14),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                key: Key('product_detail_button_${producto.idProducto}'),
                onPressed: onInspect,
                child: const Text('Ver detalle'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AvailabilityBadge extends StatelessWidget {
  const _AvailabilityBadge({required this.disponible});

  final bool disponible;

  @override
  Widget build(BuildContext context) {
    final ink = disponible ? GpagoTokens.successInk : GpagoTokens.pendingInk;
    final bg = disponible ? GpagoTokens.successBg : GpagoTokens.pendingBg;
    final icon = disponible ? Icons.check_circle_outline : Icons.schedule;
    final label = disponible ? 'Disponible' : 'No disponible';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 15, color: ink),
          const SizedBox(width: 4),
          Text(
            label,
            style: GpagoTokens.helpStyle.copyWith(
              color: ink,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _ProductDetailSheet extends StatelessWidget {
  const _ProductDetailSheet({required this.producto});

  final ProductoCatalogoDto producto;

  @override
  Widget build(BuildContext context) {
    final ayuda = producto.textoAyuda;

    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: GpagoTokens.canvas,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.receipt_long_outlined,
                    color: GpagoTokens.primary,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        producto.nombreProducto,
                        style: GpagoTokens.subtitleStyle,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        producto.nombreServicio,
                        style: GpagoTokens.helpStyle,
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Cerrar detalle',
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _AvailabilityBadge(disponible: producto.estaDisponible),
                Chip(label: Text(producto.etiquetaCategoria)),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              _formatPrecio(producto.precio),
              style: GpagoTokens.amountStyle,
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: GpagoTokens.canvas,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _MetadataRow(
                    label: 'ID de producto',
                    value: '${producto.idProducto}',
                  ),
                  const SizedBox(height: 8),
                  _MetadataRow(
                    label: 'ID de servicio',
                    value: producto.idServicio?.toString() ?? 'No especificado',
                  ),
                  const SizedBox(height: 8),
                  _MetadataRow(
                    label: 'Tipo de referencia',
                    value:
                        (producto.tipoReferencia != null &&
                            producto.tipoReferencia!.trim().isNotEmpty)
                        ? producto.tipoReferencia!.trim()
                        : 'No especificada',
                  ),
                  const SizedBox(height: 8),
                  _MetadataRow(
                    label: 'Dígito verificador',
                    value: producto.hasDigitoVerificador
                        ? 'Sí requiere'
                        : 'No requiere',
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: GpagoTokens.surface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFD7E0EC)),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(
                    Icons.info_outline,
                    size: 20,
                    color: GpagoTokens.primary,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      ayuda ??
                          'Sin indicaciones adicionales registradas para este producto.',
                      style: GpagoTokens.helpStyle,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              key: const Key('product_detail_action_button'),
              onPressed: producto.estaDisponible
                  ? () => Navigator.of(context).pop()
                  : null,
              child: Text(
                producto.estaDisponible
                    ? 'Listo para compra'
                    : 'Producto no disponible',
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MetadataRow extends StatelessWidget {
  const _MetadataRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: GpagoTokens.helpStyle),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: GpagoTokens.folioStyle.copyWith(fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}

String _formatPrecio(double? precio) {
  if (precio == null || precio <= 0) {
    return 'Importe variable';
  }
  final fixed = precio.toStringAsFixed(2);
  final parts = fixed.split('.');
  final intPart = parts[0];
  final buf = StringBuffer();
  for (var i = 0; i < intPart.length; i++) {
    final remaining = intPart.length - i;
    if (i > 0 && remaining % 3 == 0) {
      buf.write(',');
    }
    buf.write(intPart[i]);
  }
  return '\$${buf.toString()}.${parts[1]} MXN';
}
