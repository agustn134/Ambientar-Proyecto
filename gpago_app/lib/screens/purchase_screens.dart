import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/purchase_controller.dart';
import '../models/auth_models.dart';
import '../models/catalog_models.dart';
import '../models/purchase_models.dart';
import '../services/gpago_api_client.dart';
import '../theme/gpago_theme.dart';

/// Traducción nativa de la pantalla Stitch "Confirmar y pagar servicio" (`fca4610b44e34626b29f2838419a36d9`)
/// implementando prevalidación local, revisión de importe, confirmación expresa e idempotente,
/// y separación estricta de los cuatro estados operativos (Capturada, Rechazada, Pendiente y Comunicación incierta).
class PurchaseFlowScreen extends StatefulWidget {
  const PurchaseFlowScreen({
    super.key,
    required this.apiClient,
    required this.producto,
    required this.cuentas,
    required this.readAccessToken,
    required this.onUnauthorized,
    this.onBalanceUpdated,
    this.controller,
  });

  final GpagoApiClient apiClient;
  final ProductoCatalogoDto producto;
  final List<CuentaPropiaDto> cuentas;
  final String Function() readAccessToken;
  final Future<void> Function(GpagoApiException) onUnauthorized;
  final void Function(int cuentaId, double saldoContabilizado)? onBalanceUpdated;
  final PurchaseController? controller;

  @override
  State<PurchaseFlowScreen> createState() => _PurchaseFlowScreenState();
}

