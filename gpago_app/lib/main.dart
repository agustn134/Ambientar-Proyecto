import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'controllers/auth_controller.dart';
import 'models/auth_models.dart';
import 'screens/auth_screens.dart';
import 'services/gpago_api_client.dart';
import 'services/session_storage.dart';
import 'theme/gpago_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env');
  final controller = AuthController(
    apiClient: GpagoApiClient(
      baseUrl: GPagoApp.apiBaseUrl,
      plataforma: PlataformaCliente.android,
    ),
    storage: PlatformSecureSessionStorage(),
  );
  runApp(GPagoApp(controller: controller));
}

class GPagoApp extends StatefulWidget {
  const GPagoApp({super.key, this.controller});

  final AuthController? controller;

  static String get apiBaseUrl => dotenv.get('API_BASE_URL');

  @override
  State<GPagoApp> createState() => _GPagoAppState();
}

class _GPagoAppState extends State<GPagoApp> {
  late final AuthController _controller;
  late final bool _ownsController;

  @override
  void initState() {
    super.initState();
    _ownsController = widget.controller == null;
    _controller =
        widget.controller ??
        AuthController(
          apiClient: GpagoApiClient(
            baseUrl: GPagoApp.apiBaseUrl,
            plataforma: PlataformaCliente.android,
          ),
          storage: PlatformSecureSessionStorage(),
        );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _controller.restoreSession();
    });
  }

  @override
  void dispose() {
    if (_ownsController) {
      _controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GPago',
      theme: GpagoTokens.lightTheme(),
      home: AnimatedBuilder(
        animation: _controller,
        builder: (context, _) {
          return switch (_controller.stage) {
            AuthStage.restoring => const _RestoringSessionView(),
            AuthStage.login => LoginScreen(controller: _controller),
            AuthStage.otpVerification => OtpVerificationScreen(
              controller: _controller,
            ),
            AuthStage.sessionConflict => SessionConflictScreen(
              controller: _controller,
            ),
            AuthStage.authenticated => ProtectedHomeScreen(
              controller: _controller,
            ),
          };
        },
      ),
    );
  }
}

class _RestoringSessionView extends StatelessWidget {
  const _RestoringSessionView();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 16),
                Text(
                  'Verificando sesión segura...',
                  style: GpagoTokens.bodyStyle,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
