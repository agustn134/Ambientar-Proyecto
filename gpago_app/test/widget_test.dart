import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gpago_app/controllers/auth_controller.dart';
import 'package:gpago_app/main.dart';
import 'package:gpago_app/models/auth_models.dart';
import 'package:gpago_app/services/gpago_api_client.dart';
import 'package:gpago_app/services/session_storage.dart';
import 'package:gpago_app/theme/gpago_theme.dart';

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
    final fullPath = uri.hasQuery ? '${uri.path}?${uri.query}' : uri.path;
    calls.add('$method $fullPath');
    final decoded = body != null
        ? jsonDecode(body) as Map<String, dynamic>
        : null;
    return handler(method, fullPath, headers, decoded);
  }
}

RawHttpResponse _okJson(Object datos, {int statusCode = 200}) {
  return RawHttpResponse(
    statusCode: statusCode,
    body: jsonEncode({
      'timestamp': '2026-11-21T16:30:00',
      'status': statusCode,
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

Future<void> _ensureMountedAndVisible(WidgetTester tester, Finder finder) async {
  if (finder.evaluate().isEmpty) {
    final listFinder = find.byType(ListView);
    if (listFinder.evaluate().isNotEmpty) {
      await tester.drag(listFinder.last, const Offset(0, -450));
      await tester.pumpAndSettle();
    }
  }
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
}

Future<void> _tapVisible(WidgetTester tester, Key key) async {
  final finder = find.byKey(key);
  await _ensureMountedAndVisible(tester, finder);
  await tester.tap(finder);
  await tester.pumpAndSettle();
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

  testWidgets(
    'M2: Lista catálogo real, agrupa por idCatTipoServicio, muestra detalle y no invoca sincronización',
    (WidgetTester tester) async {
      final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
      final storage = InMemorySessionStorage(
        LoginTokensDto(
          accessToken: 'jwt-catalogo-ok',
          tokenType: 'Bearer',
          expiresIn: 300,
          expiresAt: now.add(const Duration(seconds: 300)),
          sesionId: '66666666-6666-6666-6666-666666666666',
          plataforma: PlataformaCliente.android,
        ),
      );

      final transport = _FakeTransport((method, path, headers, body) async {
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
        if (method == 'GET' && path == '/api/gestopago/productos') {
          expect(headers['Authorization'], 'Bearer jwt-catalogo-ok');
          return _okJson([
            {
              'id': 1,
              'idProducto': 185,
              'idServicio': 56,
              'servicio': 'Servicio Agua',
              'producto': 'Agua Cancun',
              'idCatTipoServicio': 15,
              'tipoFront': '2',
              'hasDigitoVerificador': false,
              'precio': 10.00,
              'showAyuda': true,
              'tipoReferencia': 'c',
              'legend': 'Atención a clientes',
              'fechaCreacion': '2026-10-08T12:00:00',
              'fechaActualizacion': '2026-10-08T12:00:00',
              'activo': true,
            },
            {
              'id': 2,
              'idProducto': 210,
              'idServicio': 80,
              'servicio': 'Telefonía',
              'producto': 'Recarga 100',
              'idCatTipoServicio': 20,
              'tipoFront': '1',
              'hasDigitoVerificador': true,
              'precio': 100.00,
              'showAyuda': false,
              'tipoReferencia': null,
              'legend': null,
              'fechaCreacion': '2026-10-08T12:00:00',
              'fechaActualizacion': '2026-10-08T12:00:00',
              'activo': false,
            },
          ]);
        }
        fail('Ruta inesperada: $method $path');
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

      await tester.tap(find.byKey(const Key('nav_catalog_tab')));
      await tester.pumpAndSettle();

      expect(find.text('Catálogo de servicios'), findsOneWidget);
      expect(find.text('Todas (2)'), findsOneWidget);
      expect(find.text('Categoría #15 (1)'), findsOneWidget);
      expect(find.text('Categoría #20 (1)'), findsOneWidget);
      expect(find.text('Agua Cancun'), findsOneWidget);
      expect(find.text('Recarga 100'), findsOneWidget);
      expect(find.text('Disponible'), findsOneWidget);
      expect(find.text('No disponible'), findsOneWidget);

      // Filtrar por Categoría #20
      await tester.tap(find.byKey(const Key('catalog_chip_20')));
      await tester.pumpAndSettle();
      expect(find.text('Agua Cancun'), findsNothing);
      expect(find.text('Recarga 100'), findsOneWidget);

      // Abrir detalle del producto no disponible y sin leyenda
      final detailBtn = find.byKey(const Key('product_detail_button_210'));
      await tester.ensureVisible(detailBtn);
      await tester.pumpAndSettle();
      await tester.tap(detailBtn);
      await tester.pumpAndSettle();
      expect(find.text('210'), findsOneWidget);
      expect(
        find.text('Sin indicaciones adicionales registradas para este producto.'),
        findsOneWidget,
      );
      final actionButton = tester.widget<ElevatedButton>(
        find.byKey(const Key('product_detail_action_button')),
      );
      expect(actionButton.onPressed, isNull);

      expect(
        transport.calls.any(
          (call) =>
              call.contains('sincronizar') ||
              call.contains('consultar-externo'),
        ),
        isFalse,
      );
    },
  );

  testWidgets('M2: Maneja catálogo vacío sin inventar productos', (
    WidgetTester tester,
  ) async {
    final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
    final storage = InMemorySessionStorage(
      LoginTokensDto(
        accessToken: 'jwt-catalogo-vacio',
        tokenType: 'Bearer',
        expiresIn: 300,
        expiresAt: now.add(const Duration(seconds: 300)),
        sesionId: '77777777-7777-7777-7777-777777777777',
        plataforma: PlataformaCliente.android,
      ),
    );

    final transport = _FakeTransport((method, path, headers, body) async {
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
      if (method == 'GET' && path == '/api/gestopago/productos') {
        return _okJson([]);
      }
      fail('Ruta inesperada: $method $path');
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

    await tester.tap(find.byKey(const Key('nav_catalog_tab')));
    await tester.pumpAndSettle();

    expect(find.text('Catálogo sin productos disponibles'), findsOneWidget);
  });

  testWidgets(
    'M2: Maneja error de red y servicio indisponible con recuperación al reintentar',
    (WidgetTester tester) async {
      final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
      int catalogAttempts = 0;
      final storage = InMemorySessionStorage(
        LoginTokensDto(
          accessToken: 'jwt-catalogo-red',
          tokenType: 'Bearer',
          expiresIn: 300,
          expiresAt: now.add(const Duration(seconds: 300)),
          sesionId: '88888888-8888-8888-8888-888888888888',
          plataforma: PlataformaCliente.android,
        ),
      );

      final transport = _FakeTransport((method, path, headers, body) async {
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
        if (method == 'GET' && path == '/api/gestopago/productos') {
          catalogAttempts++;
          if (catalogAttempts == 1) {
            throw const GpagoApiException(
              statusCode: 0,
              codigo: 'ERROR_COMUNICACION',
              mensaje: 'Sin conexión de red al consultar el catálogo',
              isNetworkError: true,
            );
          }
          if (catalogAttempts == 2) {
            return _errJson(
              503,
              'SERVICIO_EXTERNO',
              'Catálogo temporalmente no disponible',
            );
          }
          return _okJson([
            {
              'id': 1,
              'idProducto': 185,
              'idServicio': 56,
              'servicio': 'Servicio Agua',
              'producto': 'Agua Cancun',
              'idCatTipoServicio': 15,
              'tipoFront': '2',
              'hasDigitoVerificador': false,
              'precio': 10.00,
              'showAyuda': false,
              'tipoReferencia': 'c',
              'legend': null,
              'activo': true,
            },
          ]);
        }
        fail('Ruta inesperada: $method $path');
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

      await tester.tap(find.byKey(const Key('nav_catalog_tab')));
      await tester.pumpAndSettle();
      expect(find.text('Sin conexión con GPago'), findsOneWidget);

      await tester.tap(find.byKey(const Key('catalog_retry_button')));
      await tester.pumpAndSettle();
      expect(find.text('Catálogo no disponible'), findsOneWidget);

      await tester.tap(find.byKey(const Key('catalog_retry_button')));
      await tester.pumpAndSettle();
      expect(find.text('Agua Cancun'), findsOneWidget);
    },
  );

  testWidgets(
    'M3: Prevalidación, confirmación expresa, anti-doble-toque, RESERVED -> CAPTURED y comprobante real',
    (WidgetTester tester) async {
      final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
      final completer202 = Completer<RawHttpResponse>();
      final idempotencyKeys = <String>[];
      int postPagosCount = 0;
      double saldoDisponible = 500.0;
      double saldoContabilizado = 500.0;
      double saldoReservado = 0.0;

      final storage = InMemorySessionStorage(
        LoginTokensDto(
          accessToken: 'jwt-m3-ok',
          tokenType: 'Bearer',
          expiresIn: 300,
          expiresAt: now.add(const Duration(seconds: 300)),
          sesionId: '99999999-9999-9999-9999-999999999999',
          plataforma: PlataformaCliente.android,
        ),
      );

      final transport = _FakeTransport((method, path, headers, body) async {
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
          return _okJson([
            {
              'cuentaId': 45,
              'numeroCuenta': '12345678901234567890',
              'clienteId': 12,
              'saldo': 500.00,
              'estatus': 'ACTIVA',
            },
          ]);
        }
        if (method == 'GET' && path == '/api/gestopago/productos') {
          return _okJson([
            {
              'id': 1,
              'idProducto': 219,
              'idServicio': 78,
              'servicio': 'Oui Movil',
              'producto': 'Recarga 40',
              'idCatTipoServicio': 13,
              'tipoFront': '1',
              'hasDigitoVerificador': false,
              'precio': 40.00,
              'showAyuda': true,
              'tipoReferencia': 'a',
              'legend': 'Recarga tiempo aire 10 dígitos',
              'activo': true,
            },
          ]);
        }
        if (method == 'GET' && path == '/api/gestopago/cuentas/45/saldo') {
          return _okJson({
            'cuentaId': 45,
            'contabilizado': saldoContabilizado,
            'reservado': saldoReservado,
            'disponible': saldoDisponible,
          });
        }
        if (method == 'POST' && path == '/api/gestopago/pagos/validar') {
          expect(body?['idServicio'], 78);
          expect(body?['idProducto'], 219);
          expect(body?['montoPago'], 40);
          expect(body?['telefono'], '5512345678');
          expect(body?.containsKey('referencia'), isFalse);
          return _okJson({
            'contratoLocalValido': true,
            'idServicio': 78,
            'idProducto': 219,
            'montoPago': 40.00,
            'requiereVerificacionReferencia': false,
            'pagoEnviado': false,
          });
        }
        if (method == 'POST' && path == '/api/gestopago/pagos') {
          postPagosCount++;
          idempotencyKeys.add(headers['Idempotency-Key'] ?? '');
          expect(body?['cuentaId'], 45);
          expect(body?['pago']['telefono'], '5512345678');
          return completer202.future;
        }
        if (method == 'GET' &&
            path ==
                '/api/gestopago/pagos/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee') {
          saldoContabilizado = 460.00;
          saldoReservado = 0.00;
          saldoDisponible = 460.00;
          return _okJson({
            'id': 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
            'cuentaId': 45,
            'monto': 40.00,
            'estado': 'CAPTURED',
            'revisionManual': false,
          });
        }
        fail('Ruta inesperada: $method $path');
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

      await _tapVisible(tester, const Key('nav_catalog_tab'));
      await _tapVisible(tester, const Key('product_detail_button_219'));
      await _tapVisible(tester, const Key('product_detail_action_button'));

      expect(find.text('Preparar pago'), findsOneWidget);
      expect(find.text('\$500.00 MXN'), findsWidgets);

      await tester.ensureVisible(find.byKey(const Key('purchase_phone_field')));
      await tester.enterText(
        find.byKey(const Key('purchase_phone_field')),
        '5512345678',
      );
      await _tapVisible(tester, const Key('purchase_validate_button'));

      expect(find.text('Confirmar y pagar servicio'), findsOneWidget);
      expect(find.text('\$40.00 MXN'), findsWidgets);

      // Doble toque rápido en el botón de confirmación: solo debe dispararse 1 POST.
      final submitFinder = find.byKey(
        const Key('purchase_confirm_submit_button'),
      );
      await tester.ensureVisible(submitFinder);
      await tester.tap(submitFinder);
      await tester.pump();
      await tester.tap(submitFinder, warnIfMissed: false);
      await tester.pump();

      expect(postPagosCount, 1);
      expect(idempotencyKeys.single.length, greaterThanOrEqualTo(8));

      completer202.complete(
        _okJson({
          'id': 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
          'cuentaId': 45,
          'monto': 40.00,
          'estado': 'RESERVED',
          'revisionManual': false,
        }, statusCode: 202),
      );
      await tester.pumpAndSettle();

      // 202 RESERVED NO muestra comprobante de éxito.
      expect(find.byKey(const Key('purchase_pending_card')), findsOneWidget);
      expect(find.byKey(const Key('purchase_receipt_card')), findsNothing);

      await _tapVisible(tester, const Key('purchase_poll_status_button'));

      // Solo CAPTURED presenta comprobante con UUID y fecha real del servidor.
      expect(find.byKey(const Key('purchase_receipt_card')), findsOneWidget);
      expect(
        find.text('aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'),
        findsOneWidget,
      );
      expect(
        find.text(
          GpagoTokens.formatServerTimestamp(
            DateTime.parse('2026-11-21T16:30:00'),
          ),
        ),
        findsOneWidget,
      );
      expect(find.text('\$460.00 MXN'), findsWidgets);
    },
  );

  testWidgets(
    'M3: Timeout e incertidumbre conservan Idempotency-Key y permiten recuperar; UNKNOWN -> FAILED y conflicto 409',
    (WidgetTester tester) async {
      final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
      final observedKeys = <String>[];
      int scenario = 0;

      final storage = InMemorySessionStorage(
        LoginTokensDto(
          accessToken: 'jwt-m3-uncertain',
          tokenType: 'Bearer',
          expiresIn: 300,
          expiresAt: now.add(const Duration(seconds: 300)),
          sesionId: '10101010-1010-1010-1010-101010101010',
          plataforma: PlataformaCliente.android,
        ),
      );

      final transport = _FakeTransport((method, path, headers, body) async {
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
          return _okJson([
            {
              'cuentaId': 45,
              'numeroCuenta': '12345678901234567890',
              'clienteId': 12,
              'saldo': 500.00,
              'estatus': 'ACTIVA',
            },
          ]);
        }
        if (method == 'GET' && path == '/api/gestopago/productos') {
          return _okJson([
            {
              'id': 1,
              'idProducto': 219,
              'idServicio': 78,
              'servicio': 'Oui Movil',
              'producto': 'Recarga 40',
              'idCatTipoServicio': 13,
              'tipoFront': '1',
              'hasDigitoVerificador': false,
              'precio': 40.00,
              'showAyuda': false,
              'tipoReferencia': null,
              'legend': null,
              'activo': true,
            },
          ]);
        }
        if (method == 'GET' && path == '/api/gestopago/cuentas/45/saldo') {
          return _okJson({
            'cuentaId': 45,
            'contabilizado': 500.00,
            'reservado': 0.00,
            'disponible': 500.00,
          });
        }
        if (method == 'POST' && path == '/api/gestopago/pagos/validar') {
          return _okJson({
            'contratoLocalValido': true,
            'idServicio': 78,
            'idProducto': 219,
            'montoPago': 40.00,
            'requiereVerificacionReferencia': false,
            'pagoEnviado': false,
          });
        }
        if (method == 'POST' && path == '/api/gestopago/pagos') {
          observedKeys.add(headers['Idempotency-Key'] ?? '');
          if (scenario == 0) {
            scenario = 1;
            throw const GpagoApiException(
              statusCode: 0,
              codigo: 'ERROR_COMUNICACION',
              mensaje: 'Timeout de red esperando respuesta de pago',
              isNetworkError: true,
            );
          }
          if (scenario == 1) {
            scenario = 2;
            return _okJson({
              'id': '11111111-2222-4333-8444-555555555555',
              'cuentaId': 45,
              'monto': 40.00,
              'estado': 'UNKNOWN',
              'revisionManual': false,
            }, statusCode: 202);
          }
          return _errJson(
            409,
            'IDEMPOTENCIA_CONFLICTO',
            'La clave ya corresponde a otros datos.',
          );
        }
        if (method == 'POST' &&
            path ==
                '/api/gestopago/pagos/11111111-2222-4333-8444-555555555555/conciliar') {
          return _okJson({
            'id': '11111111-2222-4333-8444-555555555555',
            'cuentaId': 45,
            'monto': 40.00,
            'estado': 'FAILED',
            'revisionManual': false,
          });
        }
        fail('Ruta inesperada: $method $path');
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

      await _tapVisible(tester, const Key('nav_catalog_tab'));
      await _tapVisible(tester, const Key('product_detail_button_219'));
      await _tapVisible(tester, const Key('product_detail_action_button'));

      await tester.ensureVisible(find.byKey(const Key('purchase_phone_field')));
      await tester.enterText(
        find.byKey(const Key('purchase_phone_field')),
        '5512345678',
      );
      await _tapVisible(tester, const Key('purchase_validate_button'));
      await _tapVisible(tester, const Key('purchase_confirm_submit_button'));

      // Timeout => Comunicación incierta, sin tratar como FAILED y conservando Idempotency-Key.
      expect(find.byKey(const Key('purchase_uncertain_card')), findsOneWidget);
      expect(find.byKey(const Key('purchase_rejected_card')), findsNothing);
      expect(find.byKey(const Key('purchase_receipt_card')), findsNothing);
      expect(observedKeys.length, 1);

      // Recuperar con la EXACTA misma clave de idempotencia -> UNKNOWN.
      await _tapVisible(tester, const Key('purchase_recover_button'));

      expect(observedKeys.length, 2);
      expect(observedKeys[1], observedKeys[0]);
      expect(find.byKey(const Key('purchase_pending_card')), findsOneWidget);

      // Conciliar UNKNOWN -> FAILED definitivo sin comprobante de éxito.
      await _tapVisible(tester, const Key('purchase_reconcile_button'));

      expect(find.byKey(const Key('purchase_rejected_card')), findsOneWidget);
      expect(find.byKey(const Key('purchase_receipt_card')), findsNothing);
      expect(find.text('Operación rechazada'), findsOneWidget);

      // Volver al catálogo y probar escenario de conflicto 409 en nueva compra.
      await _tapVisible(tester, const Key('purchase_back_to_catalog_button'));

      await _tapVisible(tester, const Key('product_detail_button_219'));
      await _tapVisible(tester, const Key('product_detail_action_button'));
      await tester.ensureVisible(find.byKey(const Key('purchase_phone_field')));
      await tester.enterText(
        find.byKey(const Key('purchase_phone_field')),
        '5512345679',
      );
      await _tapVisible(tester, const Key('purchase_validate_button'));
      await _tapVisible(tester, const Key('purchase_confirm_submit_button'));

      expect(find.byKey(const Key('purchase_rejected_card')), findsOneWidget);
      expect(
        find.text('Código de servidor: IDEMPOTENCIA_CONFLICTO'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'M4: Historial paginado solo CAPTURED, detalle trazable sin fabricar folios y filtro defensivo',
    (WidgetTester tester) async {
      final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
      final storage = InMemorySessionStorage(
        LoginTokensDto(
          accessToken: 'jwt-m4-ok',
          tokenType: 'Bearer',
          expiresIn: 300,
          expiresAt: now.add(const Duration(seconds: 300)),
          sesionId: '12121212-1212-1212-1212-121212121212',
          plataforma: PlataformaCliente.android,
        ),
      );

      final transport = _FakeTransport((method, path, headers, body) async {
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
        if (method == 'GET' &&
            path == '/api/gestopago/pagos/historial?page=0&size=10') {
          return _okJson({
            'contenido': [
              {
                'id': 'aaaaaaaa-0001-4000-8000-000000000001',
                'cuentaId': 45,
                'idServicio': 78,
                'servicio': 'Oui Movil',
                'idProducto': 219,
                'producto': 'Recarga 40',
                'monto': 40.00,
                'estado': 'CAPTURED',
                'referenciaInternaUpc': '100000000000001',
                'folioProveedorIdTx': 'TX-M4-001',
                'creadoEn': '2026-11-21T10:25:00',
                'capturadoEn': '2026-11-21T10:30:00',
              },
              {
                'id': 'aaaaaaaa-0002-4000-8000-000000000002',
                'cuentaId': 45,
                'idServicio': 56,
                'servicio': 'Servicio Luz',
                'idProducto': 300,
                'producto': 'Pago Luz',
                'monto': 250.00,
                'estado': 'CAPTURED',
                'referenciaInternaUpc': '100000000000002',
                'folioProveedorIdTx': null,
                'creadoEn': '2026-11-20T09:00:00',
                'capturadoEn': '2026-11-20T09:05:00',
              },
              // Guardia defensiva: aunque llegara otro estado, nunca debe mostrarse.
              {
                'id': 'bbbbbbbb-0009-4000-8000-000000000009',
                'cuentaId': 45,
                'idServicio': 78,
                'servicio': 'Oui Movil',
                'idProducto': 219,
                'producto': 'Intento Fallido',
                'monto': 40.00,
                'estado': 'FAILED',
                'referenciaInternaUpc': '999999999999999',
                'folioProveedorIdTx': null,
              },
            ],
            'page': 0,
            'size': 10,
            'totalElementos': 11,
            'totalPaginas': 2,
          });
        }
        if (method == 'GET' &&
            path == '/api/gestopago/pagos/historial?page=1&size=10') {
          return _okJson({
            'contenido': [
              {
                'id': 'aaaaaaaa-0003-4000-8000-000000000003',
                'cuentaId': 45,
                'idServicio': 80,
                'servicio': null,
                'idProducto': 310,
                'producto': null,
                'monto': 120.00,
                'estado': 'CAPTURED',
                'referenciaInternaUpc': '100000000000003',
                'folioProveedorIdTx': null,
                'creadoEn': '2026-11-19T14:00:00',
                'capturadoEn': '2026-11-19T14:01:00',
              },
            ],
            'page': 1,
            'size': 10,
            'totalElementos': 11,
            'totalPaginas': 2,
          });
        }
        if (method == 'GET' &&
            path ==
                '/api/gestopago/pagos/historial/aaaaaaaa-0003-4000-8000-000000000003') {
          return _okJson({
            'id': 'aaaaaaaa-0003-4000-8000-000000000003',
            'cuentaId': 45,
            'idServicio': 80,
            'servicio': 'Internet MX',
            'idProducto': 310,
            'producto': 'Paquete Hogar 120',
            'monto': 120.00,
            'estado': 'CAPTURED',
            'referenciaInternaUpc': '100000000000003',
            'folioProveedorIdTx': 'TX-DET-310',
            'creadoEn': '2026-11-19T14:00:00',
            'capturadoEn': '2026-11-19T14:01:00',
          });
        }
        fail('Ruta inesperada: $method $path');
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

      await _tapVisible(tester, const Key('nav_history_tab'));

      expect(find.text('Historial de pagos aplicados'), findsOneWidget);
      expect(find.text('Recarga 40'), findsOneWidget);
      expect(find.text('Pago Luz'), findsOneWidget);
      expect(find.text('Intento Fallido'), findsNothing);
      expect(find.text('TX-M4-001'), findsOneWidget);
      expect(find.text('Sin folio externo reportado'), findsOneWidget);
      await _ensureMountedAndVisible(
        tester,
        find.byKey(const Key('history_pagination_bar')),
      );
      expect(find.text('Página 1 de 2'), findsOneWidget);

      await _tapVisible(tester, const Key('history_next_page_button'));

      await _ensureMountedAndVisible(
        tester,
        find.byKey(const Key('history_pagination_bar')),
      );
      expect(find.text('Página 2 de 2'), findsOneWidget);
      await _ensureMountedAndVisible(tester, find.text('Producto #310'));
      expect(find.text('Producto #310'), findsOneWidget);

      await _tapVisible(
        tester,
        const Key(
          'history_detail_button_aaaaaaaa-0003-4000-8000-000000000003',
        ),
      );

      expect(find.text('Detalle del comprobante'), findsOneWidget);
      expect(find.text('Paquete Hogar 120'), findsOneWidget);
      expect(find.text('TX-DET-310'), findsOneWidget);
      expect(find.text('100000000000003'), findsWidgets);
    },
  );

  testWidgets(
    'M4: Historial maneja error de red con reintento y estado vacío sin fabricar datos',
    (WidgetTester tester) async {
      final now = DateTime.utc(2026, 11, 21, 16, 0, 0);
      int calls = 0;
      final storage = InMemorySessionStorage(
        LoginTokensDto(
          accessToken: 'jwt-m4-vacio',
          tokenType: 'Bearer',
          expiresIn: 300,
          expiresAt: now.add(const Duration(seconds: 300)),
          sesionId: '13131313-1313-1313-1313-131313131313',
          plataforma: PlataformaCliente.android,
        ),
      );

      final transport = _FakeTransport((method, path, headers, body) async {
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
        if (method == 'GET' &&
            path == '/api/gestopago/pagos/historial?page=0&size=10') {
          calls++;
          if (calls == 1) {
            throw const GpagoApiException(
              statusCode: 0,
              codigo: 'ERROR_COMUNICACION',
              mensaje: 'Error de red consultando historial',
              isNetworkError: true,
            );
          }
          return _okJson({
            'contenido': <Object>[],
            'page': 0,
            'size': 10,
            'totalElementos': 0,
            'totalPaginas': 0,
          });
        }
        fail('Ruta inesperada: $method $path');
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

      await _tapVisible(tester, const Key('nav_history_tab'));
      expect(find.text('Sin conexión con GPago'), findsOneWidget);

      await _tapVisible(tester, const Key('history_retry_button'));
      expect(find.byKey(const Key('history_empty_state')), findsOneWidget);
      expect(find.text('Sin compras capturadas registradas'), findsOneWidget);
    },
  );
}