class _PurchaseFlowScreenState extends State<PurchaseFlowScreen> {
  late final PurchaseController _controller;
  late final bool _ownsController;
  late final TextEditingController _montoController;
  final TextEditingController _destinoController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _ownsController = widget.controller == null;
    _controller =
        widget.controller ??
        PurchaseController(
          apiClient: widget.apiClient,
          producto: widget.producto,
          cuentas: widget.cuentas,
          readAccessToken: widget.readAccessToken,
          onUnauthorized: widget.onUnauthorized,
          onBalanceUpdated: widget.onBalanceUpdated,
        );
    final precio = widget.producto.precio;
    _montoController = TextEditingController(
      text: (precio != null && precio > 0) ? precio.toStringAsFixed(2) : '',
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _controller.cargarSaldoFinanciero();
      }
    });
  }

  @override
  void dispose() {
    _montoController.dispose();
    _destinoController.dispose();
    if (_ownsController) {
      _controller.dispose();
    }
    super.dispose();
  }

  Future<void> _copiarTexto(
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
      animation: _controller,
      builder: (context, _) {
        final ctrl = _controller;
        return Scaffold(
          appBar: AppBar(
            title: Text(switch (ctrl.stage) {
              PurchaseFlowStage.capture => 'Preparar pago',
              PurchaseFlowStage.review => 'Revisión de compra',
              PurchaseFlowStage.result => 'Resultado de operación',
            }),
          ),
          body: SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: GpagoTokens.maxFormWidth,
                  ),
                  child: switch (ctrl.stage) {
                    PurchaseFlowStage.capture => _CaptureStep(
                      controller: ctrl,
                      montoController: _montoController,
                      destinoController: _destinoController,
                    ),
                    PurchaseFlowStage.review => _ReviewStep(
                      controller: ctrl,
                      onCopyKey: (k) =>
                          _copiarTexto(context, k, 'Clave de idempotencia'),
                    ),
                    PurchaseFlowStage.result => _ResultStep(
                      controller: ctrl,
                      onCopyUuid: (id) =>
                          _copiarTexto(context, id, 'UUID de operación'),
                      onCopyIdempotency: (k) =>
                          _copiarTexto(context, k, 'Clave de idempotencia'),
                      onClose: () => Navigator.of(context).pop(),
                    ),
                  },
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _CaptureStep extends StatelessWidget {
  const _CaptureStep({
    required this.controller,
    required this.montoController,
    required this.destinoController,
  });

  final PurchaseController controller;
  final TextEditingController montoController;
  final TextEditingController destinoController;

  @override
  Widget build(BuildContext context) {
    final p = controller.producto;
    final cuenta = controller.selectedCuenta;
    final saldoFin = controller.saldoFinanciero;
    final errorMsg = controller.localValidationError ?? controller.error?.mensaje;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
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
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        p.nombreProducto,
                        style: GpagoTokens.subtitleStyle.copyWith(fontSize: 18),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${p.nombreServicio} · ID Producto #${p.idProducto}',
                        style: GpagoTokens.helpStyle,
                      ),
                      if (p.textoAyuda != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          p.textoAyuda!,
                          style: GpagoTokens.helpStyle.copyWith(
                            color: GpagoTokens.ink,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        if (errorMsg != null) ...[
          GpagoStatusBanner(
            tone: controller.error?.isNetworkError == true
                ? GpagoBannerTone.network
                : GpagoBannerTone.rejected,
            title: controller.error != null
                ? 'Validación rechazada (${controller.error!.codigo})'
                : 'Revisa los datos capturados',
            description: errorMsg,
          ),
          const SizedBox(height: 16),
        ],
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: FocusTraversalGroup(
              policy: OrderedTraversalPolicy(),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                const Text('Cuenta de cargo', style: GpagoTokens.subtitleStyle),
                const SizedBox(height: 8),
                if (controller.cuentas.isEmpty)
                  const GpagoStatusBanner(
                    tone: GpagoBannerTone.rejected,
                    title: 'Sin cuentas propias disponibles',
                    description:
                        'El servidor no devolvió una cuenta financiera propia activa para realizar compras.',
                  )
                else ...[
                  DropdownButtonFormField<int>(
                    key: const Key('purchase_account_dropdown'),
                    initialValue: cuenta?.cuentaId,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Cuenta propia (ID servidor)',
                      helperText: 'Seleccionada desde tu inventario autorizado',
                    ),
                    items: controller.cuentas
                        .map(
                          (c) => DropdownMenuItem<int>(
                            value: c.cuentaId,
                            child: Text(
                              'ID #${c.cuentaId} · ${_maskAccount(c.numeroCuenta)} (${c.estatus})',
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        )
                        .toList(),
                    onChanged: controller.isBusy
                        ? null
                        : (selectedId) {
                            if (selectedId == null) return;
                            final found = controller.cuentas.firstWhere(
                              (c) => c.cuentaId == selectedId,
                            );
                            controller.seleccionarCuenta(found);
                          },
                  ),
                  if (cuenta != null) ...[
                    const SizedBox(height: 12),
                    _AccountBalanceBox(
                      cuenta: cuenta,
                      saldoFinanciero: saldoFin,
                      isLoading: controller.isLoadingSaldo,
                    ),
                  ],
                ],
                const SizedBox(height: 20),
                const Text(
                  'Destino e importe',
                  style: GpagoTokens.subtitleStyle,
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: DestinoPagoModo.values.map((modo) {
                    final label = switch (modo) {
                      DestinoPagoModo.telefono => 'Teléfono (10 dígitos)',
                      DestinoPagoModo.referencia => 'Referencia',
                      DestinoPagoModo.sinDestino => 'Sin destino',
                    };
                    return ChoiceChip(
                      key: Key('purchase_mode_${modo.apiHint}'),
                      label: Text(label),
                      selected: controller.destinoModo == modo,
                      onSelected: controller.isBusy
                          ? null
                          : (_) => controller.seleccionarDestinoModo(modo),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 12),
                if (controller.destinoModo == DestinoPagoModo.telefono)
                  TextFormField(
                    key: const Key('purchase_phone_field'),
                    controller: destinoController,
                    enabled: !controller.isBusy,
                    keyboardType: TextInputType.number,
                    maxLength: 10,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: const InputDecoration(
                      labelText: 'Número telefónico de destino',
                      hintText: '10 dígitos sin espacios',
                      helperText: 'Validado por el servidor antes de cobrar',
                      counterText: '',
                    ),
                  )
                else if (controller.destinoModo == DestinoPagoModo.referencia)
                  TextFormField(
                    key: const Key('purchase_reference_field'),
                    controller: destinoController,
                    enabled: !controller.isBusy,
                    maxLength: 100,
                    decoration: const InputDecoration(
                      labelText: 'Referencia de servicio',
                      hintText: 'Alfanumérica sin espacios',
                      helperText:
                          'Se validará conforme al contrato del producto en el servidor',
                      counterText: '',
                    ),
                  )
                else
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: GpagoTokens.canvas,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Text(
                      'Este modo no envía teléfono ni referencia; el servidor resolverá el destino autorizado.',
                      style: GpagoTokens.helpStyle,
                    ),
                  ),
                const SizedBox(height: 16),
                TextFormField(
                  key: const Key('purchase_amount_field'),
                  controller: montoController,
                  readOnly: controller.esMontoFijoCatalogo,
                  enabled: !controller.isBusy,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  decoration: InputDecoration(
                    labelText: 'Importe (MXN)',
                    prefixText: '\$ ',
                    helperText: controller.esMontoFijoCatalogo
                        ? 'Importe fijo establecido en el catálogo autorizado'
                        : 'Ingresa el importe en MXN (hasta 2 decimales)',
                  ),
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  key: const Key('purchase_validate_button'),
                  onPressed:
                      (controller.isBusy || !controller.tieneCuentaSeleccionable)
                      ? null
                      : () => controller.prevalidarYRevisar(
                          montoRaw: montoController.text,
                          destinoRaw: destinoController.text,
                        ),
                  child: controller.isBusy
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('Prevalidar y revisar importe'),
                ),
              ],
            ),
            ),
          ),
        ),
      ],
    );
  }
}

