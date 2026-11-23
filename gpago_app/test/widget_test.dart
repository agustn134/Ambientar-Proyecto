import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gpago_app/controllers/auth_controller.dart';
import 'package:gpago_app/main.dart';
import 'package:gpago_app/models/auth_models.dart';
import 'package:gpago_app/services/gpago_api_client.dart';
import 'package:gpago_app/services/session_storage.dart';

typedef _Handler = Future<RawHttpResponse> Function(
  String method,
  String path,
  Map<String, String> headers,
  Map<String, dynamic>? body,
);

class _FakeTransport implements GpagoHttpTransport {
  _FakeTransport(this.handler);

  final _Handler handler;
  final List<String> calls = [];

  @override
  Future<RawHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
  }) async {
    calls.add('$method ${uri.path}');
    final decoded = body != null
        ? jsonDecode(body) as Map<String, dynamic>
        : null;
    return handler(method, uri.path, headers, decoded);
  }
}

RawHttpResponse _okJson(Object datos) {
  return RawHttpResponse(
    statusCode: 200,
    body: jsonEncode({
      'timestamp': '2026-11-21T16:30:00',
      'status': 200,
      'codigo': 'EXITO',
      'mensaje': 'Operación realizada correctamente',
      'path': '/auth',
      'datos': datos,
      'errores': <Object>[],
    }),
  );
}

