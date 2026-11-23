import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/history_controller.dart';
import '../models/history_models.dart';
import '../theme/gpago_theme.dart';

/// Traducción nativa de la pantalla Stitch "Historial de compras exitosas" (`26b234943cf949f2858a19e0d61f3b89`).
class HistoryView extends StatelessWidget {
  const HistoryView({
    super.key,
    required this.controller,
    required this.onRefresh,
    required this.onLoadPage,
    required this.onOpenDetail,
  });

  final HistoryController controller;
  final Future<void> Function() onRefresh;
  final Future<void> Function(int page) onLoadPage;
  final Future<void> Function(ComprobanteHistorialDto item) onOpenDetail;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final ctrl = controller;
        final items = ctrl.items;

        return SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: GpagoTokens.maxContentWidth,
              ),
              child: RefreshIndicator(
                onRefresh: onRefresh,
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
                              'Historial de pagos aplicados',
                              style: GpagoTokens.titleStyle,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              ctrl.hasLoadedOnce
                                  ? 'Solo operaciones confirmadas (CAPTURED) · Total: ${ctrl.totalElementos}'
                                  : 'Solo operaciones confirmadas (CAPTURED)',
                              style: GpagoTokens.helpStyle,
                            ),
                          ],
                        ),
                        OutlinedButton.icon(
                          key: const Key('history_refresh_button'),
                          onPressed: ctrl.isLoading ? null : onRefresh,
                          icon: const Icon(Icons.refresh, size: 18),
                          label: const Text('Actualizar'),
                        ),
                      ],
                    ),
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
                                'Cargando compras exitosas...',
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
                            : 'No se pudo consultar el historial',
                        description: ctrl.error!.mensaje,
                        action: ElevatedButton.icon(
                          key: const Key('history_retry_button'),
                          onPressed: ctrl.isLoading ? null : onRefresh,
                          icon: const Icon(Icons.refresh),
                          label: const Text('Reintentar'),
                        ),
                      )
                    else if (items.isEmpty)
                      Card(
                        key: const Key('history_empty_state'),
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            children: [
                              const Icon(
                                Icons.receipt_long_outlined,
                                size: 48,
                                color: GpagoTokens.muted,
                              ),
                              const SizedBox(height: 12),
                              const Text(
                                'Sin compras capturadas registradas',
                                style: GpagoTokens.subtitleStyle,
                                textAlign: TextAlign.center,
                              ),
                              const SizedBox(height: 8),
                              const Text(
                                'Aquí aparecerán únicamente tus pagos confirmados en estado CAPTURED.',
                                style: GpagoTokens.helpStyle,
                                textAlign: TextAlign.center,
                              ),
                              const SizedBox(height: 16),
                              ElevatedButton.icon(
                                key: const Key('history_empty_refresh_button'),
                                onPressed: ctrl.isLoading ? null : onRefresh,
                                icon: const Icon(Icons.refresh),
                                label: const Text('Consultar nuevamente'),
                              ),
                            ],
                          ),
                        ),
                      )
                    else ...[
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
                            children: items
                                .map(
                                  (item) => SizedBox(
                                    width: cardWidth,
                                    child: _HistoryItemCard(
                                      item: item,
                                      onInspect: () => onOpenDetail(item),
                                    ),
                                  ),
                                )
                                .toList(),
                          );
                        },
                      ),
                      const SizedBox(height: 20),
                      _PaginationBar(
                        controller: ctrl,
                        onLoadPage: onLoadPage,
                      ),
                    ],
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

class _HistoryItemCard extends StatelessWidget {
  const _HistoryItemCard({
    required this.item,
    required this.onInspect,
  });

  final ComprobanteHistorialDto item;
  final VoidCallback onInspect;