class _AccountBalanceBox extends StatelessWidget {
  const _AccountBalanceBox({
    required this.cuenta,
    required this.saldoFinanciero,
    required this.isLoading,
  });

  final CuentaPropiaDto cuenta;
  final SaldoCuentaFinancieraDto? saldoFinanciero;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    final disponible = saldoFinanciero?.disponible ?? cuenta.saldo;
    final reservado = saldoFinanciero?.reservado;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: GpagoTokens.canvas,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFD7E0EC)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  saldoFinanciero != null
                      ? 'Disponible financiero (servidor)'
                      : 'Saldo en cuenta (servidor)',
                  style: GpagoTokens.helpStyle,
                ),
              ),
              if (isLoading) ...[
                const SizedBox(width: 8),
                const SizedBox(
                  height: 14,
                  width: 14,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ],
            ],
          ),
          const SizedBox(height: 4),
          Text(
            GpagoTokens.formatMxn(disponible),
            style: GpagoTokens.subtitleStyle.copyWith(
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
          if (reservado != null) ...[
            const SizedBox(height: 4),
            Text(
              'Contabilizado: ${GpagoTokens.formatMxn(saldoFinanciero!.contabilizado)} · Reservado: ${GpagoTokens.formatMxn(reservado)}',
              style: GpagoTokens.helpStyle.copyWith(fontSize: 12),
            ),
          ],
        ],
      ),
    );
  }
}

class _ReviewStep extends StatelessWidget {
  const _ReviewStep({
    required this.controller,
    required this.onCopyKey,
  });

  final PurchaseController controller;
  final void Function(String key) onCopyKey;

