import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/auth_controller.dart';
import '../controllers/catalog_controller.dart';
import '../theme/gpago_theme.dart';
import 'catalog_screens.dart';

class _CenteredFormShell extends StatelessWidget {
  const _CenteredFormShell({
    required this.child,
    this.maxWidth = GpagoTokens.maxFormWidth,
  });

  final Widget child;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxWidth),
            child: child,
          ),
        ),
      ),
    );
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.controller});

  final AuthController controller;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _correoController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void dispose() {
    _correoController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    await widget.controller.iniciarLogin(
      correo: _correoController.text,
      password: _passwordController.text,
    );
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = widget.controller;
    final feedback = ctrl.feedback;

    return Scaffold(
      appBar: AppBar(title: const Text('GPago')),
      body: _CenteredFormShell(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text('Iniciar sesión', style: GpagoTokens.titleStyle),
                  const SizedBox(height: 8),
                  const Text(
                    'Ingresa tus credenciales para recibir un código de verificación en tu correo.',
                    style: GpagoTokens.helpStyle,
                  ),
                  if (feedback != null) ...[
                    const SizedBox(height: 16),
                    GpagoStatusBanner(
                      tone: feedback.tone,
                      title: feedback.title,
                      description: feedback.description,
                    ),
                  ],
                  const SizedBox(height: 20),
                  TextFormField(
                    key: const Key('login_correo_field'),
                    controller: _correoController,
                    enabled: !ctrl.isBusy,
                    keyboardType: TextInputType.emailAddress,
                    textInputAction: TextInputAction.next,
                    autofillHints: const [AutofillHints.email],
                    decoration: const InputDecoration(
                      labelText: 'Correo electrónico',
                      hintText: 'usuario@dominio.com',
                      helperText: 'Máximo 100 caracteres',
                    ),
                    validator: (value) {
                      final trimmed = value?.trim() ?? '';
                      if (trimmed.isEmpty) {
                        return 'El correo electrónico es obligatorio';
                      }
                      if (trimmed.length > 100 || !trimmed.contains('@')) {
                        return 'Ingresa un correo electrónico válido';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    key: const Key('login_password_field'),
                    controller: _passwordController,
                    enabled: !ctrl.isBusy,
                    obscureText: _obscurePassword,
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _submit(),
                    decoration: InputDecoration(
                      labelText: 'Contraseña',
                      helperText: 'Protegida con verificación en dos pasos',
                      suffixIcon: IconButton(
                        tooltip: _obscurePassword
                            ? 'Mostrar contraseña'
                            : 'Ocultar contraseña',
                        onPressed: () {
                          setState(() => _obscurePassword = !_obscurePassword);
                        },
                        icon: Icon(
                          _obscurePassword
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                      ),
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'La contraseña es obligatoria';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton(
                    key: const Key('login_submit_button'),
                    onPressed: ctrl.isBusy ? null : _submit,
                    child: ctrl.isBusy
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Text('Continuar'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Traducción nativa de la pantalla Stitch "Verificación en dos pasos" (`8a6c751244c2493b9c5e16ab2b35077d`).
class OtpVerificationScreen extends StatefulWidget {
  const OtpVerificationScreen({super.key, required this.controller});

  final AuthController controller;

  @override
  State<OtpVerificationScreen> createState() => _OtpVerificationScreenState();
}

class _OtpVerificationScreenState extends State<OtpVerificationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _otpController = TextEditingController();
  Timer? _timer;
  Duration _remaining = Duration.zero;

  @override
  void initState() {
    super.initState();
    _syncRemaining();
    _timer = Timer.periodic(
      const Duration(seconds: 1),
      (_) => _syncRemaining(),
    );
  }

  @override
  void didUpdateWidget(covariant OtpVerificationScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncRemaining();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _otpController.dispose();
    super.dispose();
  }

  void _syncRemaining() {
    final desafio = widget.controller.desafio;
    if (desafio == null) return;
    final diff = desafio.expiresAt.toUtc().difference(
      widget.controller.nowUtc(),
    );
    final next = diff.isNegative ? Duration.zero : diff;
    if (mounted && next != _remaining) {
      setState(() => _remaining = next);
    }
  }

  Future<void> _pegarCodigo() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final raw = data?.text ?? '';
    final digits = raw.replaceAll(RegExp(r'[^0-9]'), '');
    if (digits.isNotEmpty) {
      final six = digits.length > 6 ? digits.substring(0, 6) : digits;
      _otpController.text = six;
      _otpController.selection = TextSelection.collapsed(offset: six.length);
    }
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    await widget.controller.verificarOtp(_otpController.text);
  }

  String _formatCountdown(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = widget.controller;
    final desafio = ctrl.desafio;
    if (desafio == null) return const SizedBox.shrink();

    final expirado = _remaining == Duration.zero;
    final feedback = ctrl.feedback;

    return Scaffold(
      appBar: AppBar(
        title: const Text('GPago'),
        leading: IconButton(
          tooltip: 'Volver a inicio de sesión',
          onPressed: ctrl.isBusy ? null : ctrl.volverALogin,
          icon: const Icon(Icons.arrow_back),
        ),
      ),
      body: _CenteredFormShell(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Verificación en dos pasos', style: GpagoTokens.titleStyle),
            const SizedBox(height: 8),
            Text(
              'Ingresa el código de 6 dígitos que enviamos a ${desafio.correoEnmascarado}. Este código de un solo uso caduca al agotarse el tiempo.',
              style: GpagoTokens.helpStyle,
            ),
            if (feedback != null) ...[
              const SizedBox(height: 16),
              GpagoStatusBanner(
                tone: feedback.tone,
                title: feedback.title,
                description: feedback.description,
              ),
            ],
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextFormField(
                        key: const Key('otp_code_field'),
                        controller: _otpController,
                        enabled: !ctrl.isBusy,
                        keyboardType: TextInputType.number,
                        maxLength: 6,
                        inputFormatters: [
                          FilteringTextInputFormatter.digitsOnly,
                        ],
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 24,
                          letterSpacing: 6,
                          fontWeight: FontWeight.w600,
                          color: GpagoTokens.ink,
                        ),
                        decoration: InputDecoration(
                          labelText: 'Código OTP de 6 dígitos',
                          hintText: '000000',
                          counterText: '',
                          suffixIcon: TextButton.icon(
                            key: const Key('otp_paste_button'),
                            onPressed: ctrl.isBusy ? null : _pegarCodigo,
                            icon: const Icon(Icons.content_paste, size: 18),
                            label: const Text('Pegar código'),
                          ),
                        ),
                        validator: (value) {
                          final trimmed = value?.trim() ?? '';
                          if (!RegExp(r'^[0-9]{6}$').hasMatch(trimmed)) {
                            return 'Ingresa los 6 dígitos numéricos del código';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 12,
                        runSpacing: 8,
                        alignment: WrapAlignment.spaceBetween,
                        children: [
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.schedule,
                                size: 18,
                                color: expirado
                                    ? GpagoTokens.rejectedInk
                                    : GpagoTokens.muted,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                expirado
                                    ? 'Código expirado'
                                    : 'El código expira en ${_formatCountdown(_remaining)}',
                                style: GpagoTokens.helpStyle.copyWith(
                                  color: expirado
                                      ? GpagoTokens.rejectedInk
                                      : GpagoTokens.muted,
                                  fontWeight: expirado
                                      ? FontWeight.w600
                                      : FontWeight.w400,
                                ),
                              ),
                            ],
                          ),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(
                                Icons.shield_outlined,
                                size: 18,
                                color: GpagoTokens.muted,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                'Intentos restantes: ${desafio.intentosRestantes}',
                                style: GpagoTokens.helpStyle,
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton(
                        key: const Key('otp_verify_button'),
                        onPressed: ctrl.isBusy ? null : _submit,
                        child: ctrl.isBusy
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Text('Verificar código'),
                      ),
                      const SizedBox(height: 8),
                      TextButton(
                        key: const Key('otp_resend_button'),
                        onPressed:
                            (ctrl.isBusy ||
                                expirado ||
                                desafio.reenviosRestantes <= 0)
                            ? null
                            : ctrl.reenviarOtp,
                        child: Text(
                          desafio.reenviosRestantes > 0
                              ? '¿No recibiste el código? Reenviar (${desafio.reenviosRestantes})'
                              : 'Sin reenvíos disponibles',
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class SessionConflictScreen extends StatelessWidget {
  const SessionConflictScreen({super.key, required this.controller});

  final AuthController controller;

  @override
  Widget build(BuildContext context) {
    final conflicto = controller.conflicto;
    if (conflicto == null) return const SizedBox.shrink();

    final feedback = controller.feedback;

    return Scaffold(
      appBar: AppBar(title: const Text('GPago · Sesión única')),
      body: _CenteredFormShell(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            GpagoStatusBanner(
              tone: GpagoBannerTone.pending,
              title: 'Sesión activa en otra plataforma',
              description:
                  'Detectamos una sesión vigente en GPago ${conflicto.plataformaActiva.etiqueta} '
                  '(iniciada ${_formatIsoLocal(conflicto.sesionActivaDesde)}). '
                  'Por seguridad solo puedes mantener una sesión activa a la vez.',
            ),
            if (feedback != null) ...[
              const SizedBox(height: 16),
              GpagoStatusBanner(
                tone: feedback.tone,
                title: feedback.title,
                description: feedback.description,
              ),
            ],
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text(
                      'Confirmación explícita',
                      style: GpagoTokens.subtitleStyle,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Si continúas, se cerrará inmediatamente la sesión en '
                      'GPago ${conflicto.plataformaActiva.etiqueta} y se autorizará '
                      'este acceso en GPago ${conflicto.plataformaNueva.etiqueta}.',
                      style: GpagoTokens.bodyStyle,
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton(
                      key: const Key('conflict_confirm_button'),
                      onPressed: controller.isBusy
                          ? null
                          : controller.confirmarReemplazoSesion,
                      child: controller.isBusy
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text('Cerrar sesión anterior y continuar'),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton(
                      key: const Key('conflict_cancel_button'),
                      onPressed: controller.isBusy
                          ? null
                          : controller.cancelarConflictoSesion,
                      child: const Text('Cancelar'),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  static String _formatIsoLocal(DateTime utc) {
    final local = utc.toLocal();
    final y = local.year.toString().padLeft(4, '0');
    final m = local.month.toString().padLeft(2, '0');
    final d = local.day.toString().padLeft(2, '0');
    final hh = local.hour.toString().padLeft(2, '0');
    final mm = local.minute.toString().padLeft(2, '0');
    return '$d/$m/$y $hh:$mm';
  }
}

class ProtectedHomeScreen extends StatefulWidget {
  const ProtectedHomeScreen({super.key, required this.controller});

  final AuthController controller;

  @override
  State<ProtectedHomeScreen> createState() => _ProtectedHomeScreenState();
}

class _ProtectedHomeScreenState extends State<ProtectedHomeScreen> {
  late final CatalogController _catalogController;
  int _selectedIndex = 0;

  @override
  void initState() {
    super.initState();
    _catalogController = CatalogController(
      apiClient: widget.controller.apiClient,
    );
  }

  @override
  void dispose() {
    _catalogController.dispose();
    super.dispose();
  }

  Future<void> _cargarCatalogo() async {
    final token = widget.controller.tokens?.accessToken;
    if (token == null || token.isEmpty) return;
    await _catalogController.cargarProductos(
      accessToken: token,
      onUnauthorized: widget.controller.manejarSesionNoAutorizada,
    );
  }

  void _onDestinationSelected(int index) {
    setState(() => _selectedIndex = index);
    if (index == 1 && !_catalogController.hasLoadedOnce) {
      _cargarCatalogo();
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final perfil = controller.perfil;
    final cliente = controller.cliente;
    final tokens = controller.tokens;
    final feedback = controller.feedback;

    if (perfil == null || tokens == null) {
      return const SizedBox.shrink();
    }

    final profileBody = _CenteredFormShell(
      maxWidth: GpagoTokens.maxContentWidth,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('Cuenta verificada', style: GpagoTokens.titleStyle),
          const SizedBox(height: 8),
          Text(
            'Sesión única activa en ${tokens.plataforma.etiqueta}.',
            style: GpagoTokens.helpStyle,
          ),
          if (feedback != null) ...[
            const SizedBox(height: 16),
            GpagoStatusBanner(
              tone: feedback.tone,
              title: feedback.title,
              description: feedback.description,
            ),
          ],
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Perfil', style: GpagoTokens.subtitleStyle),
                  const SizedBox(height: 12),
                  Text(
                    cliente?.nombreCompleto.isNotEmpty == true
                        ? cliente!.nombreCompleto
                        : perfil.correo,
                    style: GpagoTokens.bodyStyle.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(perfil.correo, style: GpagoTokens.helpStyle),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 12,
                    runSpacing: 8,
                    children: [
                      Chip(label: Text('Rol: ${perfil.rol}')),
                      Chip(
                        label: Text(
                          'Estatus: ${cliente?.estatus ?? (perfil.activo ? 'ACTIVO' : 'INACTIVO')}',
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Cuentas propias',
                        style: GpagoTokens.subtitleStyle,
                      ),
                      OutlinedButton.icon(
                        key: const Key('refresh_session_button'),
                        onPressed: controller.isBusy
                            ? null
                            : controller.refrescarSesionProtegida,
                        icon: const Icon(Icons.refresh, size: 18),
                        label: const Text('Verificar sesión'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (controller.cuentas.isEmpty)
                    const Text(
                      'No se encontraron cuentas asociadas a este cliente.',
                      style: GpagoTokens.helpStyle,
                    )
                  else
                    ...controller.cuentas.map(
                      (cuenta) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: GpagoTokens.canvas,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: const Color(0xFFD7E0EC)),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Wrap(
                                spacing: 12,
                                runSpacing: 4,
                                alignment: WrapAlignment.spaceBetween,
                                children: [
                                  Text(
                                    'Cuenta ID #${cuenta.cuentaId}',
                                    style: GpagoTokens.bodyStyle.copyWith(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  Text(
                                    cuenta.estatus,
                                    style: GpagoTokens.helpStyle.copyWith(
                                      fontWeight: FontWeight.w600,
                                      color: cuenta.estatus == 'ACTIVA'
                                          ? GpagoTokens.successInk
                                          : GpagoTokens.muted,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 6),
                              SelectableText(
                                cuenta.numeroCuenta,
                                style: GpagoTokens.folioStyle,
                              ),
                              const SizedBox(height: 8),
                              Text(
                                _formatMxn(cuenta.saldo),
                                style: GpagoTokens.amountStyle.copyWith(
                                  fontSize: 24,
                                  height: 32 / 24,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );

    return Scaffold(
      appBar: AppBar(
        title: const Text('GPago'),
        actions: [
          TextButton.icon(
            key: const Key('logout_button'),
            onPressed: controller.isBusy
                ? null
                : () {
                    _catalogController.limpiar();
                    controller.cerrarSesion();
                  },
            icon: const Icon(Icons.logout),
            label: const Text('Cerrar sesión'),
          ),
        ],
      ),
      body: _selectedIndex == 0
          ? profileBody
          : CatalogView(
              controller: _catalogController,
              onRefresh: _cargarCatalogo,
            ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: _onDestinationSelected,
        destinations: const [
          NavigationDestination(
            key: Key('nav_profile_tab'),
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Perfil',
          ),
          NavigationDestination(
            key: Key('nav_catalog_tab'),
            icon: Icon(Icons.grid_view_outlined),
            selectedIcon: Icon(Icons.grid_view),
            label: 'Catálogo',
          ),
        ],
      ),
    );
  }

  static String _formatMxn(double monto) {
    final fixed = monto.toStringAsFixed(2);
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
}