  @override
  Widget build(BuildContext context) {
    final folioExterno = item.folioExternoDisponible;

    return Card(
      key: Key('history_card_${item.id}'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
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
                    color: GpagoTokens.successBg,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.check_circle_outline,
                        size: 15,
                        color: GpagoTokens.successInk,
                      ),
                      SizedBox(width: 4),
                      Text(
                        'Capturada',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: GpagoTokens.successInk,
                        ),
                      ),
                    ],
                  ),
                ),
                Text(
                  item.fechaVisible != null
                      ? GpagoTokens.formatServerTimestamp(item.fechaVisible!)
                      : 'Fecha no disponible',
                  style: GpagoTokens.helpStyle.copyWith(fontSize: 12),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              item.nombreProductoVisible,
              style: GpagoTokens.bodyStyle.copyWith(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 2),
            Text(item.nombreServicioVisible, style: GpagoTokens.helpStyle),
            const SizedBox(height: 10),
            Text(
              GpagoTokens.formatMxn(item.monto),
              style: GpagoTokens.subtitleStyle.copyWith(
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 10),
            _TraceRow(
              label: 'UUID interno',
              value: item.id,
            ),
            const SizedBox(height: 4),
            _TraceRow(
              label: 'UPC interno',
              value: item.referenciaInternaUpc,
            ),
            const SizedBox(height: 4),
            _TraceRow(
              label: 'Folio externo',
              value: folioExterno ?? 'Sin folio externo reportado',
            ),
            const SizedBox(height: 14),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                key: Key('history_detail_button_${item.id}'),
                onPressed: onInspect,
                child: const Text('Ver comprobante'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PaginationBar extends StatelessWidget {
  const _PaginationBar({
    required this.controller,
    required this.onLoadPage,
  });

  final HistoryController controller;
  final Future<void> Function(int page) onLoadPage;

  @override
  Widget build(BuildContext context) {
    final totalPagesSafe = controller.totalPaginas > 0
        ? controller.totalPaginas
        : 1;

    return Card(
      key: const Key('history_pagination_bar'),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Wrap(
          spacing: 12,
          runSpacing: 12,
          alignment: WrapAlignment.spaceBetween,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            OutlinedButton.icon(
              key: const Key('history_prev_page_button'),
              onPressed: (!controller.isLoading && controller.hasPreviousPage)
                  ? () => onLoadPage(controller.currentPage - 1)
                  : null,
              icon: const Icon(Icons.chevron_left),
              label: const Text('Anterior'),
            ),
            Text(
              'Página ${controller.currentPage + 1} de $totalPagesSafe',
              style: GpagoTokens.bodyStyle.copyWith(fontWeight: FontWeight.w600),
            ),
            OutlinedButton.icon(
              key: const Key('history_next_page_button'),
              onPressed: (!controller.isLoading && controller.hasNextPage)
                  ? () => onLoadPage(controller.currentPage + 1)
                  : null,
              icon: const Icon(Icons.chevron_right),
              label: const Text('Siguiente'),
            ),
          ],
        ),
      ),
    );
  }
}

class HistoryDetailSheet extends StatelessWidget {
  const HistoryDetailSheet({
    super.key,
    required this.controller,
    required this.initialItem,
    required this.onRetryDetail,
  });

  final HistoryController controller;
  final ComprobanteHistorialDto initialItem;
  final Future<void> Function() onRetryDetail;

  Future<void> _copiar(
    BuildContext context,
    String valor,
    String etiqueta,
  ) async {
    await Clipboard.setData(ClipboardData(text: valor));
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$etiqueta copiado al portapapeles')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final detail = controller.selectedDetail ?? initialItem;
        final folioExterno = detail.folioExternoDisponible;

        return SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.check_circle,
                      color: GpagoTokens.successInk,
                      size: 26,
                    ),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Text(
                        'Detalle del comprobante',
                        style: GpagoTokens.subtitleStyle,
                      ),
                    ),
                    IconButton(
                      tooltip: 'Cerrar detalle',
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.close),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  detail.nombreProductoVisible,
                  style: GpagoTokens.bodyStyle.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  '${detail.nombreServicioVisible} · Cuenta ID #${detail.cuentaId}',
                  style: GpagoTokens.helpStyle,
                ),
                const SizedBox(height: 12),
                Text(
                  GpagoTokens.formatMxn(detail.monto),
                  style: GpagoTokens.amountStyle,
                ),
                if (controller.isLoadingDetail) ...[
                  const SizedBox(height: 12),
                  const LinearProgressIndicator(),
                ] else if (controller.detailError != null) ...[
                  const SizedBox(height: 12),
                  GpagoStatusBanner(
                    tone: controller.detailError!.isNetworkError
                        ? GpagoBannerTone.network
                        : GpagoBannerTone.rejected,
                    title: 'No se pudo refrescar el detalle',
                    description: controller.detailError!.mensaje,
                    action: OutlinedButton.icon(
                      key: const Key('history_detail_retry_button'),
                      onPressed: onRetryDetail,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Reintentar'),
                    ),
                  ),
                ],
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: GpagoTokens.canvas,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFD7E0EC)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _TraceDetailBlock(
                        label: 'UUID de operación (servidor)',
                        value: detail.id,
                        copyButtonKey: Key('history_copy_uuid_${detail.id}'),
                        onCopy: () => _copiar(
                          context,
                          detail.id,
                          'UUID de operación',
                        ),
                      ),
                      const SizedBox(height: 12),
                      _TraceDetailBlock(
                        label: 'Referencia UPC interna',
                        value: detail.referenciaInternaUpc,
                        copyButtonKey: Key('history_copy_upc_${detail.id}'),
                        onCopy: () => _copiar(
                          context,
                          detail.referenciaInternaUpc,
                          'Referencia UPC',
                        ),
                      ),
                      const SizedBox(height: 12),
                      _TraceDetailBlock(
                        label: 'Folio proveedor externo (id_tx)',
                        value: folioExterno ?? 'Sin folio externo reportado',
                        copyButtonKey: folioExterno != null
                            ? Key('history_copy_tx_${detail.id}')
                            : null,
                        onCopy: folioExterno != null
                            ? () => _copiar(
                                context,
                                folioExterno,
                                'Folio externo',
                              )
                            : null,
                      ),
                      const SizedBox(height: 12),
                      _TraceRow(
                        label: 'Fecha de captura (servidor)',
                        value: detail.fechaVisible != null
                            ? GpagoTokens.formatServerTimestamp(
                                detail.fechaVisible!,
                              )
                            : 'No disponible',
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                ElevatedButton(
                  key: const Key('history_detail_close_button'),
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Cerrar'),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _TraceRow extends StatelessWidget {
  const _TraceRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Flexible(child: Text(label, style: GpagoTokens.helpStyle)),
        const SizedBox(width: 8),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: GpagoTokens.folioStyle.copyWith(fontSize: 12),
          ),
        ),
      ],
    );
  }
}

class _TraceDetailBlock extends StatelessWidget {
  const _TraceDetailBlock({
    required this.label,
    required this.value,
    this.copyButtonKey,
    this.onCopy,
  });

  final String label;
  final String value;
  final Key? copyButtonKey;
  final VoidCallback? onCopy;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Expanded(child: Text(label, style: GpagoTokens.helpStyle)),
            if (onCopy != null)
              TextButton.icon(
                key: copyButtonKey,
                onPressed: onCopy,
                icon: const Icon(Icons.copy, size: 16),
                label: const Text('Copiar'),
              ),
          ],
        ),
        SelectableText(
          value,
          style: GpagoTokens.folioStyle.copyWith(fontWeight: FontWeight.w600),
        ),
      ],
    );
  }
}