  @override
  Widget build(BuildContext context) {
    final solicitud = controller.solicitudConfirmada;
    final validacion = controller.validacion;
    final cuenta = controller.selectedCuenta;
    final key = controller.idempotencyKey;
    if (solicitud == null ||
        validacion == null ||
        cuenta == null ||
        key == null) {
      return const SizedBox.shrink();
    }

    final p = controller.producto;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          child: const Text(
            'Confirmar y pagar servicio',
            style: GpagoTokens.titleStyle,
          ),
        ),
        const SizedBox(height: 6),
        const Text(
          'Revisa con atención el importe, el destino y la cuenta financiera antes de confirmar la operación.',
          style: GpagoTokens.helpStyle,
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: FocusTraversalGroup(
              policy: OrderedTraversalPolicy(),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: GpagoTokens.canvas,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.verified_user_outlined,
                        color: GpagoTokens.primary,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            p.nombreProducto,
                            style: GpagoTokens.subtitleStyle.copyWith(
                              fontSize: 18,
                            ),
                          ),
                          Text(
                            p.nombreServicio,
                            style: GpagoTokens.helpStyle,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: GpagoTokens.canvas,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    children: [
                      _SummaryRow(
                        label: 'Cuenta propia',
                        value:
                            'ID #${cuenta.cuentaId} · ${_maskAccount(cuenta.numeroCuenta)}',
                      ),
                      const SizedBox(height: 8),
                      _SummaryRow(
                        label: 'Servicio / Producto',
                        value: '${solicitud.pago.idServicio} / ${solicitud.pago.idProducto}',
                      ),
                      const SizedBox(height: 8),
                      _SummaryRow(
                        label: 'Destino',
                        value: solicitud.pago.destinoResumen,
                      ),
                      if (validacion.requiereVerificacionReferencia) ...[
                        const SizedBox(height: 8),
                        const _SummaryRow(
                          label: 'Verificación de referencia',
                          value: 'Requerida en servidor antes de reservar',
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: GpagoTokens.canvas,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFD7E0EC)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Importe total a confirmar',
                        style: GpagoTokens.helpStyle,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        GpagoTokens.formatMxn(solicitud.pago.montoPago),
                        style: GpagoTokens.amountStyle,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: GpagoTokens.surface,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFD7E0EC)),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.fingerprint,
                        size: 20,
                        color: GpagoTokens.primary,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Clave de idempotencia única (protección anti-duplicados)',
                              style: TextStyle(
                                fontSize: 12,
                                color: GpagoTokens.muted,
                              ),
                            ),
                            SelectableText(
                              key,
                              style: GpagoTokens.folioStyle.copyWith(
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        tooltip: 'Copiar clave',
                        onPressed: () => onCopyKey(key),
                        icon: const Icon(Icons.copy, size: 18),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                ElevatedButton.icon(
                  key: const Key('purchase_confirm_submit_button'),
                  onPressed: controller.isSubmittingPayment || controller.isBusy
                      ? null
                      : controller.confirmarYEnviarPago,
                  icon: controller.isSubmittingPayment
                      ? const SizedBox(
                          height: 18,
                          width: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.lock_outline),
                  label: Text(
                    controller.isSubmittingPayment
                        ? 'Enviando operación segura...'
                        : 'Confirmar y pagar ${GpagoTokens.formatMxn(solicitud.pago.montoPago)}',
                  ),
                ),
                const SizedBox(height: 10),
                OutlinedButton(
                  key: const Key('purchase_edit_button'),
                  onPressed: controller.isSubmittingPayment || controller.isBusy
                      ? null
                      : () => controller.volverACaptura(),
                  child: const Text('Modificar datos'),
                ),
              ],
            ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ResultStep extends StatelessWidget {
  const _ResultStep({
    required this.controller,
    required this.onCopyUuid,
    required this.onCopyIdempotency,
    required this.onClose,
  });

  final PurchaseController controller;
  final void Function(String uuid) onCopyUuid;
  final void Function(String key) onCopyIdempotency;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final kind = controller.outcomeKind;
    final op = controller.operacion;
    final error = controller.error;
    final key = controller.idempotencyKey;
    final solicitud = controller.solicitudConfirmada;

    return switch (kind) {
      PurchaseOutcomeKind.captured when op != null => _CapturedReceiptCard(
        operacion: op,
        producto: controller.producto,
        saldo: controller.saldoFinanciero,
        onCopyUuid: onCopyUuid,
        onClose: onClose,
      ),
      PurchaseOutcomeKind.pending when op != null => _PendingOutcomeCard(
        operacion: op,
        controller: controller,
        onCopyUuid: onCopyUuid,
        onClose: onClose,
      ),
      PurchaseOutcomeKind.uncertainCommunication => _UncertainOutcomeCard(
        controller: controller,
        idempotencyKey: key,
        solicitud: solicitud,
        operacion: op,
        error: error,
        onCopyIdempotency: onCopyIdempotency,
        onClose: onClose,
      ),
      PurchaseOutcomeKind.rejected || _ => _RejectedOutcomeCard(
        operacion: op,
        error: error,
        onRetryCapture: op == null
            ? () => controller.volverACaptura(limpiarResultado: true)
            : null,
        onClose: onClose,
      ),
    };
  }
}

class _CapturedReceiptCard extends StatelessWidget {
  const _CapturedReceiptCard({
    required this.operacion,
    required this.producto,
    required this.saldo,
    required this.onCopyUuid,
    required this.onClose,
  });

  final OperacionPagoDto operacion;
  final ProductoCatalogoDto producto;
  final SaldoCuentaFinancieraDto? saldo;
  final void Function(String uuid) onCopyUuid;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('purchase_receipt_card'),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: GpagoTokens.successBg,
        borderRadius: BorderRadius.circular(GpagoTokens.radiusCard),
        border: Border.all(
          color: GpagoTokens.successInk.withValues(alpha: 0.35),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const Icon(
                Icons.check_circle,
                color: GpagoTokens.successInk,
                size: 28,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Pago aplicado',
                      style: GpagoTokens.subtitleStyle.copyWith(
                        color: GpagoTokens.successInk,
                      ),
                    ),
                    const Text(
                      'Estado confirmado en servidor: CAPTURED',
                      style: TextStyle(
                        fontSize: 13,
                        color: GpagoTokens.successInk,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: GpagoTokens.surface,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(producto.nombreProducto, style: GpagoTokens.subtitleStyle),
                Text(producto.nombreServicio, style: GpagoTokens.helpStyle),
                const SizedBox(height: 12),
                Text(
                  GpagoTokens.formatMxn(operacion.monto),
                  style: GpagoTokens.amountStyle,
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Expanded(
                      child: Text(
                        'UUID de operación (servidor)',
                        style: GpagoTokens.helpStyle,
                      ),
                    ),
                    TextButton.icon(
                      key: const Key('purchase_copy_uuid_button'),
                      onPressed: () => onCopyUuid(operacion.id),
                      icon: const Icon(Icons.copy, size: 16),
                      label: const Text('Copiar'),
                    ),
                  ],
                ),
                SelectableText(
                  operacion.id,
                  key: const Key('purchase_receipt_uuid'),
                  style: GpagoTokens.folioStyle.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 12),
                _SummaryRow(
                  label: 'Cuenta ID',
                  value: '#${operacion.cuentaId}',
                ),
                const SizedBox(height: 6),
                _SummaryRow(
                  label: 'Fecha respuesta servidor',
                  value: operacion.serverTimestamp != null
                      ? GpagoTokens.formatServerTimestamp(
                          operacion.serverTimestamp!,
                        )
                      : 'No incluida en el DTO actual',
                ),
                if (saldo != null) ...[
                  const SizedBox(height: 6),
                  _SummaryRow(
                    label: 'Saldo disponible actualizado',
                    value: GpagoTokens.formatMxn(saldo!.disponible),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            key: const Key('purchase_back_to_catalog_button'),
            onPressed: onClose,
            child: const Text('Volver al catálogo'),
          ),
        ],
      ),
    );
  }
}

class _PendingOutcomeCard extends StatelessWidget {
  const _PendingOutcomeCard({
    required this.operacion,
    required this.controller,
    required this.onCopyUuid,
    required this.onClose,
  });