RawHttpResponse _errJson(
  int status,
  String codigo,
  String mensaje, {
  Object? datos,
}) {
  return RawHttpResponse(
    statusCode: status,
    body: jsonEncode({
      'timestamp': '2026-11-21T16:30:00',
      'status': status,
      'codigo': codigo,
      'mensaje': mensaje,
      'path': '/auth',
      'datos': ?datos,
      'errores': <Object>[],
    }),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Flujo completo Login -> Pegar OTP -> Ruta protegida y Logout', (
    WidgetTester tester,
  ) async {
    final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
    String? clipboardText = '654321';
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.getData') {
        return <String, dynamic>{'text': clipboardText};
      }
      return null;
    });

    final transport = _FakeTransport((method, path, headers, body) async {
      if (method == 'POST' && path == '/auth/login') {
        expect(body?['plataforma'], 'ANDROID');
        return _okJson({
          'desafioId': '11111111-1111-1111-1111-111111111111',
          'correoEnmascarado': 'u***o@example.com',
          'plataforma': 'ANDROID',
          'requiereOtp': true,
          'expiresIn': 180,
          'expiresAt': now.add(const Duration(seconds: 180)).toIso8601String(),
          'intentosRestantes': 3,
          'reenviosRestantes': 3,
        });
      }
      if (method == 'POST' && path == '/auth/otp/verificar') {
        expect(body?['codigoOtp'], '654321');
        return _okJson({
          'accessToken': 'jwt-android-1',
          'tokenType': 'Bearer',
          'expiresIn': 300,
          'expiresAt': now.add(const Duration(seconds: 300)).toIso8601String(),
          'sesionId': '22222222-2222-2222-2222-222222222222',
          'plataforma': 'ANDROID',
        });
      }
      if (method == 'GET' && path == '/auth/me') {
        expect(headers['Authorization'], 'Bearer jwt-android-1');
        return _okJson({
          'usuarioId': 7,
          'clienteId': 12,
          'correo': 'usuario@example.com',
          'activo': true,
          'rol': 'CLIENTE',
        });
      }
      if (method == 'GET' && path == '/clientes/me') {
        return _okJson({
          'cliente': {
            'clienteId': 12,
            'nombre': 'Ana',
            'apellidoPaterno': 'Pérez',
            'apellidoMaterno': 'López',
            'correo': 'usuario@example.com',
            'estatus': 'ACTIVO',
          },
        });
      }
      if (method == 'GET' && path == '/cuentas/me') {
        return _okJson([
          {
            'cuentaId': 45,
            'numeroCuenta': '12345678901234567890',
            'clienteId': 12,
            'saldo': 1250.5,
            'estatus': 'ACTIVA',
          },
        ]);
      }
      if (method == 'POST' && path == '/auth/logout') {
        return const RawHttpResponse(statusCode: 204, body: '');
      }
      fail('Ruta inesperada: $method $path');
    });

    final storage = InMemorySessionStorage();
    final controller = AuthController(
      apiClient: GpagoApiClient(
        baseUrl: 'https://api.gpago.local',
        transport: transport,
      ),
      storage: storage,
      clock: () => now,
    );

    await tester.pumpWidget(GPagoApp(controller: controller));
    await tester.pumpAndSettle();

    expect(find.text('Iniciar sesión'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('login_correo_field')),
      'usuario@example.com',
    );
    await tester.enterText(
      find.byKey(const Key('login_password_field')),
      'Secreto123!',
    );
    await tester.tap(find.byKey(const Key('login_submit_button')));
    await tester.pumpAndSettle();

    expect(find.text('Verificación en dos pasos'), findsOneWidget);
    await tester.tap(find.byKey(const Key('otp_paste_button')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('otp_verify_button')));
    await tester.pumpAndSettle();

    expect(find.text('Cuenta verificada'), findsOneWidget);
    expect(find.text('Ana Pérez López'), findsOneWidget);
    expect(find.text('Cuenta ID #45'), findsOneWidget);
    expect(find.text('\$1,250.50 MXN'), findsOneWidget);
    expect((await storage.read())?.accessToken, 'jwt-android-1');

    await tester.tap(find.byKey(const Key('logout_button')));
    await tester.pumpAndSettle();

    expect(find.text('Iniciar sesión'), findsOneWidget);
    expect(find.text('Sesión cerrada'), findsOneWidget);
    expect(await storage.read(), isNull);
    expect(transport.calls, contains('POST /auth/logout'));
  });

  testWidgets(
    'Rechaza OTP vencido/reutilizado y exige confirmación explícita ante conflicto Web/Android',
    (WidgetTester tester) async {
      final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
      int otpAttempt = 0;

      final transport = _FakeTransport((method, path, headers, body) async {
        if (method == 'POST' && path == '/auth/login') {
          return _okJson({
            'desafioId': '33333333-3333-3333-3333-333333333333',
            'correoEnmascarado': 'u***o@example.com',
            'plataforma': 'ANDROID',
            'requiereOtp': true,
            'expiresIn': 180,
            'expiresAt': now
                .add(const Duration(seconds: 180))
                .toIso8601String(),
            'intentosRestantes': 3,
            'reenviosRestantes': 3,
          });
        }
        if (method == 'POST' && path == '/auth/otp/verificar') {
          otpAttempt++;
          if (otpAttempt == 1) {
            return _errJson(
              401,
              'OTP_EXPIRADO',
              'El código OTP expiró; solicita uno nuevo',
            );
          }
          return _errJson(
            409,
            'CONFLICTO_SESION_ACTIVA',
            'Ya existe una sesión activa en otra plataforma',
            datos: {
              'conflictoSesion': true,
              'desafioId': '33333333-3333-3333-3333-333333333333',
              'tokenConfirmacion': 'token-confirmacion-unico-123456',
              'plataformaActiva': 'WEB',
              'plataformaNueva': 'ANDROID',
              'sesionActivaDesde': now
                  .subtract(const Duration(minutes: 2))
                  .toIso8601String(),
              'expiraConfirmacionEn': now
                  .add(const Duration(minutes: 3))
                  .toIso8601String(),
            },
          );
        }
        if (method == 'POST' && path == '/auth/sesion/reemplazar') {
          expect(body?['confirmarCierre'], true);
          return _okJson({
            'accessToken': 'jwt-android-reemplazo',
            'tokenType': 'Bearer',
            'expiresIn': 300,
            'expiresAt': now
                .add(const Duration(seconds: 300))
                .toIso8601String(),
            'sesionId': '44444444-4444-4444-4444-444444444444',
            'plataforma': 'ANDROID',
          });
        }
        if (method == 'GET' && path == '/auth/me') {
          return _okJson({
            'usuarioId': 7,
            'clienteId': 12,
            'correo': 'usuario@example.com',
            'activo': true,
            'rol': 'CLIENTE',
          });
        }
        if (method == 'GET' && path == '/clientes/me') {
          return _okJson({
            'cliente': {
              'clienteId': 12,
              'nombre': 'Ana',
              'apellidoPaterno': 'Pérez',
              'apellidoMaterno': 'López',
              'correo': 'usuario@example.com',
              'estatus': 'ACTIVO',
            },
          });
        }
        if (method == 'GET' && path == '/cuentas/me') {
          return _okJson([]);
        }
        fail('Ruta inesperada: $method $path');
      });

      final controller = AuthController(
        apiClient: GpagoApiClient(
          baseUrl: 'https://api.gpago.local',
          transport: transport,
        ),
        storage: InMemorySessionStorage(),
        clock: () => now,
      );

      await tester.pumpWidget(GPagoApp(controller: controller));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const Key('login_correo_field')),
        'usuario@example.com',
      );
      await tester.enterText(
        find.byKey(const Key('login_password_field')),
        'Secreto123!',
      );
      await tester.tap(find.byKey(const Key('login_submit_button')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('otp_code_field')), '111111');
      await tester.tap(find.byKey(const Key('otp_verify_button')));
      await tester.pumpAndSettle();

      expect(find.text('Código OTP expirado'), findsOneWidget);

      // Segundo intento dispara conflicto Web/Android
      await tester.enterText(find.byKey(const Key('otp_code_field')), '654321');
      await tester.tap(find.byKey(const Key('otp_verify_button')));
      await tester.pumpAndSettle();

      expect(find.text('Sesión activa en otra plataforma'), findsOneWidget);
      expect(find.textContaining('GPago Web'), findsWidgets);

      await tester.tap(find.byKey(const Key('conflict_confirm_button')));
      await tester.pumpAndSettle();

      expect(find.text('Cuenta verificada'), findsOneWidget);
      expect(controller.tokens?.accessToken, 'jwt-android-reemplazo');
    },
  );

  testWidgets('Restaura sesión y expulsa si el backend revocó el JWT (401)', (
    WidgetTester tester,
  ) async {
    final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
    final storage = InMemorySessionStorage(
      LoginTokensDto(
        accessToken: 'jwt-revocado',
        tokenType: 'Bearer',
        expiresIn: 300,
        expiresAt: now.add(const Duration(seconds: 200)),
        sesionId: '55555555-5555-5555-5555-555555555555',
        plataforma: PlataformaCliente.android,
      ),
    );

    final transport = _FakeTransport((method, path, headers, body) async {
      return _errJson(401, 'NO_AUTENTICADO', 'Sesión revocada');
    });

    final controller = AuthController(
      apiClient: GpagoApiClient(
        baseUrl: 'https://api.gpago.local',
        transport: transport,
      ),
      storage: storage,
      clock: () => now,
    );

    await tester.pumpWidget(GPagoApp(controller: controller));
    await tester.pumpAndSettle();

    expect(find.text('Iniciar sesión'), findsOneWidget);
    expect(find.text('Sesión revocada o expirada'), findsOneWidget);
    expect(await storage.read(), isNull);
  });
}