  final OperacionPagoDto operacion;
  final PurchaseController controller;
  final void Function(String uuid) onCopyUuid;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final titulo = switch (operacion.estado) {
      EstadoOperacionPago.reserved => 'Fondos reservados · Pendiente de aplicación',
      EstadoOperacionPago.processing => 'Operación en procesamiento',
      _ => 'Resultado pendiente de confirmación (UNKNOWN)',
    };

    return Container(
      key: const Key('purchase_pending_card'),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: GpagoTokens.pendingBg,
        borderRadius: BorderRadius.circular(GpagoTokens.radiusCard),
        border: Border.all(
          color: GpagoTokens.pendingInk.withValues(alpha: 0.35),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const Icon(
                Icons.schedule,
                color: GpagoTokens.pendingInk,
                size: 28,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      titulo,
                      style: GpagoTokens.subtitleStyle.copyWith(
                        color: GpagoTokens.pendingInk,
                        fontSize: 18,
                      ),
                    ),
                    Text(
                      'Estado actual: ${operacion.estado.apiValue}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: GpagoTokens.pendingInk,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (operacion.revisionManual) ...[
            const SizedBox(height: 12),
            Container(
              key: const Key('manual_review_badge'),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: GpagoTokens.surface,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: GpagoTokens.pendingInk),
              ),
              child: const Row(
                children: [
                  Icon(
                    Icons.manage_search,
                    size: 18,
                    color: GpagoTokens.pendingInk,
                  ),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Operación marcada para revisión manual en servidor.',
                      style: TextStyle(
                        color: GpagoTokens.pendingInk,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: GpagoTokens.surface,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  GpagoTokens.formatMxn(operacion.monto),
                  style: GpagoTokens.amountStyle,
                ),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Expanded(
                      child: Text(
                        'UUID de operación',
                        style: GpagoTokens.helpStyle,
                      ),
                    ),
                    TextButton.icon(
                      onPressed: () => onCopyUuid(operacion.id),
                      icon: const Icon(Icons.copy, size: 16),
                      label: const Text('Copiar'),
                    ),
                  ],
                ),
                SelectableText(
                  operacion.id,
                  style: GpagoTokens.folioStyle,
                ),
                const SizedBox(height: 10),
                const Text(
                  'Esta respuesta NO acredita una venta aplicada. No realices un nuevo pago; consulta el estado de este UUID.',
                  style: GpagoTokens.helpStyle,
                ),
                const SizedBox(height: 6),
                Text(
                  'Consultas acotadas en esta vista: ${controller.pollAttempts} de ${controller.maxBoundedPolls}',
                  style: GpagoTokens.helpStyle.copyWith(fontSize: 12),
                ),
              ],
            ),
          ),
          if (controller.error != null) ...[
            const SizedBox(height: 12),
            Text(
              controller.error!.mensaje,
              style: const TextStyle(
                color: GpagoTokens.pendingInk,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
          const SizedBox(height: 16),
          ElevatedButton.icon(
            key: const Key('purchase_poll_status_button'),
            onPressed: (controller.isBusy || !controller.canPollMore)
                ? null
                : controller.consultarEstadoORecuperar,
            icon: const Icon(Icons.refresh),
            label: Text(
              controller.canPollMore
                  ? 'Consultar estado actual'
                  : 'Límite de consultas alcanzado',
            ),
          ),
          if (operacion.estado.esUnknown && !operacion.revisionManual) ...[
            const SizedBox(height: 10),
            OutlinedButton.icon(
              key: const Key('purchase_reconcile_button'),
              onPressed: controller.isBusy
                  ? null
                  : controller.conciliarOperacionUnknown,
              icon: const Icon(Icons.sync),
              label: const Text('Conciliar operación UNKNOWN'),
            ),
          ],
          const SizedBox(height: 10),
          OutlinedButton(
            key: const Key('purchase_back_to_catalog_button'),
            onPressed: controller.isBusy ? null : onClose,
            child: const Text('Volver al catálogo'),
          ),
        ],
      ),
    );
  }
}

class _RejectedOutcomeCard extends StatelessWidget {
  const _RejectedOutcomeCard({
    required this.operacion,
    required this.error,
    required this.onRetryCapture,
    required this.onClose,
  });

  final OperacionPagoDto? operacion;
  final GpagoApiException? error;
  final VoidCallback? onRetryCapture;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final codigo = error?.codigo ?? 'FAILED';
    final detalle =
        error?.mensaje ??
        'El pago fue rechazado de forma definitiva y los fondos reservados fueron liberados.';

    return Container(
      key: const Key('purchase_rejected_card'),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: GpagoTokens.rejectedBg,
        borderRadius: BorderRadius.circular(GpagoTokens.radiusCard),
        border: Border.all(
          color: GpagoTokens.rejectedInk.withValues(alpha: 0.35),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Row(
            children: [
              Icon(Icons.block, color: GpagoTokens.rejectedInk, size: 28),
              SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Operación rechazada',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: GpagoTokens.rejectedInk,
                      ),
                    ),
                    Text(
                      'Resultado final sin reintento automático',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: GpagoTokens.rejectedInk,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: GpagoTokens.surface,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Código de servidor: $codigo',
                  style: GpagoTokens.folioStyle.copyWith(
                    fontWeight: FontWeight.w600,
                    color: GpagoTokens.rejectedInk,
                  ),
                ),
                const SizedBox(height: 8),
                Text(detalle, style: GpagoTokens.bodyStyle),
                if (operacion != null) ...[
                  const SizedBox(height: 12),
                  const Text('UUID de operación', style: GpagoTokens.helpStyle),
                  SelectableText(operacion!.id, style: GpagoTokens.folioStyle),
                ],
                const SizedBox(height: 12),
                const Text(
                  'Esta operación no genera comprobante de éxito y no se reenviará automáticamente.',
                  style: GpagoTokens.helpStyle,
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          if (onRetryCapture != null) ...[
            ElevatedButton(
              key: const Key('purchase_edit_after_reject_button'),
              onPressed: onRetryCapture,
              child: const Text('Revisar y corregir datos'),
            ),
            const SizedBox(height: 10),
          ],
          OutlinedButton(
            key: const Key('purchase_back_to_catalog_button'),
            onPressed: onClose,
            child: const Text('Volver al catálogo'),
          ),
        ],
      ),
    );
  }
}

class _UncertainOutcomeCard extends StatelessWidget {
  const _UncertainOutcomeCard({
    required this.controller,
    required this.idempotencyKey,
    required this.solicitud,
    required this.operacion,
    required this.error,
    required this.onCopyIdempotency,
    required this.onClose,
  });

  final PurchaseController controller;
  final String? idempotencyKey;
  final SolicitudPagoDto? solicitud;
  final OperacionPagoDto? operacion;
  final GpagoApiException? error;
  final void Function(String key) onCopyIdempotency;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('purchase_uncertain_card'),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: GpagoTokens.networkBg,
        borderRadius: BorderRadius.circular(GpagoTokens.radiusCard),
        border: Border.all(
          color: GpagoTokens.networkInk.withValues(alpha: 0.35),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Row(
            children: [
              Icon(
                Icons.wifi_off_outlined,
                color: GpagoTokens.networkInk,
                size: 28,
              ),
              SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Comunicación incierta',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: GpagoTokens.networkInk,
                      ),
                    ),
                    Text(
                      'Resultado incierto · No iniciar otra compra',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: GpagoTokens.networkInk,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: GpagoTokens.surface,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  error?.mensaje ??
                      'Se perdió la comunicación antes de recibir confirmación del servidor.',
                  style: GpagoTokens.bodyStyle,
                ),
                const SizedBox(height: 12),
                const Text(
                  'Se conserva la misma clave de idempotencia y el cuerpo exacto de tu solicitud para evitar cobros duplicados.',
                  style: GpagoTokens.helpStyle,
                ),
                if (idempotencyKey != null) ...[
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Text(
                          'Idempotency-Key conservada',
                          style: GpagoTokens.helpStyle,
                        ),
                      ),
                      TextButton.icon(
                        onPressed: () => onCopyIdempotency(idempotencyKey!),
                        icon: const Icon(Icons.copy, size: 16),
                        label: const Text('Copiar'),
                      ),
                    ],
                  ),
                  SelectableText(
                    idempotencyKey!,
                    key: const Key('purchase_uncertain_idempotency_key'),
                    style: GpagoTokens.folioStyle,
                  ),
                ],
                if (operacion != null) ...[
                  const SizedBox(height: 10),
                  const Text('UUID registrado', style: GpagoTokens.helpStyle),
                  SelectableText(operacion!.id, style: GpagoTokens.folioStyle),
                ],
                if (solicitud != null) ...[
                  const SizedBox(height: 10),
                  _SummaryRow(
                    label: 'Importe protegido',
                    value: GpagoTokens.formatMxn(solicitud!.pago.montoPago),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            key: const Key('purchase_recover_button'),
            onPressed: controller.isBusy
                ? null
                : controller.consultarEstadoORecuperar,
            icon: const Icon(Icons.refresh),
            label: Text(
              operacion != null
                  ? 'Consultar estado por UUID'
                  : 'Verificar estado con misma clave',
            ),
          ),
          const SizedBox(height: 10),
          OutlinedButton(
            key: const Key('purchase_back_to_catalog_button'),
            onPressed: controller.isBusy ? null : onClose,
            child: const Text('Volver al catálogo'),
          ),
        ],
      ),
    );
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Flexible(child: Text(label, style: GpagoTokens.helpStyle)),
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

String _maskAccount(String numero) {
  final clean = numero.trim();
  if (clean.length <= 4) return clean;
  return '**** ${clean.substring(clean.length - 4)}';
}
